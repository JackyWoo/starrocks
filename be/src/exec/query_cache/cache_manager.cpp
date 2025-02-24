// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Limited.
#include "exec/query_cache/cache_manager.h"

#include "util/defer_op.h"
namespace starrocks {
namespace query_cache {

CacheManager::CacheManager(size_t capacity) : _cache(capacity) {}
static void delete_cache_entry(const CacheKey& key, void* value) {
    auto* cache_value = (CacheValue*)value;
    delete cache_value;
}

Status CacheManager::populate(const std::string& key, const CacheValue& value) {
    CacheValue* cache_value = new CacheValue(value);
    auto* handle = _cache.insert(key, cache_value, cache_value->size(), &delete_cache_entry, CachePriority::NORMAL);
    DeferOp defer([this, handle]() { _cache.release(handle); });
    if (_cache.get_memory_usage() > _cache.get_capacity()) {
        _cache.prune();
    }
    return handle != nullptr ? Status::OK() : Status::InternalError("Insert failure");
}

static const Status CACHE_MISS = Status::NotFound("CacheMiss");

StatusOr<CacheValue> CacheManager::probe(const std::string& key) {
    auto* handle = _cache.lookup(key);
    if (handle == nullptr) {
        return CACHE_MISS;
    }
    DeferOp defer([this, handle]() { _cache.release(handle); });
    CacheValue cache_value(*reinterpret_cast<CacheValue*>(_cache.value(handle)));
    return cache_value;
}

size_t CacheManager::memory_usage() {
    return _cache.get_memory_usage();
}

size_t CacheManager::capacity() {
    return _cache.get_capacity();
}

size_t CacheManager::lookup_count() {
    return _cache.get_lookup_count();
}

size_t CacheManager::hit_count() {
    return _cache.get_hit_count();
}

void CacheManager::invalidate_all() {
    auto old_capacity = _cache.get_capacity();
    // set capacity of cache to zero, the cache shall prune all cache entries.
    _cache.set_capacity(0);
    _cache.set_capacity(old_capacity);
}

} // namespace query_cache
} // namespace starrocks
