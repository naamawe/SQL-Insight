package com.xhx.web.controller;

import com.xhx.common.result.Result;
import com.xhx.ai.model.ChartConfigDTO;
import com.xhx.core.service.chart.ChartConfigService;
import com.xhx.dal.entity.ChartConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

import static com.xhx.common.constant.SystemPermissionConstants.USER;

/**
 * 图表配置 Controller
 *
 * @author master
 */
@Slf4j
@RestController
@RequestMapping("/api/chart")
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + USER + "')")
public class ChartConfigController {

    private final ChartConfigService chartConfigService;

    /**
     * 获取某条记录的图表配置
     */
    @GetMapping("/records/{recordId}")
    public Result<ChartConfig> getChartConfig(@PathVariable Long recordId) {
        Optional<ChartConfig> config = chartConfigService.getByRecordId(recordId);
        return config.map(Result::success)
                .orElse(Result.success(null));
    }

    /**
     * 保存/更新图表配置（用户手动修改）
     */
    @PostMapping("/records/{recordId}")
    public Result<Void> saveChartConfig(
            @PathVariable Long recordId,
            @RequestBody ChartConfigDTO request) {

        // 查询已有记录，若存在则带上 id，走直接 updateById 路径（跳过 isUserModified 拦截）
        ChartConfig.ChartConfigBuilder builder = ChartConfig.builder()
                .recordId(recordId)
                .type(request.getType())
                .xAxis(request.getXAxis())
                .yAxis(request.getYAxis())
                .title(request.getTitle())
                .isUserModified(true);

        chartConfigService.getByRecordId(recordId)
                .ifPresent(existing -> builder.id(existing.getId()));

        chartConfigService.saveOrUpdate(builder.build());
        return Result.success("图表配置已保存", null);
    }

    /**
     * 删除图表配置
     */
    @DeleteMapping("/records/{recordId}")
    public Result<Void> deleteChartConfig(@PathVariable Long recordId) {
        chartConfigService.deleteByRecordId(recordId);
        return Result.success("图表配置已删除", null);
    }


}