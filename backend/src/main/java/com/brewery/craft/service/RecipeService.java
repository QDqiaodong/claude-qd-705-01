package com.brewery.craft.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.brewery.craft.common.BizException;
import com.brewery.craft.domain.Ingredient;
import com.brewery.craft.domain.Recipe;
import com.brewery.craft.domain.RecipeItem;
import com.brewery.craft.dto.MetricDtos;
import com.brewery.craft.dto.RecipeDtos;
import com.brewery.craft.dto.Views;
import com.brewery.craft.repository.IngredientRepository;
import com.brewery.craft.repository.RecipeRepository;
import com.brewery.craft.service.measure.MetricEngine;
import com.brewery.craft.service.measure.RecipeScaler;
import com.brewery.craft.service.measure.UnitCatalog;

/**
 * 模块三：配方与原料用量。
 *
 * 用量明细走 {@code Recipe#getItems()} 这张 {@code @OneToMany + @MapKeyColumn} 的 Map，
 * 这里集中实现 Map 的增（put）删（remove）改（拿到 entity 改字段）查（按 key 取）。
 *
 * 计量：列表、占比、阶段小计、缩放全部走 {@link MetricEngine}/{@link RecipeScaler}，
 * 明细保存时同步做单位规范化（旧写法/空单位推导、未知单位保留并标记）。
 */
@Service
@Transactional(readOnly = true)
public class RecipeService {

    /** 历史配方没有基准批量列时的兼容默认（与页面「1000L 标准批次」口径一致） */
    public static final BigDecimal DEFAULT_BASE_BATCH = new BigDecimal("1000");

    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;

    public RecipeService(RecipeRepository recipeRepository, IngredientRepository ingredientRepository) {
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
    }

    public List<Views.RecipeView> list() {
        List<Views.RecipeView> list = new ArrayList<>();
        for (Recipe r : recipeRepository.findAllByOrderByCodeAsc()) {
            list.add(Views.RecipeView.of(r));
        }
        return list;
    }

    public Views.RecipeView get(Long id) {
        return Views.RecipeView.of(require(id));
    }

    public Recipe require(Long id) {
        return recipeRepository.findById(id).orElseThrow(() -> new BizException("配方不存在"));
    }

    @Transactional
    public Views.RecipeView create(RecipeDtos.RecipeSaveReq req) {
        if (recipeRepository.existsByCode(req.getCode())) {
            throw new BizException("配方编码 " + req.getCode() + " 已存在");
        }
        if (Views.styleName(req.getStyle()).equals(req.getStyle())) {
            throw new BizException("啤酒风格取值非法：" + req.getStyle());
        }
        if (req.getBaseBatchSizeL() != null && req.getBaseBatchSizeL().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("基准批量必须大于 0");
        }
        Recipe r = new Recipe();
        r.setCode(req.getCode());
        r.setName(req.getName());
        r.setStyle(req.getStyle());
        r.setTargetOg(req.getTargetOg());
        r.setTargetAbv(req.getTargetAbv());
        r.setTargetIbu(req.getTargetIbu());
        r.setTargetEbc(req.getTargetEbc());
        r.setBaseBatchSizeL(req.getBaseBatchSizeL());
        r.setItemVersion(0L);
        r.setProcessNote(req.getProcessNote());
        r.setUpdatedAt(LocalDateTime.now());
        return Views.RecipeView.of(recipeRepository.save(r));
    }

    /** 【Map 查询】整张 Map：stage → 用量明细（带统一规范化结果） */
    public List<Views.RecipeItemView> items(Long id) {
        return get(id).getItems();
    }

    /** 【Map 查询】按 key 查某一条 */
    public Views.RecipeItemView item(Long id, String stage) {
        Recipe r = require(id);
        RecipeItem item = r.getItems().get(stage);
        if (item == null) {
            throw new BizException("阶段 " + Views.stageName(stage) + " 还没有投放记录");
        }
        return Views.RecipeItemView.of(stage, item);
    }

    /**
     * 【Map 新增 / 修改】key 已存在 → 改用量；不存在 → put 一条新的。
     * 保存时即完成单位规范化；单位无法识别/空且类别不可推导时允许保存但标记 UNRESOLVED，
     * 该明细不参与任何合计（保留原值待人工处理），而不是静默按错误单位计算。
     */
    @Transactional
    public Views.RecipeItemView putItem(Long id, String stage, RecipeDtos.ItemSaveReq req) {
        Recipe r = require(id);
        if (req.getQuantity() == null || req.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("用量必须大于 0");
        }
        Ingredient ing = ingredientRepository.findByCode(req.getIngredientCode())
                .orElseThrow(() -> new BizException("原料 " + req.getIngredientCode() + " 不在库存档案中"));

        RecipeItem item = r.getItems().get(stage);
        boolean isNew = item == null;
        if (isNew) {
            item = new RecipeItem();
        }
        item.setIngredientCode(ing.getCode());
        item.setIngredientName(ing.getName());
        item.setIngredientType(ing.getType());
        item.setQuantity(req.getQuantity());
        // 空单位沿用原料档案的习惯单位（仍然过规范化解析，保证状态可解释）
        String rawUnit = (req.getUnit() == null || req.getUnit().isBlank()) ? ing.getUnit() : req.getUnit();
        item.setUnit(rawUnit);
        applyUnitResolution(item);
        item.setNote(req.getNote());
        if (isNew) {
            r.getItems().put(stage, item);
        }
        touchItems(r);
        recipeRepository.save(r);
        return Views.RecipeItemView.of(stage, item);
    }

    /** 按统一单位目录解析并回写规范化字段 */
    private void applyUnitResolution(RecipeItem item) {
        MetricDtos.UnitResolution res = UnitCatalog.resolve(item.getUnit(), item.getIngredientType());
        item.setCanonicalUnit(res.getCanonicalUnit());
        item.setUnitStatus(res.getStatus());
        item.setUnitNote(res.getNote());
        if (res.resolved()) {
            BigDecimal factor = UnitCatalog.factorToBase(res.getCanonicalUnit());
            item.setBaseQuantity(MetricEngine.scale(item.getQuantity().multiply(factor)));
        } else {
            item.setBaseQuantity(null);
        }
    }

    /** 【Map 删除】按 key 移除一条用量明细（orphanRemoval 会把行删掉） */
    @Transactional
    public Views.RecipeView removeItem(Long id, String stage) {
        Recipe r = require(id);
        RecipeItem removed = r.getItems().remove(stage);
        if (removed == null) {
            throw new BizException("阶段 " + Views.stageName(stage) + " 没有投放记录，无需删除");
        }
        touchItems(r);
        recipeRepository.save(r);
        return Views.RecipeView.of(r);
    }

    /** 环形图/列表/摘要共用的原料构成（统一计量引擎的分维度占比） */
    public List<MetricDtos.CompositionSlice> composition(Long id) {
        Recipe r = require(id);
        return MetricEngine.build(RecipeMetricsFactory.fromRecipe(r)).getComposition();
    }

    /** 整张配方的统一计量摘要（规范化明细、阶段小计、分维度合计、待处理项） */
    public MetricDtos.RecipeMetrics metrics(Long id) {
        Recipe r = require(id);
        return MetricEngine.build(RecipeMetricsFactory.fromRecipe(r));
    }

    // ---------------- 配方缩放 ----------------

    /**
     * 缩放预览（不落库）：展示原值、换算值、等比例精确值、初舍入值、余差去向与最终建议值，
     * 以及缩放后逐维度规范化合计与目标合计的对照。
     */
    public MetricDtos.ScaleView previewScale(Long id, RecipeDtos.ScalePreviewReq req) {
        Recipe r = require(id);
        if (r.getItems().isEmpty()) {
            throw new BizException("配方还没有用量明细，无法缩放");
        }
        BigDecimal target = requiredPositive(req.getTargetBatchSizeL(), "目标批量");
        BigDecimal base = resolveBaseBatch(r, req.getBaseBatchSizeL());
        MetricDtos.ScaleView view = RecipeScaler.scale(
                MetricEngine.build(RecipeMetricsFactory.fromRecipe(r)), base, target);
        view.setRecipeId(r.getId());
        view.setRecipeName(r.getName());
        view.setFingerprint(fingerprint(r));
        return view;
    }

    /**
     * 确认应用缩放：
     *   - fingerprint 必须匹配当前配方的明细版本，预览后明细被改过 → 409 式业务报错，要求重新预览；
     *   - 以预览同款输入（当前明细、基准/目标批量）重新算一遍再落库，杜绝用旧预览覆盖新数据；
     *   - 数量落回酿酒师习惯单位，规范化字段同步刷新；明细版本 +1。
     */
    @Transactional
    public MetricDtos.ScaleView applyScale(Long id, RecipeDtos.ScaleApplyReq req) {
        Recipe r = require(id);
        if (r.getItems().isEmpty()) {
            throw new BizException("配方还没有用量明细，无法缩放");
        }
        if (!fingerprint(r).equals(req.getFingerprint())) {
            throw new BizException("缩放预览已过期：预览之后配方明细被修改过（当前版本 "
                    + r.getItemVersion() + "），请重新预览后再应用");
        }
        BigDecimal target = requiredPositive(req.getTargetBatchSizeL(), "目标批量");
        BigDecimal base = resolveBaseBatch(r, req.getBaseBatchSizeL());
        MetricDtos.ScaleView view = RecipeScaler.scale(
                MetricEngine.build(RecipeMetricsFactory.fromRecipe(r)), base, target);
        if (view.getUnresolvedCount() > 0) {
            throw new BizException("配方存在 " + view.getUnresolvedCount()
                    + " 条单位待处理明细，不能整体缩放；请先在用量明细中修正单位");
        }

        // stage → 最终建议值，按当前 Map 逐条覆盖（预览与应用之间版本一致，key 集合相同）
        Map<String, MetricDtos.ScaleItem> byStage = new LinkedHashMap<>();
        for (MetricDtos.ScaleItem it : view.getItems()) {
            byStage.put(it.getStage(), it);
        }
        for (Map.Entry<String, RecipeItem> e : r.getItems().entrySet()) {
            MetricDtos.ScaleItem planned = byStage.get(e.getKey());
            if (planned == null || planned.getFinalQuantity() == null) {
                continue;
            }
            RecipeItem item = e.getValue();
            item.setQuantity(planned.getFinalQuantity());
            item.setUnit(planned.getFinalUnit());
            applyUnitResolution(item);
        }
        touchItems(r);
        recipeRepository.save(r);

        // 返回值按应用后的配方重算一次，保证页面拿到的合计与落库完全一致
        MetricDtos.ScaleView applied = RecipeScaler.scale(
                MetricEngine.build(RecipeMetricsFactory.fromRecipe(r)), base, target);
        applied.setRecipeId(r.getId());
        applied.setRecipeName(r.getName());
        applied.setFingerprint(fingerprint(r));
        return applied;
    }

    /** 明细版本指纹：配方 id + 明细版本号（应用缩放时强校验） */
    public String fingerprint(Recipe r) {
        return "R" + r.getId() + ":V" + (r.getItemVersion() == null ? 0L : r.getItemVersion());
    }

    private void touchItems(Recipe r) {
        r.setItemVersion((r.getItemVersion() == null ? 0L : r.getItemVersion()) + 1);
        r.setUpdatedAt(LocalDateTime.now());
    }

    private BigDecimal resolveBaseBatch(Recipe r, BigDecimal requestOverride) {
        if (requestOverride != null) {
            return requiredPositive(requestOverride, "基准批量");
        }
        if (r.getBaseBatchSizeL() != null && r.getBaseBatchSizeL().compareTo(BigDecimal.ZERO) > 0) {
            return r.getBaseBatchSizeL();
        }
        return DEFAULT_BASE_BATCH;
    }

    private BigDecimal requiredPositive(BigDecimal v, String label) {
        if (v == null || v.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(label + "必须大于 0");
        }
        return v;
    }

    /** 所有可用投放阶段（UI 下拉用） */
    public List<Map<String, String>> stages() {
        List<Map<String, String>> list = new ArrayList<>();
        for (String key : Views.STAGE_ORDER) {
            list.add(Map.of("key", key, "name", Views.stageName(key)));
        }
        return list;
    }
}
