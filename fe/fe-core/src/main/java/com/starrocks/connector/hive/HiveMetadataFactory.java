// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.

package com.starrocks.connector.hive;

import com.starrocks.external.CachingRemoteFileConf;
import com.starrocks.external.CachingRemoteFileIO;
import com.starrocks.external.RemoteFileIO;
import com.starrocks.external.RemoteFileOperations;
import com.starrocks.external.hive.CachingHiveMetastore;
import com.starrocks.external.hive.CachingHiveMetastoreConf;
import com.starrocks.external.hive.HiveMetastoreOperations;
import com.starrocks.external.hive.HiveStatisticsProvider;
import com.starrocks.external.hive.IHiveMetastore;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

import static com.starrocks.external.hive.CachingHiveMetastore.createQueryLevelInstance;

public class HiveMetadataFactory {
    private final String catalogName;
    private final IHiveMetastore metastore;
    private final RemoteFileIO remoteFileIO;
    private final long perQueryMetastoreMaxNum;
    private final long perQueryCacheRemotePathMaxNum;
    private final ExecutorService pullRemoteFileExecutor;
    private final boolean isRecursive;

    public HiveMetadataFactory(String catalogName,
                               IHiveMetastore metastore,
                               RemoteFileIO remoteFileIO,
                               CachingHiveMetastoreConf hmsConf,
                               CachingRemoteFileConf fileConf,
                               ExecutorService pullRemoteFileExecutor,
                               boolean isRecursive) {
        this.catalogName = catalogName;
        this.metastore = metastore;
        this.remoteFileIO = remoteFileIO;
        this.perQueryMetastoreMaxNum = hmsConf.getPerQueryCacheMaxNum();
        this.perQueryCacheRemotePathMaxNum = fileConf.getPerQueryCacheMaxSize();
        this.pullRemoteFileExecutor = pullRemoteFileExecutor;
        this.isRecursive = isRecursive;
    }

    public HiveMetadata create() {
        HiveMetastoreOperations hiveMetastoreOperations = new HiveMetastoreOperations(
                createQueryLevelInstance(metastore, perQueryMetastoreMaxNum), metastore instanceof CachingHiveMetastore);
        RemoteFileOperations remoteFileOperations = new RemoteFileOperations(
                CachingRemoteFileIO.createQueryLevelInstance(remoteFileIO, perQueryCacheRemotePathMaxNum),
                pullRemoteFileExecutor,
                isRecursive,
                remoteFileIO instanceof CachingRemoteFileIO);
        HiveStatisticsProvider statisticsProvider = new HiveStatisticsProvider(hiveMetastoreOperations, remoteFileOperations);

        Optional<CacheUpdateProcessor> cacheUpdateProcessor = getCacheUpdateProcessor();
        return new HiveMetadata(catalogName, hiveMetastoreOperations,
                remoteFileOperations, statisticsProvider, cacheUpdateProcessor);
    }

    public synchronized Optional<CacheUpdateProcessor> getCacheUpdateProcessor() {
        Optional<CacheUpdateProcessor> cacheUpdateProcessor;
        if (remoteFileIO instanceof CachingRemoteFileIO || metastore instanceof CachingHiveMetastore) {
            cacheUpdateProcessor = Optional.of(new CacheUpdateProcessor(
                    catalogName, metastore, remoteFileIO, pullRemoteFileExecutor, isRecursive));
        } else {
            cacheUpdateProcessor = Optional.empty();
        }

        return cacheUpdateProcessor;
    }
}
