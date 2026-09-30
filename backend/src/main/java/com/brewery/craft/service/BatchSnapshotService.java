package com.brewery.craft.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.brewery.craft.domain.Batch;
import com.brewery.craft.domain.BatchRecipeSnapshot;
import com.brewery.craft.domain.BatchSnapshotItem;
import com.brewery.craft.domain.Recipe;
import com.brewery.craft.domain.RecipeItem;
import com.brewery.craft.dto.MetricDtos;
import com.brewery.craft.dto.Views;
import com.brewery.craft.repository.BatchRecipeSnapshotRepository;
import com.brewery.craft.repository.RecipeRepository;
import com.brewery.craft.service.measure.MetricEngine;
import com.brewery.craft.service.measure.UnitCatalog;

/**
 * 批次配方用量快照。
 *
 * 唯一原则：批次的历史用量在批次【创建时】钉死，之后配方的单位规范化、缩放、明细增删
 * 都不再回写历史批次。批次详情页看到的合计、阶段小计、占比，都是统一计量引擎对
 * 快照行只读计算的结果，与配方页同源，但输入是不可变快照。
 */
@Service
public class BatchSnapshotService {

    private final BatchRecipeSnapshotRepository snapshotRepository;
    private final RecipeRepository recipeRepository;

    public BatchSnapshotService(BatchRecipeSnapshotRepository snapshotRepository,
                                RecipeRepository recipeRepository) {
        this.snapshotRepository = snapshotRepository;
        this.recipeRepository = recipeRepository;
    }

    /** 批次创建时实时快照（LIVE）。配方不存在时不阻断建档，返回无明细的空快照。 */
    @Transactional
    public BatchRecipeSnapshot snapshotForNewBatch(Batch batch, Recipe recipe) {
        BatchRecipeSnapshot s = new BatchRecipeSnapshot();
        s.setBatchId(batch.getId());
        s.setBatchNo(batch.getBatchNo());
        s.setBatchVolumeL(batch.getVolumeL());
        s.setSnapshotKind("LIVE");
        s.setCreatedAt(LocalDateTime.now());
        if (recipe != null) {
            fillFromRecipe(s, recipe);
        }
        return snapshotRepository.save(s);
    }

    private void fillFromRecipe(BatchRecipeSnapshot s, Recipe recipe) {
        s.setRecipeId(recipe.getId());
        s.setRecipeCode(recipe.getCode());
        s.setRecipeName(recipe.getName());
        s.setBaseBatchSizeL(recipe.getBaseBatchSizeL() == null
                ? RecipeService.DEFAULT_BASE_BATCH : recipe.getBaseBatchSizeL());
        s.setRecipeItemVersion(recipe.getItemVersion());
        int unresolved = 0;
        for (var entry : recipe.getItems().entrySet()) {
            RecipeItem it = entry.getValue();
            BatchSnapshotItem row = new BatchSnapshotItem();
            row.setStage(entry.getKey());
            row.setIngredientCode(it.getIngredientCode());
            row.setIngredientName(it.getIngredientName());
            row.setIngredientType(it.getIngredientType());
            row.setQuantity(it.getQuantity());
            row.setUnit(it.getUnit());
            row.setCanonicalUnit(it.getCanonicalUnit());
            row.setBaseQuantity(it.getBaseQuantity());
            row.setUnitStatus(it.getUnitStatus());
            row.setNote(it.getNote());
            if (it.getCanonicalUnit() != null) {
                row.setDimension(UnitCatalog.dimensionOf(it.getCanonicalUnit()).name());
            }
            if ("UNRESOLVED".equals(it.getUnitStatus())) {
                unresolved++;
            }
            s.getItems().add(row);
        }
        s.setUnresolvedCount(unresolved);
    }

    /** 详情场景：快照 + 统一计量结果；无快照（理论上只可能是迁移未覆盖）返回 null。 */
    public Views.BatchRecipeSnapshotView viewOfBatch(Long batchId) {
        BatchRecipeSnapshot s = snapshotRepository.findByBatchId(batchId).orElse(null);
        if (s == null) {
            return null;
        }
        MetricDtos.RecipeMetrics metrics = MetricEngine.build(RecipeMetricsFactory.fromSnapshot(s));
        return Views.BatchRecipeSnapshotView.of(s, metrics);
    }

    public boolean exists(Long batchId) {
        return snapshotRepository.findByBatchId(batchId).isPresent();
    }

    /**
     * 历史迁移：为迁移前创建、还没有快照的批次按【当时引用的配方当前版本】回填一份 LEGACY
     * 快照。注意这只是给历史批次一个可展示的用量快照，后续配方改动仍不会影响它。
     * 配方缺失的批次跳过（页面显示无用量快照）。
     */
    @Transactional
    public int backfillLegacy(List<Batch> legacyBatches, List<Recipe> recipes) {
        int n = 0;
        for (Batch b : legacyBatches) {
            if (b.getRecipeId() == null || exists(b.getId())) {
                continue;
            }
            Recipe recipe = recipes.stream().filter(r -> r.getId().equals(b.getRecipeId())).findFirst()
                    .orElse(null);
            if (recipe == null) {
                continue;
            }
            BatchRecipeSnapshot s = new BatchRecipeSnapshot();
            s.setBatchId(b.getId());
            s.setBatchNo(b.getBatchNo());
            s.setBatchVolumeL(b.getVolumeL());
            s.setSnapshotKind("LEGACY");
            s.setCreatedAt(LocalDateTime.now());
            fillFromRecipe(s, recipe);
            snapshotRepository.save(s);
            n++;
        }
        return n;
    }
}
