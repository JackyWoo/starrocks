// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.

#include "formats/parquet/metadata.h"

#include <gtest/gtest.h>

namespace starrocks::parquet {

class ParquetMetaDataTest : public testing::Test {
public:
    ParquetMetaDataTest() {}
    virtual ~ParquetMetaDataTest() {}

private:
    tparquet::SchemaElement _create_root_schema_element();
    tparquet::SchemaElement _create_schema_element(const std::string& name);
    std::vector<tparquet::SchemaElement> _create_schema_elements();
    tparquet::FileMetaData _create_t_file_meta();
};

tparquet::SchemaElement ParquetMetaDataTest::_create_root_schema_element() {
    tparquet::SchemaElement element;

    element.__set_num_children(2);

    return element;
}

tparquet::SchemaElement ParquetMetaDataTest::_create_schema_element(const std::string& name) {
    tparquet::SchemaElement element;

    element.__set_name(name);
    element.__set_type(tparquet::Type::type::INT32);
    element.__set_type_length(4);
    element.__set_num_children(0);

    return element;
}

std::vector<tparquet::SchemaElement> ParquetMetaDataTest::_create_schema_elements() {
    std::vector<tparquet::SchemaElement> elements;

    auto c0 = _create_root_schema_element();
    auto c1 = _create_schema_element("c1");
    auto c2 = _create_schema_element("c2");

    elements.emplace_back(c0);
    elements.emplace_back(c1);
    elements.emplace_back(c2);

    return elements;
}

tparquet::FileMetaData ParquetMetaDataTest::_create_t_file_meta() {
    auto elements = _create_schema_elements();

    tparquet::FileMetaData meta;

    meta.__set_version(0);
    meta.__set_schema(elements);
    meta.__set_num_rows(1024);

    return meta;
}

TEST_F(ParquetMetaDataTest, NumRows) {
    auto t_meta = _create_t_file_meta();

    FileMetaData meta_data;
    Status status = meta_data.init(t_meta);
    ASSERT_TRUE(status.ok());

    // check
    ASSERT_EQ(1024, meta_data.num_rows());
}

} // namespace starrocks::parquet
