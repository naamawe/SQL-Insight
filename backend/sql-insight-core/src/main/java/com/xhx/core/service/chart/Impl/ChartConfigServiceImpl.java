package com.xhx.core.service.chart.Impl;

import com.xhx.core.service.chart.ChartConfigService;
import com.xhx.dal.entity.ChartConfig;
import com.xhx.dal.mapper.ChartConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChartConfigServiceImpl implements ChartConfigService {

    private final ChartConfigMapper chartConfigMapper;

    @Override
    public void saveOrUpdate(ChartConfig config) {
        if (config.getId() == null) {
            // 先检查是否已存在（同一 recordId 只能有一条记录）
            ChartConfig existing = chartConfigMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChartConfig>()
                            .eq(ChartConfig::getRecordId, config.getRecordId())
            );
            if (existing != null) {
                // 已存在：如果用户曾手动修改过，则跳过更新（保留用户配置）
                if (Boolean.TRUE.equals(existing.getIsUserModified())) {
                    log.info("用户已手动修改过图表配置，跳过 AI 推荐更新，recordId: {}", config.getRecordId());
                    return;
                }
                // 未手动修改过，则更新为新的 AI 推荐
                config.setId(existing.getId());
                chartConfigMapper.updateById(config);
                log.info("图表配置已更新（AI 推荐），recordId: {}", config.getRecordId());
                return;
            }
            // 否则插入
            chartConfigMapper.insert(config);
            log.info("图表配置已保存（AI 推荐），recordId: {}", config.getRecordId());
        } else {
            chartConfigMapper.updateById(config);
            log.info("图表配置已更新（用户手动），id: {}", config.getId());
        }
    }

    @Override
    public Optional<ChartConfig> getByRecordId(Long recordId) {
        ChartConfig config = chartConfigMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChartConfig>()
                        .eq(ChartConfig::getRecordId, recordId)
        );
        return Optional.ofNullable(config);
    }

    @Override
    public void deleteByRecordId(Long recordId) {
        chartConfigMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChartConfig>()
                        .eq(ChartConfig::getRecordId, recordId)
        );
        log.info("图表配置已删除，recordId: {}", recordId);
    }
}