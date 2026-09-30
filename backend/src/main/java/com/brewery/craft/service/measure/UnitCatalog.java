package com.brewery.craft.service.measure;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import com.brewery.craft.dto.MetricDtos;

/**
 * 单位目录：单位规范化与维度换算的唯一事实来源。
 *
 * 规则（接口、页面、迁移、快照共用这一套，不允许各处再写一份字符串判断）：
 *   1. 单位匹配大小写不敏感、去空格，并兼容常见中文写法（千克/克/升/毫升/包…）；
 *   2. 同一维度内先换算到规范单位再汇总：kg↔g（1000）、L↔mL（1000）；
 *   3. 质量与体积互不换算，pkg（计件）独立一维；
 *   4. 历史空单位/旧写法按原料类别推导：MALT/HOP/WATER → kg，YEAST → pkg，
 *      ADJUNCT 不确定（可能 kg 也可能 pkg）→ UNRESOLVED，保留原值并标记待处理。
 */
public final class UnitCatalog {

    private UnitCatalog() {
    }

    /** 单位 → 维度（规范名与别名全部小写存放） */
    private static final Map<String, String> ALIAS_DIMENSION = new LinkedHashMap<>();
    /** 单位别名 → 规范单位 */
    private static final Map<String, String> ALIAS_CANONICAL = new LinkedHashMap<>();
    /** 规范单位 → 换算到基准单位的倍率 */
    private static final Map<String, BigDecimal> FACTOR = new LinkedHashMap<>();

    static {
        // 质量
        register(Dimension.MASS, "kg", "1", "kg", "kgs", "kilogram", "kilograms", "千克", "公斤");
        register(Dimension.MASS, "g", "0.001", "g", "gram", "grams", "克");
        // 体积
        register(Dimension.VOLUME, "L", "1", "l", "liter", "liters", "litre", "litres", "升", "公升");
        register(Dimension.VOLUME, "mL", "0.001", "ml", "milliliter", "milliliters", "millilitre",
                "millilitres", "毫升");
        // 计件
        register(Dimension.COUNT, "pkg", "1", "pkgs", "pack", "packs", "package", "packages",
                "sachet", "sachets", "bag", "bags", "包", "袋", "小包");
    }

    private static void register(Dimension dim, String canonical, String factorToBase, String... aliases) {
        FACTOR.put(canonical, new BigDecimal(factorToBase));
        for (String alias : aliases) {
            ALIAS_DIMENSION.put(aliasKey(alias), dim.name());
            ALIAS_CANONICAL.put(aliasKey(alias), canonical);
        }
    }

    private static String aliasKey(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase();
    }

    public static boolean isKnown(String unit) {
        return ALIAS_DIMENSION.containsKey(aliasKey(unit));
    }

    public static String canonicalize(String unit) {
        return ALIAS_CANONICAL.get(aliasKey(unit));
    }

    public static Dimension dimensionOf(String unit) {
        String code = ALIAS_DIMENSION.get(aliasKey(unit));
        return code == null ? null : Dimension.valueOf(code);
    }

    /** 该单位换算到所在维度基准单位的倍率；未知单位返回 null */
    public static BigDecimal factorToBase(String canonicalUnit) {
        return FACTOR.get(canonicalUnit);
    }

    /**
     * 按「显式单位 → 原料类别推导 → 无法可靠推导」的顺序解析一条历史/新录入单位。
     *
     * @param rawUnit        明细上记录的原始单位（可能为空、大小写不一、旧写法）
     * @param ingredientType 原料大类 MALT/HOP/YEAST/ADJUNCT/WATER，用于空单位推导
     * @return 永不返回 null；无法可靠推导时 status = UNRESOLVED，原值原样保留
     */
    public static MetricDtos.UnitResolution resolve(String rawUnit, String ingredientType) {
        return resolve(rawUnit, ingredientType, null);
    }

    /**
     * 按「显式单位 → 原料档案单位 → 原料类别推导 → 无法可靠推导」解析。
     *
     * @param fallbackUnit 原料库存档案上的习惯单位（历史空单位优先按它补全，属于「现有数据推导」）
     */
    public static MetricDtos.UnitResolution resolve(String rawUnit, String ingredientType, String fallbackUnit) {
        MetricDtos.UnitResolution r = new MetricDtos.UnitResolution();
        String raw = rawUnit == null ? "" : rawUnit.trim();
        r.setRawUnit(raw);
        if (!raw.isEmpty()) {
            String canonical = canonicalize(raw);
            if (canonical != null) {
                boolean same = canonical.equals(raw);
                r.setCanonicalUnit(canonical);
                r.setDimension(dimensionOf(canonical).name());
                r.setStatus(same ? "OK" : "DERIVED");
                if (!same) {
                    r.setNote("原始单位「" + raw + "」已按 " + canonical
                            + "（" + dimensionOf(canonical).label() + "）规范化");
                }
                return r;
            }
            // 有明确单位但系统不认识：没有可靠的换算依据，保留原值并标记待处理
            r.setCanonicalUnit(null);
            r.setDimension(null);
            r.setStatus("UNRESOLVED");
            r.setNote("无法识别的单位「" + raw + "」，已保留原值，请人工核对后改为 "
                    + supportedUnits() + " 之一");
            return r;
        }
        // 空单位：先信原料档案上的习惯单位（同一条原料档案单位是库存与领用一贯口径）
        if (fallbackUnit != null && !fallbackUnit.isBlank()) {
            String canonical = canonicalize(fallbackUnit);
            if (canonical != null) {
                Dimension dim = dimensionOf(canonical);
                r.setCanonicalUnit(canonical);
                r.setDimension(dim.name());
                r.setStatus("DERIVED");
                r.setNote("单位为空，已按原料档案单位 " + canonical + "（" + dim.label() + "）补全");
                return r;
            }
        }
        // 再按原料类别推导
        String inferred = inferUnitByType(ingredientType);
        if (inferred != null) {
            Dimension dim = dimensionOf(inferred);
            r.setCanonicalUnit(inferred);
            r.setDimension(dim.name());
            r.setStatus("DERIVED");
            r.setNote("单位为空，已按原料类别" + typeName(ingredientType)
                    + "推导为 " + inferred + "（" + dim.label() + "）");
            return r;
        }
        r.setCanonicalUnit(null);
        r.setDimension(null);
        r.setStatus("UNRESOLVED");
        r.setNote("单位为空且原料类别" + typeName(ingredientType)
                + "无法可靠推导（可能 kg 也可能 pkg），已保留空值并标记待处理");
        return r;
    }

    /**
     * 空单位的类别推导：麦芽/酒花/水处理剂默认计重 kg，酵母默认计件 pkg；
     * 辅料（ADJUNCT）既可能是果泥/香辛料（kg）也可能是菌粉包（pkg），不猜。
     */
    private static String inferUnitByType(String type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case "MALT", "HOP", "WATER" -> "kg";
            case "YEAST" -> "pkg";
            default -> null;
        };
    }

    private static String typeName(String type) {
        return switch (type == null ? "" : type) {
            case "MALT" -> "麦芽";
            case "HOP" -> "啤酒花";
            case "YEAST" -> "酵母";
            case "ADJUNCT" -> "辅料";
            case "WATER" -> "水处理剂";
            default -> "未知(" + type + ")";
        };
    }

    /** 页面录入提示 / 报错文案里给酿酒师看的可选单位 */
    public static String supportedUnits() {
        return "kg / g（质量）、L / mL（体积）、pkg（计件）";
    }
}
