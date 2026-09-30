package com.brewery.craft.service.measure;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.brewery.craft.dto.MetricDtos;
import com.brewery.craft.dto.Views;

/**
 * 统一计量引擎。
 *
 * 列表、环形占比、阶段小计、配方摘要、批次详情拿到的都是这里同一次计算的结果，
 * 任何调用方都不允许再自己拿原始 quantity 直接相加。
 *
 * 规则：
 *   1. 每条明细先用 {@link UnitCatalog} 解析单位，换算到所属维度基准单位（kg/L/pkg）；
 *   2. 汇总只在同一维度内进行，质量与体积不直接相加，计件单列一维；
 *   3. UNRESOLVED（空单位无法推导/未知单位）的明细保留原值、不参与任何计算，统一告警；
 *   4. 占比按维度分别计算；环形图主维度取 质量 → 体积 → 计件 的第一个非空维度。
 */
public final class MetricEngine {

    private MetricEngine() {
    }

    /** 换算 / 合计统一保留 6 位小数，避免中间值丢精度（展示时再截） */
    public static final int CALC_SCALE = 6;
    /** 页面展示与落库的数量精度（列 precision=10,scale=3） */
    public static final int QUANTITY_SCALE = 3;

    /** 引擎的一行输入（配方明细或批次快照都先适配成它） */
    public record Input(String stage,
                        Long itemId,
                        String ingredientCode,
                        String ingredientName,
                        String ingredientType,
                        BigDecimal quantity,
                        String rawUnit,
                        String note) {
    }

    public static MetricDtos.RecipeMetrics build(List<Input> inputs) {
        MetricDtos.RecipeMetrics m = new MetricDtos.RecipeMetrics();

        // key = dimension|stage，保证同阶段同维度才相加
        Map<String, BigDecimal> stageSum = new LinkedHashMap<>();
        // key = dimension|type
        Map<String, BigDecimal> typeSum = new LinkedHashMap<>();
        Map<String, BigDecimal> dimSum = new LinkedHashMap<>();
        Map<String, Integer> dimCount = new LinkedHashMap<>();

        for (Input in : inputs) {
            MetricDtos.MetricItem row = toMetricItem(in);
            m.getItems().add(row);

            if (!"UNRESOLVED".equals(row.getUnitStatus())) {
                Dimension dim = Dimension.valueOf(row.getDimension());
                add(stageSum, dim.name(), row.getStage(), row.getBaseQuantity());
                add(typeSum, dim.name(), row.getIngredientType(), row.getBaseQuantity());
                dimSum.merge(dim.name(), row.getBaseQuantity(), BigDecimal::add);
                dimCount.merge(dim.name(), 1, Integer::sum);
            } else {
                m.setUnresolvedCount(m.getUnresolvedCount() + 1);
                m.getWarnings().add(row.getStageName() + " · " + nz(row.getIngredientName())
                        + "：" + nz(row.getUnitNote()));
            }
            if ("DERIVED".equals(row.getUnitStatus())) {
                m.getWarnings().add(row.getStageName() + " · " + nz(row.getIngredientName())
                        + "：" + nz(row.getUnitNote()));
            }
        }

        // 阶段小计（按阶段展示顺序、维度顺序输出）
        List<String> stages = sortedStages(inputs);
        for (String stage : stages) {
            for (Dimension dim : Dimension.values()) {
                BigDecimal amount = stageSum.get(key(dim.name(), stage));
                if (amount != null) {
                    MetricDtos.StageTotal st = new MetricDtos.StageTotal();
                    st.setStage(stage);
                    st.setStageName(Views.stageName(stage));
                    st.setDimension(dim.name());
                    st.setDimensionName(dim.label());
                    st.setAmount(display(amount));
                    st.setUnit(dim.baseUnit());
                    m.getStageTotals().add(st);
                }
            }
        }

        // 分维度合计，主维度 = 质量 → 体积 → 计件 第一个非空
        String primary = null;
        for (Dimension dim : Dimension.values()) {
            BigDecimal amount = dimSum.get(dim.name());
            if (amount == null) {
                continue;
            }
            if (primary == null) {
                primary = dim.name();
            }
            MetricDtos.DimensionTotal dt = new MetricDtos.DimensionTotal();
            dt.setDimension(dim.name());
            dt.setDimensionName(dim.label());
            dt.setUnit(dim.baseUnit());
            dt.setAmount(display(amount));
            dt.setItemCount(dimCount.getOrDefault(dim.name(), 0));
            m.getDimensionTotals().add(dt);
        }
        m.setPrimaryDimension(primary);
        for (MetricDtos.DimensionTotal dt : m.getDimensionTotals()) {
            dt.setPrimary(dt.getDimension().equals(primary));
        }

        // 占比：同一维度内的各原料类型分别计算（跨维度不相加）
        for (Dimension dim : Dimension.values()) {
            BigDecimal total = dimSum.get(dim.name());
            if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            for (String type : Views.INGREDIENT_TYPE_ORDER) {
                BigDecimal amount = typeSum.get(key(dim.name(), type));
                if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                MetricDtos.CompositionSlice slice = new MetricDtos.CompositionSlice();
                slice.setDimension(dim.name());
                slice.setDimensionName(dim.label());
                slice.setIngredientType(type);
                slice.setIngredientTypeName(Views.ingredientTypeName(type));
                slice.setColor(Views.ingredientColor(type));
                slice.setAmount(display(amount));
                slice.setUnit(dim.baseUnit());
                slice.setPercent(amount.multiply(BigDecimal.valueOf(100))
                        .divide(total, 1, RoundingMode.HALF_UP));
                m.getComposition().add(slice);
            }
        }
        return m;
    }

    /** 单条明细的单位解析 + 基准换算（不丢原始显示值） */
    public static MetricDtos.MetricItem toMetricItem(Input in) {
        MetricDtos.MetricItem row = new MetricDtos.MetricItem();
        row.setItemId(in.itemId());
        row.setStage(in.stage());
        row.setStageName(Views.stageName(in.stage()));
        row.setIngredientCode(in.ingredientCode());
        row.setIngredientName(in.ingredientName());
        row.setIngredientType(in.ingredientType());
        row.setIngredientTypeName(Views.ingredientTypeName(in.ingredientType()));
        row.setQuantity(in.quantity());
        // 页面保留酿酒师习惯单位：空值原样给空，不在这里偷换
        row.setUnit(in.rawUnit());
        row.setNote(in.note());

        MetricDtos.UnitResolution res = UnitCatalog.resolve(in.rawUnit(), in.ingredientType());
        row.setCanonicalUnit(res.getCanonicalUnit());
        row.setDimension(res.getDimension());
        row.setUnitStatus(res.getStatus());
        row.setUnitNote(res.getNote());
        if (res.resolved() && in.quantity() != null) {
            BigDecimal factor = UnitCatalog.factorToBase(res.getCanonicalUnit());
            row.setBaseQuantity(scale(in.quantity().multiply(factor)));
        }
        return row;
    }

    /** 规范化数量四舍五入到统一计算精度 */
    public static BigDecimal scale(BigDecimal v) {
        return v.setScale(CALC_SCALE, RoundingMode.HALF_UP);
    }

    /** 展示/落库精度（3 位小数） */
    public static BigDecimal display(BigDecimal v) {
        return v.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    private static void add(Map<String, BigDecimal> target, String dim, String key, BigDecimal value) {
        target.merge(key(dim, key), value, BigDecimal::add);
    }

    private static String key(String dim, String key) {
        return dim + "|" + key;
    }

    private static List<String> sortedStages(List<Input> inputs) {
        List<String> ordered = new ArrayList<>();
        for (Input in : inputs) {
            if (!ordered.contains(in.stage())) {
                ordered.add(in.stage());
            }
        }
        ordered.sort((a, b) -> Integer.compare(stageOrder(a), stageOrder(b)));
        return ordered;
    }

    private static int stageOrder(String stage) {
        int idx = Views.STAGE_ORDER.indexOf(stage);
        return idx < 0 ? Views.STAGE_ORDER.size() + stage.hashCode() : idx;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
