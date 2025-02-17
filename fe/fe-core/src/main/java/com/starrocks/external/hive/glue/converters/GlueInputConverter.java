// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.

package com.starrocks.external.hive.glue.converters;

import com.amazonaws.services.glue.model.DatabaseInput;
import com.amazonaws.services.glue.model.PartitionInput;
import com.amazonaws.services.glue.model.TableInput;
import com.amazonaws.services.glue.model.UserDefinedFunctionInput;
import org.apache.hadoop.hive.metastore.api.Database;
import org.apache.hadoop.hive.metastore.api.Function;
import org.apache.hadoop.hive.metastore.api.Partition;
import org.apache.hadoop.hive.metastore.api.Table;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This class provides methods to convert Hive/Catalog objects to Input objects used
 * for Glue API parameters
 */
public final class GlueInputConverter {

    public static DatabaseInput convertToDatabaseInput(Database hiveDatabase) {
        return convertToDatabaseInput(HiveToCatalogConverter.convertDatabase(hiveDatabase));
    }

    public static DatabaseInput convertToDatabaseInput(com.amazonaws.services.glue.model.Database database) {
        DatabaseInput input = new DatabaseInput();

        input.setName(database.getName());
        input.setDescription(database.getDescription());
        input.setLocationUri(database.getLocationUri());
        input.setParameters(database.getParameters());

        return input;
    }

    public static TableInput convertToTableInput(Table hiveTable) {
        return convertToTableInput(HiveToCatalogConverter.convertTable(hiveTable));
    }

    public static TableInput convertToTableInput(com.amazonaws.services.glue.model.Table table) {
        TableInput tableInput = new TableInput();

        tableInput.setRetention(table.getRetention());
        tableInput.setPartitionKeys(table.getPartitionKeys());
        tableInput.setTableType(table.getTableType());
        tableInput.setName(table.getName());
        tableInput.setOwner(table.getOwner());
        tableInput.setLastAccessTime(table.getLastAccessTime());
        tableInput.setStorageDescriptor(table.getStorageDescriptor());
        tableInput.setParameters(table.getParameters());
        tableInput.setViewExpandedText(table.getViewExpandedText());
        tableInput.setViewOriginalText(table.getViewOriginalText());

        return tableInput;
    }

    public static PartitionInput convertToPartitionInput(Partition src) {
        return convertToPartitionInput(HiveToCatalogConverter.convertPartition(src));
    }

    public static PartitionInput convertToPartitionInput(com.amazonaws.services.glue.model.Partition src) {
        PartitionInput partitionInput = new PartitionInput();

        partitionInput.setLastAccessTime(src.getLastAccessTime());
        partitionInput.setParameters(src.getParameters());
        partitionInput.setStorageDescriptor(src.getStorageDescriptor());
        partitionInput.setValues(src.getValues());

        return partitionInput;
    }

    public static List<PartitionInput> convertToPartitionInputs(
            Collection<com.amazonaws.services.glue.model.Partition> parts) {
        List<PartitionInput> inputList = new ArrayList<>();

        for (com.amazonaws.services.glue.model.Partition part : parts) {
            inputList.add(convertToPartitionInput(part));
        }
        return inputList;
    }

    public static UserDefinedFunctionInput convertToUserDefinedFunctionInput(Function hiveFunction) {
        UserDefinedFunctionInput functionInput = new UserDefinedFunctionInput();

        functionInput.setClassName(hiveFunction.getClassName());
        functionInput.setFunctionName(hiveFunction.getFunctionName());
        functionInput.setOwnerName(hiveFunction.getOwnerName());
        if (hiveFunction.getOwnerType() != null) {
            functionInput.setOwnerType(hiveFunction.getOwnerType().name());
        }
        functionInput.setResourceUris(HiveToCatalogConverter.covertResourceUriList(hiveFunction.getResourceUris()));
        return functionInput;
    }

}
