package com.brewery.craft.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 模块三：配方原料用量明细。
 *
 * 它同时是 {@link Recipe#getItems()} 这张 Map 的 value —— 投放阶段不写在本实体里，
 * 而是由 {@code @MapKeyColumn(name = "stage")} 单独维护（Map 的 key 即这一行所处的投放阶段）。
 *
 * 计量口径：{@code quantity/unit} 永远是酿酒师录入的【显示值】（可能是 kg、g、L、mL、pkg），
 * 汇总前由统一计量引擎先换算到维度基准单位。{@code canonicalUnit/baseQuantity} 是迁移时
 * 落库的规范化结果（可空，实时计算仍以引擎为准）；{@code unitStatus} 标记历史脏数据
 * （空单位/旧写法已推导 OK/DERIVED，无法可靠推导的 UNRESOLVED 保留原值待人工处理）。
 */
@Data
@Entity
@Table(name = "recipe_item")
public class RecipeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属配方：这张表的关系由 Recipe.items 单向维护（Map 的 key 落在 stage 列） */
    @Column(name = "ingredient_code", nullable = false, length = 24)
    private String ingredientCode;

    /** 原料名称（冗余，便于列表展示） */
    @Column(name = "ingredient_name", length = 64)
    private String ingredientName;

    /** 原料大类：MALT/HOP/YEAST/ADJUNCT/WATER */
    @Column(name = "ingredient_type", length = 16)
    private String ingredientType;

    /** 用量（酿酒师习惯单位下的显示值） */
    @Column(name = "quantity", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    /** 显示单位：kg / g / L / mL / pkg（历史数据可能为空或旧写法） */
    @Column(name = "unit", length = 8)
    private String unit;

    /** 规范化单位：kg / L / pkg（迁移推导结果；UNRESOLVED 时为 null） */
    @Column(name = "canonical_unit", length = 8)
    private String canonicalUnit;

    /** 换算到维度基准单位后的数量（迁移推导结果；实时计算以计量引擎为准） */
    @Column(name = "base_quantity", precision = 12, scale = 6)
    private BigDecimal baseQuantity;

    /** 单位状态：OK 原样可用 / DERIVED 旧写法或按类别推导 / UNRESOLVED 待人工处理 */
    @Column(name = "unit_status", length = 12)
    private String unitStatus;

    /** 推导或待处理说明 */
    @Column(name = "unit_note", length = 256)
    private String unitNote;

    /** 投放备注（如"60 分钟苦花"） */
    @Column(name = "note", length = 128)
    private String note;
}
