package com.brewery.craft.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

/** 模块三：配方。 */
@Data
@Entity
@Table(name = "recipe")
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 配方编码 */
    @Column(name = "code", nullable = false, length = 24)
    private String code;

    /** 配方名 */
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** 风格：IPA/STOUT/PILSNER/WHEAT/SOUR/PALE_ALE */
    @Column(name = "style", nullable = false, length = 16)
    private String style;

    /** 目标原浓 OG */
    @Column(name = "target_og", precision = 5, scale = 3)
    private BigDecimal targetOg;

    /** 目标酒精度 %vol */
    @Column(name = "target_abv", precision = 4, scale = 2)
    private BigDecimal targetAbv;

    /** 目标苦度 IBU */
    @Column(name = "target_ibu", precision = 5, scale = 1)
    private BigDecimal targetIbu;

    /** 目标色度 EBC */
    @Column(name = "target_ebc", precision = 5, scale = 1)
    private BigDecimal targetEbc;

    @Column(name = "process_note", length = 512)
    private String processNote;

    /**
     * 配方基准批量（L）：配方用量对应的标准批次体积，缩放比例 = 目标批量 / 基准批量。
     * 为空时以 1000L 作为兼容默认（{@code RecipeService} 统一兜底，不把默认值静默写死给前端）。
     */
    @Column(name = "base_batch_size_l", precision = 10, scale = 2)
    private BigDecimal baseBatchSizeL;

    /**
     * 明细版本号：任何用量明细的增删改（含缩放应用）都 +1。
     * 缩放预览携带它对应的版本指纹，确认应用时校验，防止把旧预览覆盖到被别人改过的明细上。
     */
    @Column(name = "item_version", nullable = false)
    private Long itemVersion = 0L;

    /**
     * 【主数据访问路线：@OneToMany + @MapKeyColumn 的 Map 集合映射】
     * key = 投放阶段（MASH_IN / BOIL_60 / DRYHOP_1 …），落在 recipe_item.stage 列；
     * value = 该阶段的原料用量明细实体。
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinTable(
            name = "recipe_item_slot",
            joinColumns = @JoinColumn(name = "recipe_id"),
            inverseJoinColumns = @JoinColumn(name = "item_id", unique = true))
    @MapKeyColumn(name = "stage")
    private Map<String, RecipeItem> items = new LinkedHashMap<>();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
