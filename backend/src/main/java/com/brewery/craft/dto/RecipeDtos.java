package com.brewery.craft.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 模块三（配方与原料用量）入参。 */
public final class RecipeDtos {

    private RecipeDtos() {
    }

    @Data
    public static class RecipeSaveReq {

        @NotBlank(message = "配方编码不能为空")
        private String code;

        @NotBlank(message = "配方名不能为空")
        private String name;

        @NotBlank(message = "风格不能为空")
        private String style;

        private BigDecimal targetOg;

        private BigDecimal targetAbv;

        private BigDecimal targetIbu;

        private BigDecimal targetEbc;

        /** 配方基准批量（L），缩放比例分母；空按 1000L 兼容 */
        private BigDecimal baseBatchSizeL;

        private String processNote;
    }

    /**
     * 新增或修改「某一个投放阶段」的用量。
     * Map 的 key 走路径变量 {stage}，body 只带这条明细本身。
     */
    @Data
    public static class ItemSaveReq {

        @NotBlank(message = "原料编码不能为空")
        private String ingredientCode;

        private String ingredientName;

        private String ingredientType;

        private BigDecimal quantity;

        /** 允许 kg/g/L/mL/pkg，大小写不敏感；空单位按原料类别推导（ADJUNCT 不猜） */
        private String unit;

        private String note;
    }

    /** 配方缩放预览：只计算不落库 */
    @Data
    public static class ScalePreviewReq {

        /** 目标批量 L（必填，>0） */
        private BigDecimal targetBatchSizeL;

        /**
         * 配方基准批量 L。可空：空则用配方档案上的基准批量（再空兜底 1000L）。
         * 显式传入时只用于本次预览，不改配方档案。
         */
        private BigDecimal baseBatchSizeL;
    }

    /**
     * 确认应用缩放：必须带上预览时返回的 fingerprint（= 配方 id + 明细版本）。
     * 明细在预览之后被任何人改过都会导致版本不一致，服务端拒绝并要求重新预览。
     */
    @Data
    public static class ScaleApplyReq {

        private BigDecimal targetBatchSizeL;

        private BigDecimal baseBatchSizeL;

        @NotBlank(message = "缺少预览指纹，请重新打开缩放预览")
        private String fingerprint;
    }
}
