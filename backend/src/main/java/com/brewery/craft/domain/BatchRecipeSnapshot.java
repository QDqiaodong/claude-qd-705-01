package com.brewery.craft.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 批次创建时的配方用量【快照】（每批次至多一条）。
 *
 * 与质检轮次快照同一原则：批次只在创建的那一刻按当时的配方版本复制用量，之后配方的
 * 单位规范化、缩放、明细增删都不再影响历史批次 —— 已生产批次永远显示创建当时的用量。
 * 快照里同时保存显示值与规范化值，列表/阶段小计/占比由统一计量引擎只读计算。
 *
 * 历史兼容：迁移前已存在的批次由数据迁移回填一份快照（标记 LEGACY）；配方已被删除或
 * 明细单位无法推导的批次保留快照但标记 UNRESOLVED，不静默按错误单位计算。
 */
@Data
@Entity
@Table(name = "batch_recipe_snapshot")
public class BatchRecipeSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "batch_no", nullable = false, length = 24)
    private String batchNo;

    /** 创建批次时引用的配方（可能已被删除，名称冗余存下） */
    @Column(name = "recipe_id")
    private Long recipeId;

    @Column(name = "recipe_code", length = 24)
    private String recipeCode;

    @Column(name = "recipe_name", length = 64)
    private String recipeName;

    /** 快照时的配方基准批量（L） */
    @Column(name = "base_batch_size_l", precision = 10, scale = 2)
    private java.math.BigDecimal baseBatchSizeL;

    /** 该批次计划批量（L） */
    @Column(name = "batch_volume_l", precision = 10, scale = 2)
    private java.math.BigDecimal batchVolumeL;

    /** LIVE = 批次创建实时快照；LEGACY = 迁移为历史批次回填 */
    @Column(name = "snapshot_kind", nullable = false, length = 8)
    private String snapshotKind;

    /** 快照时的配方明细版本（历史回填为空） */
    @Column(name = "recipe_item_version")
    private Long recipeItemVersion;

    /** 快照中的待处理单位条数 */
    @Column(name = "unresolved_count", nullable = false)
    private Integer unresolvedCount = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 不可变用量明细行（@ElementCollection + @OrderColumn，只随本表 INSERT 写入） */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "batch_recipe_snapshot_item", joinColumns = @JoinColumn(name = "snapshot_id"))
    @OrderColumn(name = "list_index")
    private List<BatchSnapshotItem> items = new ArrayList<>();
}
