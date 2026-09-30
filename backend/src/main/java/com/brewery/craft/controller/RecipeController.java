package com.brewery.craft.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.brewery.craft.common.ApiResponse;
import com.brewery.craft.dto.RecipeDtos;
import com.brewery.craft.dto.Views;
import com.brewery.craft.service.RecipeService;

import jakarta.validation.Valid;

/**
 * 模块三：配方与原料用量。
 *
 * 【主数据访问路线】用量明细是 {@code Recipe.items} ——
 * {@code @OneToMany + @MapKeyColumn(name="stage")} 的 Map 集合映射，
 * 这里给出对它的：整表查 / 按 key 查 / 按 key 增改 / 按 key 删。
 */
@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping
    public ApiResponse<List<Views.RecipeView>> list() {
        return ApiResponse.success(recipeService.list());
    }

    /** 投放阶段字典（Map 的 key 全表） */
    @GetMapping("/stages")
    public ApiResponse<List<Map<String, String>>> stages() {
        return ApiResponse.success(recipeService.stages());
    }

    @PostMapping
    public ApiResponse<Views.RecipeView> create(@Valid @RequestBody RecipeDtos.RecipeSaveReq req) {
        return ApiResponse.success("配方已新建", recipeService.create(req));
    }

    @GetMapping("/{id}")
    public ApiResponse<Views.RecipeView> get(@PathVariable("id") Long id) {
        return ApiResponse.success(recipeService.get(id));
    }

    /** 【Map 查】某个配方的全部投放阶段用量 */
    @GetMapping("/{id}/items")
    public ApiResponse<List<Views.RecipeItemView>> items(@PathVariable("id") Long id) {
        return ApiResponse.success(recipeService.items(id));
    }

    /** 【Map 按 key 查】查某一个投放阶段 */
    @GetMapping("/{id}/items/{stage}")
    public ApiResponse<Views.RecipeItemView> item(@PathVariable("id") Long id,
                                                  @PathVariable("stage") String stage) {
        return ApiResponse.success(recipeService.item(id, stage));
    }

    /** 【Map 增 / 改】key 存在即改用量，不存在即新增一条 */
    @PutMapping("/{id}/items/{stage}")
    public ApiResponse<Views.RecipeItemView> putItem(@PathVariable("id") Long id,
                                                     @PathVariable("stage") String stage,
                                                     @Valid @RequestBody RecipeDtos.ItemSaveReq req) {
        return ApiResponse.success("用量已保存", recipeService.putItem(id, stage, req));
    }

    /** 【Map 删】按 key 删除某个阶段的投放记录 */
    @DeleteMapping("/{id}/items/{stage}")
    public ApiResponse<Views.RecipeView> removeItem(@PathVariable("id") Long id,
                                                    @PathVariable("stage") String stage) {
        return ApiResponse.success("用量已删除", recipeService.removeItem(id, stage));
    }

    /** 环形图用的原料构成（统一计量引擎的分维度占比） */
    @GetMapping("/{id}/composition")
    public ApiResponse<List<com.brewery.craft.dto.MetricDtos.CompositionSlice>> composition(
            @PathVariable("id") Long id) {
        return ApiResponse.success(recipeService.composition(id));
    }

    /** 整张配方的统一计量摘要：规范化明细、阶段小计、分维度合计、待处理项 */
    @GetMapping("/{id}/metrics")
    public ApiResponse<com.brewery.craft.dto.MetricDtos.RecipeMetrics> metrics(@PathVariable("id") Long id) {
        return ApiResponse.success(recipeService.metrics(id));
    }

    /** 缩放预览：原值/换算值/舍入/余差去向 + 目标合计对照，不落库 */
    @PostMapping("/{id}/scale-preview")
    public ApiResponse<com.brewery.craft.dto.MetricDtos.ScaleView> previewScale(
            @PathVariable("id") Long id,
            @Valid @RequestBody RecipeDtos.ScalePreviewReq req) {
        return ApiResponse.success(recipeService.previewScale(id, req));
    }

    /** 确认应用缩放：强校验预览指纹，明细被改过则拒绝并要求重新预览 */
    @PostMapping("/{id}/scale-apply")
    public ApiResponse<com.brewery.craft.dto.MetricDtos.ScaleView> applyScale(
            @PathVariable("id") Long id,
            @Valid @RequestBody RecipeDtos.ScaleApplyReq req) {
        return ApiResponse.success("缩放已应用", recipeService.applyScale(id, req));
    }
}
