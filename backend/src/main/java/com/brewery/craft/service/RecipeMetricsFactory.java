package com.brewery.craft.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.brewery.craft.domain.BatchRecipeSnapshot;
import com.brewery.craft.domain.Recipe;
import com.brewery.craft.domain.RecipeItem;
import com.brewery.craft.dto.Views;
import com.brewery.craft.service.measure.MetricEngine;

/**
 * 把配方明细、批次用量快照统一适配成 {@link MetricEngine} 的输入行。
 *
 * 列表、环形占比、阶段小计、配方摘要、批次详情都经这里进同一个引擎，
 * 从数据结构上杜绝三处各算一套。
 */
public final class RecipeMetricsFactory {

    private RecipeMetricsFactory() {
    }

    /** 配方明细（按投放阶段固定顺序） */
    public static List<MetricEngine.Input> fromRecipe(Recipe recipe) {
        List<MetricEngine.Input> list = new ArrayList<>();
        Map<String, RecipeItem> map = recipe.getItems() == null ? Map.of() : recipe.getItems();
        List<String> keys = new ArrayList<>(map.keySet());
        keys.sort((a, b) -> Integer.compare(stageOrder(a), stageOrder(b)));
        for (String stage : keys) {
            list.add(fromItem(stage, map.get(stage)));
        }
        return list;
    }

    /** 单条配方明细 */
    public static MetricEngine.Input fromItem(String stage, RecipeItem item) {
        return new MetricEngine.Input(
                stage,
                item.getId(),
                item.getIngredientCode(),
                item.getIngredientName(),
                item.getIngredientType(),
                item.getQuantity(),
                item.getUnit(),
                item.getNote());
    }

    /** 批次创建当时钉死的用量快照（顺序与快照一致） */
    public static List<MetricEngine.Input> fromSnapshot(BatchRecipeSnapshot snapshot) {
        List<MetricEngine.Input> list = new ArrayList<>();
        for (var it : snapshot.getItems()) {
            list.add(new MetricEngine.Input(
                    it.getStage(),
                    null,
                    it.getIngredientCode(),
                    it.getIngredientName(),
                    it.getIngredientType(),
                    it.getQuantity(),
                    it.getUnit(),
                    it.getNote()));
        }
        return list;
    }

    private static int stageOrder(String stage) {
        int idx = Views.STAGE_ORDER.indexOf(stage);
        return idx < 0 ? Views.STAGE_ORDER.size() + Math.abs(stage == null ? 0 : stage.hashCode()) : idx;
    }
}
