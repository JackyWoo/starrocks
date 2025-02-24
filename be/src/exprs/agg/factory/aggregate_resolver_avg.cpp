// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.

#include "exprs/agg/aggregate.h"
#include "exprs/agg/aggregate_factory.h"
#include "exprs/agg/array_agg.h"
#include "exprs/agg/avg.h"
#include "exprs/agg/factory/aggregate_factory.hpp"
#include "exprs/agg/factory/aggregate_resolver.hpp"
#include "runtime/primitive_type.h"

namespace starrocks::vectorized {

struct AvgDispatcher {
    template <PrimitiveType pt>
    void operator()(AggregateFuncResolver* resolver) {
        if constexpr (pt_is_aggregate<pt>) {
            auto func = AggregateFactory::MakeAvgAggregateFunction<pt>();
            using AvgState = AvgAggregateState<RunTimeCppType<ImmediateAvgResultPT<pt>>>;
            resolver->add_aggregate_mapping<pt, AvgResultPT<pt>, AvgState>("avg", true, func);
        }
    }
};

struct ArrayAggDispatcher {
    template <PrimitiveType pt>
    void operator()(AggregateFuncResolver* resolver) {
        if constexpr (pt_is_aggregate<pt> || pt_is_binary<pt>) {
            auto func = AggregateFactory::MakeArrayAggAggregateFunction<pt>();
            using AggState = ArrayAggAggregateState<pt>;
            resolver->add_aggregate_mapping<pt, TYPE_ARRAY, AggState>("array_agg", false, func);
        }
    }
};

void AggregateFuncResolver::register_avg() {
    for (auto type : aggregate_types()) {
        type_dispatch_all(type, AvgDispatcher(), this);
        type_dispatch_all(type, ArrayAggDispatcher(), this);
    }
    add_decimal_mapping<TYPE_DECIMAL32, TYPE_DECIMAL128, true>("decimal_avg");
    add_decimal_mapping<TYPE_DECIMAL64, TYPE_DECIMAL128, true>("decimal_avg");
    add_decimal_mapping<TYPE_DECIMAL128, TYPE_DECIMAL128, true>("decimal_avg");
}

} // namespace starrocks::vectorized
