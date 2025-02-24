// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.

package com.starrocks.external.hive.glue.util;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.apache.hadoop.hive.metastore.api.MetaException;
import org.apache.hadoop.hive.ql.exec.SerializationUtilities;
import org.apache.hadoop.hive.ql.plan.ExprNodeDesc;
import org.apache.hadoop.hive.ql.plan.ExprNodeGenericFuncDesc;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFIn;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFOPNot;
import org.apache.hadoop.hive.serde2.typeinfo.PrimitiveTypeInfo;
import org.apache.log4j.Logger;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Utility methods for constructing the string representation of query expressions used by Catalog service
 */
public final class ExpressionHelper {

    private static final String HIVE_STRING_TYPE_NAME = "string";
    private static final String HIVE_IN_OPERATOR = "IN";
    private static final String HIVE_NOT_IN_OPERATOR = "NOT IN";
    private static final String HIVE_NOT_OPERATOR = "not";

    // TODO "hook" into Hive logging (hive or hive.metastore)
    private static final Logger LOGGER = Logger.getLogger(ExpressionHelper.class);

    private static final List<String> QUOTED_TYPES =
            ImmutableList.of("string", "char", "varchar", "date", "datetime", "timestamp");
    private static final Joiner JOINER = Joiner.on(" AND ");

    /*
     * The method below is used to rewrite the hive expression tree to quote the timestamp values.
     * An example of this would be hive providing us as query as follows:
     * ((strCol = 'test') and (timestamp = 1969-12-31 16:02:03.456))
     *
     * this will be rewritten by the method to:
     * ((strCol = 'test') and (timestamp = '1969-12-31 16:02:03.456'))
     *
     * Notice the way the timestamp is quoted.
     *
     * In order to perform this operation we recursively navigate the ExpressionTree
     * given to us by hive and switch the type to 'string' whenever we encounter a node type
     * of type 'timestamp'
     *
     * When we call the getExprTree method of the modified expression tree, the timestamp values are
     * properly quoted.
     *
     * This method also rewrites the expression string for "NOT IN" expression.
     * Hive converts the expression "<colName> NOT IN (<List of values>)"  to "(not (<colName>) IN (<List of values>))".
     * But in DataCatalog service, the parsing is done based on the original expression (which contains NOT IN).
     * So, we need to rewrite the expression if NOT IN was used.
     * */
    public static String convertHiveExpressionToCatalogExpression(byte[] exprBytes) throws MetaException {
        ExprNodeGenericFuncDesc exprTree = deserializeExpr(exprBytes);
        Set<String> columnNamesInNotInExpression = Sets.newHashSet();
        fieldEscaper(exprTree.getChildren(), exprTree, columnNamesInNotInExpression);
        String expression = rewriteExpressionForNotIn(exprTree.getExprString(), columnNamesInNotInExpression);
        return expression;
    }

    private static ExprNodeGenericFuncDesc deserializeExpr(byte[] exprBytes) throws MetaException {
        ExprNodeGenericFuncDesc expr = null;
        try {
            expr = SerializationUtilities.deserializeExpressionFromKryo(exprBytes);
        } catch (Exception ex) {
            LOGGER.error("Failed to deserialize the expression", ex);
            throw new MetaException(ex.getMessage());
        }
        if (expr == null) {
            throw new MetaException("Failed to deserialize expression - ExprNodeDesc not present");
        }
        return expr;
    }

    //Helper method that recursively switches the type of the node, this is used
    //by the convertHiveExpressionToCatalogExpression
    private static void fieldEscaper(List<ExprNodeDesc> exprNodes, ExprNodeDesc parent,
                                     Set<String> columnNamesInNotInExpression) {
        if (exprNodes == null || exprNodes.isEmpty()) {
            return;
        } else {
            for (ExprNodeDesc nodeDesc : exprNodes) {
                String nodeType = nodeDesc.getTypeString().toLowerCase();
                if (QUOTED_TYPES.contains(nodeType)) {
                    PrimitiveTypeInfo tInfo = new PrimitiveTypeInfo();
                    tInfo.setTypeName(HIVE_STRING_TYPE_NAME);
                    nodeDesc.setTypeInfo(tInfo);
                }
                addColumnNamesOfNotInExpressionToSet(nodeDesc, parent, columnNamesInNotInExpression);
                fieldEscaper(nodeDesc.getChildren(), nodeDesc, columnNamesInNotInExpression);
            }
        }
    }

    /*
     * Method to extract the names of columns that are involved in NOT IN expression. Only one column is allowed to be
     * used in NOT IN expression. So, ExprNodeDesc.getCols() would return only 1 column.
     *
     * @param childNode
     * @param parentNode
     * @param columnsInNotInExpression
     */
    private static void addColumnNamesOfNotInExpressionToSet(ExprNodeDesc childNode, ExprNodeDesc parentNode,
                                                             Set<String> columnsInNotInExpression) {
        if (parentNode instanceof ExprNodeGenericFuncDesc &&
                childNode instanceof ExprNodeGenericFuncDesc) {
            ExprNodeGenericFuncDesc parentFuncNode = (ExprNodeGenericFuncDesc) parentNode;
            ExprNodeGenericFuncDesc childFuncNode = (ExprNodeGenericFuncDesc) childNode;
            if (parentFuncNode.getGenericUDF() instanceof GenericUDFOPNot &&
                    childFuncNode.getGenericUDF() instanceof GenericUDFIn) {
                // The current parent child pair represents a "NOT IN" expression. Add name of the column to the set.
                columnsInNotInExpression.addAll(childFuncNode.getCols());
            }
        }
    }

    private static String rewriteExpressionForNotIn(String expression, Set<String> columnsInNotInExpression) {
        for (String columnName : columnsInNotInExpression) {
            if (columnName != null) {
                String hiveExpression = getHiveCompatibleNotInExpression(columnName);
                hiveExpression = escapeParentheses(hiveExpression);
                String catalogExpression = getCatalogCompatibleNotInExpression(columnName);
                catalogExpression = escapeParentheses(catalogExpression);
                expression = expression.replaceAll(hiveExpression, catalogExpression);
            }
        }
        return expression;
    }

    // return "not (<columnName>) IN ("
    private static String getHiveCompatibleNotInExpression(String columnName) {
        return String.format("%s (%s) %s (", HIVE_NOT_OPERATOR, columnName, HIVE_IN_OPERATOR);
    }

    // return "(<columnName>) NOT IN ("
    private static String getCatalogCompatibleNotInExpression(String columnName) {
        return String.format("(%s) %s (", columnName, HIVE_NOT_IN_OPERATOR);
    }

    /*
     * Escape the parentheses so that they are considered literally and not as part of regular expression. In the updated
     * expression , we need "\\(" as the output. So, the first four '\' generate '\\' and the last two '\' generate a '('
     */
    private static String escapeParentheses(String expression) {
        expression = expression.replaceAll("\\(", "\\\\\\(");
        expression = expression.replaceAll("\\)", "\\\\\\)");
        return expression;
    }

    public static String buildExpressionFromPartialSpecification(org.apache.hadoop.hive.metastore.api.Table table,
                                                                 List<String> partitionValues) throws MetaException {

        List<org.apache.hadoop.hive.metastore.api.FieldSchema> partitionKeys = table.getPartitionKeys();

        if (partitionValues == null || partitionValues.isEmpty()) {
            return null;
        }

        if (partitionKeys == null || partitionValues.size() > partitionKeys.size()) {
            throw new MetaException("Incorrect number of partition values: " + partitionValues);
        }

        partitionKeys = partitionKeys.subList(0, partitionValues.size());
        List<String> predicates = new LinkedList<>();
        for (int i = 0; i < partitionValues.size(); i++) {
            if (!Strings.isNullOrEmpty(partitionValues.get(i))) {
                predicates.add(buildPredicate(partitionKeys.get(i), partitionValues.get(i)));
            }
        }

        return JOINER.join(predicates);
    }

    private static String buildPredicate(org.apache.hadoop.hive.metastore.api.FieldSchema schema, String value) {
        if (isQuotedType(schema.getType())) {
            return String.format("(%s='%s')", schema.getName(), escapeSingleQuotes(value));
        } else {
            return String.format("(%s=%s)", schema.getName(), value);
        }
    }

    private static String escapeSingleQuotes(String s) {
        return s.replaceAll("'", "\\\\'");
    }

    private static boolean isQuotedType(String type) {
        return QUOTED_TYPES.contains(type);
    }

    public static String replaceDoubleQuoteWithSingleQuotes(String s) {
        return s.replaceAll("\"", "\'");
    }

}
