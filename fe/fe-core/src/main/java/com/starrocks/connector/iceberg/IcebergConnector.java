// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.

package com.starrocks.connector.iceberg;

import com.starrocks.connector.Connector;
import com.starrocks.connector.ConnectorContext;
import com.starrocks.connector.ConnectorMetadata;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

import static com.starrocks.catalog.IcebergTable.ICEBERG_CATALOG_LEGACY;
import static com.starrocks.catalog.IcebergTable.ICEBERG_CATALOG_TYPE;

public class IcebergConnector implements Connector {
    private static final Logger LOG = LogManager.getLogger(IcebergConnector.class);

    private final Map<String, String> properties;
    private final String catalogName;
    private ConnectorMetadata metadata;

    public IcebergConnector(ConnectorContext context) {
        this.catalogName = context.getCatalogName();
        this.properties = context.getProperties();
    }

    @Override
    public ConnectorMetadata getMetadata() {
        if (metadata == null) {
            if (null == properties.get(ICEBERG_CATALOG_TYPE) || properties.get(ICEBERG_CATALOG_TYPE).length() == 0) {
                properties.put(ICEBERG_CATALOG_TYPE, properties.get(ICEBERG_CATALOG_LEGACY));
            }
            try {
                metadata = new IcebergMetadata(catalogName, properties);
            } catch (Exception e) {
                LOG.error("Failed to create iceberg metadata on [catalog : {}]", catalogName, e);
                throw e;
            }
        }
        return metadata;
    }
}
