package com.brewery.craft.service.measure;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 计量维度。
 *
 * 统一计量规则的第一条：汇总只允许在同一个维度内进行。
 * 质量（kg/g）、体积（L/mL）、计件（pkg）分属三个维度，相互之间没有可靠换算关系，
 * 因此质量与体积永远不能直接相加，计件更不能并进质量/体积合计。
 */
public enum Dimension {

    /** 质量：规范单位 kg（克 g 先换算成 kg 再参与计算） */
    MASS("kg", "质量"),
    /** 体积：规范单位 L（毫升 mL 先换算成 L 再参与计算） */
    VOLUME("L", "体积"),
    /** 计件：规范单位 pkg（包/袋，不可再拆的最小投料包装） */
    COUNT("pkg", "计件");

    /** 该维度内的规范（基准）单位：所有汇总、占比、缩放校验都以它为准 */
    private final String baseUnit;
    private final String label;

    Dimension(String baseUnit, String label) {
        this.baseUnit = baseUnit;
        this.label = label;
    }

    public String baseUnit() {
        return baseUnit;
    }

    public String label() {
        return label;
    }

    private static final Map<String, Dimension> BY_CODE = new LinkedHashMap<>();

    static {
        for (Dimension d : values()) {
            BY_CODE.put(d.name(), d);
        }
    }

    public static Dimension byCode(String code) {
        return code == null ? null : BY_CODE.get(code);
    }
}
