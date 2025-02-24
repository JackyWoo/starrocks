// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Limited.

#pragma once

#include <memory>
#include <string>

#include "io/seekable_input_stream.h"

namespace starrocks::io {

class CacheInputStream final : public SeekableInputStream {
public:
    struct Stats {
        int64_t read_cache_ns = 0;
        int64_t write_cache_ns = 0;
        int64_t read_cache_count = 0;
        int64_t write_cache_count = 0;
        int64_t read_cache_bytes = 0;
        int64_t write_cache_bytes = 0;
    };

    static constexpr int64_t BLOCK_SIZE = 1 * 1024 * 1024;
    explicit CacheInputStream(std::string filename, std::shared_ptr<SeekableInputStream> stream);

    ~CacheInputStream() override = default;

    StatusOr<int64_t> read(void* data, int64_t count) override;

    Status seek(int64_t offset) override;

    StatusOr<int64_t> position() override;

    StatusOr<int64_t> get_size() override;

    const Stats& stats() { return _stats; }

private:
    std::string _cache_key;
    std::string _filename;
    std::shared_ptr<SeekableInputStream> _stream;
    int64_t _offset;
    std::string _buffer;
    Stats _stats;
    int64_t _size;
    int64_t _block_size;
};

} // namespace starrocks::io
