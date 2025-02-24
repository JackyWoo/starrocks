// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.

package com.starrocks.external.hive;

import org.junit.Assert;
import org.junit.Test;

public class RemoteFileInputFormatTest {
    @Test
    public void testParquetFormat() {
        Assert.assertSame(RemoteFileInputFormat.PARQUET, RemoteFileInputFormat
                .fromHdfsInputFormatClass("org.apache.hadoop.hive.ql.io.parquet.MapredParquetInputFormat"));
        Assert.assertSame(RemoteFileInputFormat.ORC,
                RemoteFileInputFormat.fromHdfsInputFormatClass("org.apache.hadoop.hive.ql.io.orc.OrcInputFormat"));
    }
}
