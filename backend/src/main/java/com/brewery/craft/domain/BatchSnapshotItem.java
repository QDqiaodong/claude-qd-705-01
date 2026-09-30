package com.brewery.craft.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;

/**
 * 批次创建时钉死的单条配方用量【快照】。
 *
 * 与 {@link QcReading} 同一性质：快照行只随 {@link BatchRecipeSnapshot} INSERT 写入，
 * 之后配方怎么改、缩放多少轮，都不会回写历史批次。显示值（酿酒师习惯单位）与规范化值
 * （维度基准单位）同时存下，批次详情无需再按新规则重算历史用量。
 */
@Data
@Embeddable
public class BatchSnapshotItem {

    /** 投放阶段 = 当时配方 Map 的 key */
    @Column(name = "stage", length = 24)
    private String stage;

    @Column(name = "ingredient_code", length = 24)
    private String ingredientCode;

    @Column(name = "ingredient_name", length = 64)
    private String ingredientName;

    @Column(name = "ingredient_type", length = 16)
    private String ingredientType;

    /** 创建当时的显示数量/单位（原样保留） */
    @Column(name = "quantity", precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit", length = 8)
    private String unit;

    /** 规范化单位与基准数量（UNRESOLVED 行单位字段为 null、不参与合计） */
    @Column(name = "canonical_unit", length = 8)
    private String canonicalUnit;

    @Column(name = "base_quantity", precision = 12, scale = 6)
    private BigDecimal baseQuantity;

    @Column(name = "dimension", length = 8)
    private String dimension;

    /** OK / DERIVED / UNRESOLVED */
    @Column(name = "unit_status", length = 12)
    private String unitStatus;

    @Column(name = "note", length = 128)
    private String note;
}
