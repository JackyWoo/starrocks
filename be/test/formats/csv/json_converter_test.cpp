// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.

#include <gtest/gtest.h>

#include "column/column_helper.h"
#include "column/json_column.h"
#include "formats/csv/converter.h"
#include "formats/csv/output_stream_string.h"
#include "runtime/types.h"
#include "util/json.h"

namespace starrocks::vectorized::csv {

class JsonConverterTest : public ::testing::Test {
public:
    JsonConverterTest() { _type.type = TYPE_JSON; }

protected:
    TypeDescriptor _type;
};

// NOLINTNEXTLINE
TEST_F(JsonConverterTest, test_read_string) {
    auto conv = csv::get_converter(_type, false);
    auto json_column = JsonColumn::create();

    string s1 = "{\"key\": 1}";
    string s2 = "{\"a\":1,\"b\":2}";
    EXPECT_TRUE(conv->read_string(json_column.get(), s1, Converter::Options()));
    EXPECT_TRUE(conv->read_string(json_column.get(), s2, Converter::Options()));

    EXPECT_EQ(2, json_column->size());

    auto input = JsonValue::parse(s1);
    JsonValue* json = json_column->get_object(0);
    EXPECT_EQ(0, json->compare(*input));

    input = JsonValue::parse(s2);
    json = json_column->get_object(1);
    EXPECT_EQ(0, json->compare(*input));
}

TEST_F(JsonConverterTest, test_read_quoted_string) {
    auto conv = csv::get_converter(_type, false);
    auto json_column = JsonColumn::create();

    EXPECT_TRUE(conv->read_quoted_string(json_column.get(), "\"{\"key\": 1}\"", Converter::Options()));
    EXPECT_TRUE(conv->read_quoted_string(json_column.get(), "\"{\"a\": 1,\"b\": 2}\"", Converter::Options()));

    EXPECT_EQ(2, json_column->size());
    auto input = JsonValue::parse("{\"key\": 1}");
    EXPECT_EQ(0, json_column->get_object(0)->compare(*input));

    input = JsonValue::parse("{\"a\": 1,\"b\": 2}");
    EXPECT_EQ(0, json_column->get_object(1)->compare(*input));
}

TEST_F(JsonConverterTest, test_write_string) {
    auto conv = csv::get_converter(_type, false);
    auto json_column = JsonColumn::create();
    auto json = JsonValue::parse(R"({"a": 1, "b": 2})");

    json_column->append(&json.value());

    json = JsonValue::parse(R"({"name": "name", "num": 3, "sites": [ "Google", "Runoob", "Taobao" ]})");
    json_column->append(&json.value());

    csv::OutputStreamString buff;
    ASSERT_TRUE(conv->write_string(&buff, *json_column, 0, Converter::Options()).ok());
    ASSERT_TRUE(conv->write_string(&buff, *json_column, 1, Converter::Options()).ok());
    ASSERT_TRUE(buff.finalize().ok());

    csv::OutputStreamString buff2;
    ASSERT_TRUE(conv->write_quoted_string(&buff2, *json_column, 0, Converter::Options()).ok());
    ASSERT_TRUE(conv->write_quoted_string(&buff2, *json_column, 1, Converter::Options()).ok());
    ASSERT_TRUE(buff2.finalize().ok());
}

} // namespace starrocks::vectorized::csv
