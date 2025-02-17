// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.

package com.starrocks.external.hudi;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.starrocks.connector.exception.StarRocksConnectorException;
import com.starrocks.external.ObjectStorageUtils;
import com.starrocks.external.RemoteFileDesc;
import com.starrocks.external.RemoteFileIO;
import com.starrocks.external.RemotePathKey;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hudi.common.fs.FSUtils;
import org.apache.hudi.common.model.BaseFile;
import org.apache.hudi.common.model.FileSlice;
import org.apache.hudi.common.model.HoodieBaseFile;
import org.apache.hudi.common.model.HoodieLogFile;
import org.apache.hudi.common.table.HoodieTableMetaClient;
import org.apache.hudi.common.table.timeline.HoodieInstant;
import org.apache.hudi.common.table.timeline.HoodieTimeline;
import org.apache.hudi.common.table.view.HoodieTableFileSystemView;
import org.apache.hudi.common.util.Option;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class HudiRemoteFileIO implements RemoteFileIO {
    private static final Logger LOG = LogManager.getLogger(HudiRemoteFileIO.class);
    private final Configuration configuration;

    // table location -> HoodieTableMetaClient
    private final Map<String, HoodieTableMetaClient> hudiClients = new ConcurrentHashMap<>();

    public HudiRemoteFileIO(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Map<RemotePathKey, List<RemoteFileDesc>> getRemoteFiles(RemotePathKey pathKey) {
        String tableLocation = pathKey.getHudiTableLocation().orElseThrow(() ->
                new StarRocksConnectorException("Missing hudi table base location on %s", pathKey));

        String partitionPath = ObjectStorageUtils.formatObjectStoragePath(pathKey.getPath());
        String partitionName = FSUtils.getRelativePartitionPath(new Path(tableLocation), new Path(partitionPath));

        ImmutableMap.Builder<RemotePathKey, List<RemoteFileDesc>> resultPartitions = ImmutableMap.builder();
        List<RemoteFileDesc> fileDescs = Lists.newArrayList();

        HoodieTableMetaClient metaClient = hudiClients.computeIfAbsent(tableLocation, ignored ->
                HoodieTableMetaClient.builder().setConf(configuration).setBasePath(tableLocation).build()
        );
        metaClient.reloadActiveTimeline();
        HoodieTimeline timeline = metaClient.getCommitsAndCompactionTimeline().filterCompletedInstants();
        Option<HoodieInstant> latestInstant = timeline.lastInstant();
        if (!latestInstant.isPresent()) {
            return resultPartitions.put(pathKey, fileDescs).build();
        }
        try {
            String globPath = String.format("%s/%s/*", metaClient.getBasePath(), partitionName);
            List<FileStatus> statuses = FSUtils.getGlobStatusExcludingMetaFolder(metaClient.getRawFs(), new Path(globPath));
            HoodieTableFileSystemView fileSystemView = new HoodieTableFileSystemView(metaClient,
                    timeline, statuses.toArray(new FileStatus[0]));
            String queryInstant = latestInstant.get().getTimestamp();
            Iterator<FileSlice> hoodieFileSliceIterator = fileSystemView
                    .getLatestMergedFileSlicesBeforeOrOn(partitionName, queryInstant).iterator();
            while (hoodieFileSliceIterator.hasNext()) {
                FileSlice fileSlice = hoodieFileSliceIterator.next();
                Optional<HoodieBaseFile> baseFile = fileSlice.getBaseFile().toJavaOptional();
                String fileName = baseFile.map(BaseFile::getFileName).orElse("");
                long fileLength = baseFile.map(BaseFile::getFileLen).orElse(-1L);
                List<String> logs = fileSlice.getLogFiles().map(HoodieLogFile::getFileName).collect(Collectors.toList());
                fileDescs.add(new RemoteFileDesc(fileName, "", fileLength,
                        ImmutableList.of(), ImmutableList.copyOf(logs)));
            }
        } catch (Exception e) {
            LOG.error("Failed to get hudi remote file's metadata on path: {}", partitionPath, e);
            throw new StarRocksConnectorException("Failed to get hudi remote file's metadata on path: %s", pathKey);
        }
        return resultPartitions.put(pathKey, fileDescs).build();
    }

}
