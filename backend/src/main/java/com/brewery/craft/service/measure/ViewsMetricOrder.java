package com.brewery.craft.service.measure;

import com.brewery.craft.dto.Views;

/** 缩放余差分配时的稳定排序依据：投放阶段固定顺序，未知阶段排在已知阶段之后。 */
final class ViewsMetricOrder {

    private ViewsMetricOrder() {
    }

    static int stage(String stage) {
        int idx = Views.STAGE_ORDER.indexOf(stage);
        return idx < 0 ? Views.STAGE_ORDER.size() + Math.abs(stage == null ? 0 : stage.hashCode()) : idx;
    }
}
