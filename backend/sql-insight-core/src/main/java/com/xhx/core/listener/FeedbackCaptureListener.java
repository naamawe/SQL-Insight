package com.xhx.core.listener;

import com.xhx.ai.listener.ChatStreamListener;
import com.xhx.ai.model.ChartConfigDTO;
import com.xhx.core.service.chart.ChartConfigService;
import com.xhx.core.service.sql.ChatRecordService;
import com.xhx.dal.entity.ChartConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务后处理监听器（装饰器）：在 SSE 推送的基础上，拦截摘要累积、图表保存、完成持久化等业务逻辑
 * @author master
 */
@Slf4j
public class FeedbackCaptureListener implements ChatStreamListener {

    private final ChatStreamListener    delegate;
    private final AtomicLong            recordId;
    private final List<Map<String, Object>> data;
    private final ChatRecordService     chatRecordService;
    private final ChartConfigService    chartConfigService;
    private final StringBuilder         summaryBuffer = new StringBuilder();

    public FeedbackCaptureListener(ChatStreamListener delegate,
                                   AtomicLong recordId,
                                   List<Map<String, Object>> data,
                                   ChatRecordService chatRecordService,
                                   ChartConfigService chartConfigService) {
        this.delegate          = delegate;
        this.recordId          = recordId;
        this.data              = data;
        this.chatRecordService = chatRecordService;
        this.chartConfigService = chartConfigService;
    }

    @Override
    public void onStage(String message) {
        delegate.onStage(message);
    }

    @Override
    public void onSql(String sql, boolean corrected) {
        delegate.onSql(sql, corrected);
    }

    @Override
    public void onData(List<Map<String, Object>> data, Long sessionId, Long recordId) {
        delegate.onData(data, sessionId, recordId);
    }

    @Override
    public void onSummaryToken(String token) {
        summaryBuffer.append(token);
        delegate.onSummaryToken(token);
    }

    @Override
    public void onChartConfig(ChartConfigDTO chartConfig) {
        delegate.onChartConfig(chartConfig);
        if (chartConfig != null) {
            saveChartConfig(chartConfig);
        }
    }

    @Override
    public void onComplete() {
        chatRecordService.cacheResult(recordId.get(), data, summaryBuffer.toString());
        delegate.onComplete();
    }

    @Override
    public void onError(String message) {
        if (!summaryBuffer.isEmpty()) {
            try {
                chatRecordService.cacheResult(recordId.get(), data, summaryBuffer.toString());
                log.info("连接异常断开，已保存部分摘要，recordId: {}", recordId.get());
            } catch (Exception e) {
                log.warn("异常断开时摘要保存失败：{}", e.getMessage());
            }
        }
        delegate.onError(message);
    }

    private void saveChartConfig(ChartConfigDTO chartConfig) {
        ChartConfig config = ChartConfig.builder()
                .recordId(recordId.get())
                .type(chartConfig.getType())
                .xAxis(chartConfig.getXAxis())
                .yAxis(chartConfig.getYAxis())
                .title(chartConfig.getTitle())
                .isUserModified(false)
                .build();
        chartConfigService.saveOrUpdate(config);
        log.info("图表配置已保存，recordId: {}, type: {}", recordId.get(), chartConfig.getType());
    }
}
