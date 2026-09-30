package com.brewery.craft.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 统一计量输出：列表明细、环形占比、阶段小计、整张配方摘要、批次快照详情，
 * 全部基于同一套 {@code RecipeMetrics} 结果渲染，不允许各处再各算一套。
 */
public final class MetricDtos {

    private MetricDtos() {
    }

    /** 单位解析结论（历史兼容：空单位/旧写法/大小写不一致的推导结果） */
    @Data
    public static class UnitResolution {

        /** 明细原始单位（原样保留，页面仍按酿酒师习惯单位显示） */
        private String rawUnit;
        /** 规范化后的单位；无法可靠推导时为 null */
        private String canonicalUnit;
        /** 所属维度 MASS/VOLUME/COUNT；无法推导时为 null */
        private String dimension;
        /** OK 原样可用 / DERIVED 由旧写法或类别推导 / UNRESOLVED 待人工处理 */
        private String status;
        /** 推导或异常说明（页面 tooltip / 待处理标记） */
        private String note;

        public boolean resolved() {
            return !"UNRESOLVED".equals(status);
        }
    }

    /** 单条明细的规范化计量行 */
    @Data
    public static class MetricItem {

        /** Map 的 key = 投放阶段 */
        private String stage;
        private String stageName;
        private Long itemId;
        private String ingredientCode;
        private String ingredientName;
        private String ingredientType;
        private String ingredientTypeName;

        /** 原始数量（酿酒师录入值，不改动） */
        private BigDecimal quantity;
        /** 原始显示单位（酿酒师习惯单位，不改动） */
        private String unit;
        private String note;

        /** 规范化单位（kg/L/pkg） */
        private String canonicalUnit;
        /** 计量维度 MASS/VOLUME/COUNT */
        private String dimension;
        /** 换算到维度基准单位后的数量，如 500 g → 0.5 kg */
        private BigDecimal baseQuantity;
        /** OK / DERIVED / UNRESOLVED */
        private String unitStatus;
        /** 单位推导或待处理说明 */
        private String unitNote;
    }

    /** 阶段小计：同一阶段、同一维度先换算再相加 */
    @Data
    public static class StageTotal {

        private String stage;
        private String stageName;
        private String dimension;
        private String dimensionName;
        /** 基准单位合计 */
        private BigDecimal amount;
        /** 基准单位（kg/L/pkg） */
        private String unit;
    }

    /** 环形占比：某原料类型在某一维度合计中的占比 */
    @Data
    public static class CompositionSlice {

        private String dimension;
        private String dimensionName;
        private String ingredientType;
        private String ingredientTypeName;
        private String color;
        /** 基准单位下的该类型合计 */
        private BigDecimal amount;
        /** 合计单位（kg/L/pkg） */
        private String unit;
        /** 在所属维度合计中的占比（同维度各类型百分比之和 = 100） */
        private BigDecimal percent;
    }

    /** 某维度的规范化合计 */
    @Data
    public static class DimensionTotal {

        private String dimension;
        private String dimensionName;
        /** 基准单位 */
        private String unit;
        /** 该维度全部可解析明细的基准单位合计 */
        private BigDecimal amount;
        /** 是否为环形图主维度（有质量时是质量，否则体积，再次计件） */
        private boolean primary;
        /** 参与合计的明细条数 */
        private int itemCount;
    }

    /** 整张配方 / 批次快照的统一计量摘要 */
    @Data
    public static class RecipeMetrics {

        /** 逐行规范化明细（显示单位沿用原值，另带换算结果） */
        private List<MetricItem> items = new ArrayList<>();
        /** 分维度、分阶段小计 */
        private List<StageTotal> stageTotals = new ArrayList<>();
        /** 分维度、分原料类型占比（环形图、阶段、详情共用） */
        private List<CompositionSlice> composition = new ArrayList<>();
        /** 分维度规范化合计 */
        private List<DimensionTotal> dimensionTotals = new ArrayList<>();
        /** 环形图主维度 */
        private String primaryDimension;
        /** 无法参与计算的明细条数（保留原值、待人工处理） */
        private int unresolvedCount;
        /** 解析告警 / 待处理说明 */
        private List<String> warnings = new ArrayList<>();
    }

    /** 缩放预览中的单行 */
    @Data
    public static class ScaleItem {

        private String stage;
        private String stageName;
        private String ingredientCode;
        private String ingredientName;
        private String ingredientType;
        private String ingredientTypeName;
        private String dimension;

        /** 缩放前原始数量/单位（酿酒师习惯单位） */
        private BigDecimal originalQuantity;
        private String originalUnit;
        /** 换算到基准单位后的原值，如 500 g → 0.5 kg */
        private BigDecimal baseQuantity;
        /** 等比例计算出的精确目标值（基准单位，未舍入） */
        private BigDecimal exactQuantity;
        /** 最小可投放量（基准单位，缩放后低于它会被抬到该值） */
        private BigDecimal minimumBase;
        /** 舍入步长（基准单位；质量/体积 0.001 = 1 g/1 mL，计件 1 pkg） */
        private BigDecimal stepBase;
        /** 舍入后建议值（显示单位） */
        private BigDecimal roundedQuantity;
        private String roundedUnit;
        /** 再换算回基准单位的结果 */
        private BigDecimal roundedBase;
        /** 余差分配后的最终基准单位数量 */
        private BigDecimal finalBase;
        /** 余差（精确值 − 本轮初舍入值，基准单位，>0 表示这条在余差分配中需要补） */
        private BigDecimal residualBase;
        /** 最终余差去向：+n / -n 个步长（基准单位），0 表示只按比例舍入 */
        private String adjustment;
        /** 最终应用数量（显示单位）= 舍入值叠加余差调整 */
        private BigDecimal finalQuantity;
        private String finalUnit;
        private String unitStatus;
        private String unitNote;
    }

    /** 缩放预览 / 应用结果 */
    @Data
    public static class ScaleView {

        private Long recipeId;
        private String recipeName;
        /** 配方基准批量（L）：缩放比例的分母 */
        private BigDecimal baseBatchSizeL;
        /** 目标批量（L）：缩放比例的分子 */
        private BigDecimal targetBatchSizeL;
        /** 缩放比例 = 目标 / 基准 */
        private BigDecimal factor;
        /** 配方版本指纹：应用时必须与当前配方一致，否则预览过期 */
        private String fingerprint;

        private List<ScaleItem> items = new ArrayList<>();
        /** 缩放后分维度规范化合计 */
        private List<DimensionTotal> dimensionTotals = new ArrayList<>();
        /** 目标规范化合计（= 原合计 × 比例），用于与缩放结果逐条核对 */
        private List<DimensionTotal> targetTotals = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private int unresolvedCount;
        /** 余差分配规则说明（页面固定展示，保证可解释） */
        private String residualPolicy;
    }
}
