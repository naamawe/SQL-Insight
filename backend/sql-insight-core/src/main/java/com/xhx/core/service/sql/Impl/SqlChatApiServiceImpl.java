package com.xhx.core.service.sql.Impl;

import com.xhx.ai.listener.ChatStreamListener;
import com.xhx.ai.model.AiResponse;
import com.xhx.ai.model.ChartConfigDTO;
import com.xhx.ai.service.NlFeedbackGenerator;
import com.xhx.common.util.CommonUtil;
import com.xhx.core.service.chart.ChartConfigService;
import com.xhx.core.service.sql.*;
import com.xhx.dal.entity.ChartConfig;
import com.xhx.dal.entity.ChatSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlChatApiServiceImpl implements SqlChatApiService {

    private final SqlGeneratorService sqlGeneratorService;
    private final SqlExecutorService  sqlExecutorService;
    private final ChatSessionService  chatSessionService;
    private final NlFeedbackGenerator nlFeedbackGenerator;
    private final ChatRecordService   chatRecordService;
    private final ChartConfigService  chartConfigService;

    @Async("aiExecutor")
    @Override
    public void executeChatStream(Long userId, Long sessionId, Long dataSourceId,
                                  String question, ChatStreamListener listener) {
        Long finalSessionId = null;
        try {
            finalSessionId = resolveSessionId(userId, sessionId, dataSourceId, question);
            ChatSession session = chatSessionService.getSessionDetail(userId, finalSessionId);
            Long dsId = session.getDataSourceId();

            listener.onStage("正在生成 SQL...");
            String sql = sqlGeneratorService.generate(userId, finalSessionId, question);

            // AI 无法生成 SQL，直接返回解释文本
            if (CommonUtil.isExplain(sql)) {
                String warmMessage = sql.replace("[EXPLAIN]", "").trim();
                listener.onStage("AI 提示");
                listener.onSummaryToken(warmMessage);
                listener.onComplete();
                saveBlockedRecord(finalSessionId, question, warmMessage);
                return;
            }

            listener.onSql(sql, false);

            // 执行 SQL，内部处理自动纠错；null 表示纠错后 AI 返回 EXPLAIN，流程已在内部结束
            ExecuteResult result = executeWithRetry(userId, finalSessionId, dsId, sql, listener);
            if (result == null) {
                saveBlockedRecord(finalSessionId, question, "SQL 自动修正后仍无法执行");
                return;
            }
            final List<Map<String, Object>> data = result.data();
            final String finalSql = result.finalSql();
            final boolean corrected = result.corrected();

            // 先保存对话记录，获取 recordId
            Long recordId = chatRecordService.save(
                    finalSessionId, question, finalSql, data.size(), null, corrected);

            listener.onData(data, finalSessionId, recordId);

            // 使用 AtomicLong 传递 recordId 给内部监听器
            final AtomicLong savedRecordId = new AtomicLong(recordId);
            // 累积流式摘要
            final StringBuilder summaryBuffer = new StringBuilder();

            // 创建内部监听器，拦截图表配置和完成事件
            ChatStreamListener internalListener = new ChatStreamListener() {
                @Override
                public void onStage(String message) {
                    listener.onStage(message);
                }

                @Override
                public void onSql(String sql, boolean corrected) {
                    listener.onSql(sql, corrected);
                }

                @Override
                public void onData(List<Map<String, Object>> data, Long sessionId, Long recordId) {
                    listener.onData(data, sessionId, recordId);
                }

                @Override
                public void onSummaryToken(String token) {
                    summaryBuffer.append(token); // 累积摘要
                    listener.onSummaryToken(token);
                }

                @Override
                public void onChartConfig(ChartConfigDTO chartConfig) {
                    // 推送给前端
                    listener.onChartConfig(chartConfig);
                    // 保存到数据库
                    if (chartConfig != null) {
                        saveChartConfig(savedRecordId.get(), chartConfig);
                    }
                }

                @Override
                public void onComplete() {
                    // 完成时更新缓存中的摘要，同时更新数据库中的 summary
                    String summary = summaryBuffer.toString();
                    chatRecordService.cacheResult(savedRecordId.get(), data, summary);
                    listener.onComplete();
                }

                @Override
                public void onError(String message) {
                    // 连接异常断开时，尝试保存已累积的摘要
                    if (!summaryBuffer.isEmpty()) {
                        try {
                            String partialSummary = summaryBuffer.toString();
                            chatRecordService.cacheResult(savedRecordId.get(), data, partialSummary);
                            log.info("连接异常断开，已保存部分摘要，recordId: {}", savedRecordId.get());
                        } catch (Exception e) {
                            log.warn("异常断开时摘要保存失败：{}", e.getMessage());
                        }
                    }
                    listener.onError(message);
                }
            };

            // 调用流式生成
            nlFeedbackGenerator.generateStream(question, finalSql, data, internalListener);

        } catch (Exception e) {
            log.error("AI 业务流执行失败，sessionId: {}", finalSessionId, e);
            listener.onError(e.getMessage());
            // 会话已建立但流程中断（如 SQL 安全校验拦截），仍保存一条错误记录
            if (finalSessionId != null) {
                saveBlockedRecord(finalSessionId, question, e.getMessage());
            }
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 保存被拦截/异常中断的对话记录，sql 为 null，summary 存错误原因
     */
    private void saveBlockedRecord(Long sessionId, String question, String errorMessage) {
        try {
            chatRecordService.save(sessionId, question, null, 0, errorMessage, false);
        } catch (Exception e) {
            log.warn("拦截记录保存失败：{}", e.getMessage());
        }
    }

    /**
     * 保存图表配置
     */
    private void saveChartConfig(Long recordId, ChartConfigDTO chartConfig) {
        if (chartConfig == null) {
            return;
        }

        ChartConfig config = ChartConfig.builder()
                .recordId(recordId)
                .type(chartConfig.getType())
                .xAxis(chartConfig.getXAxis())
                .yAxis(chartConfig.getYAxis())
                .title(chartConfig.getTitle())
                .isUserModified(false)
                .build();
        chartConfigService.saveOrUpdate(config);
        log.info("图表配置已保存，recordId: {}, type: {}", recordId, chartConfig.getType());
    }

    private Long resolveSessionId(Long userId, Long sessionId, Long dataSourceId, String question) {
        if (sessionId == null) {
            if (dataSourceId == null) {
                throw new IllegalArgumentException("新会话必须传入 dataSourceId");
            }
            return chatSessionService.createSession(userId, dataSourceId, question);
        }
        return sessionId;
    }

    /**
     * 执行 SQL，失败时触发一次 Self-correction，返回最终结果和元信息。
     * 若纠错后 AI 返回 EXPLAIN（无法修正），直接推送解释文本并结束流程，返回 null。
     */
    private ExecuteResult executeWithRetry(Long userId, Long sessionId, Long dsId,
                                           String sql, ChatStreamListener listener) {
        try {
            listener.onStage("SQL 执行中...");
            List<Map<String, Object>> data = sqlExecutorService.execute(dsId, sql);
            return new ExecuteResult(data, sql, false);

        } catch (Exception firstError) {
            log.warn("SQL 执行失败，启动修正：{}", firstError.getMessage());
            listener.onStage("执行出错，AI 正在自动修正...");

            AiResponse correction = sqlGeneratorService.correct(
                    userId, sessionId, firstError.getMessage(), sql);

            if (correction.isExplain()) {
                String explainText = correction.cleanSql().replace("[EXPLAIN]", "").trim();
                listener.onStage("AI 提示");
                listener.onSummaryToken(explainText);
                listener.onComplete();
                return null;
            }

            listener.onSql(correction.cleanSql(), true);
            listener.onStage("修正后 SQL 执行中...");
            List<Map<String, Object>> data = sqlExecutorService.execute(dsId, correction.cleanSql());
            return new ExecuteResult(data, correction.cleanSql(), true);
        }
    }

    /** 封装执行结果，携带最终 SQL 和是否纠错标记 */
    private record ExecuteResult(
            List<Map<String, Object>> data,
            String finalSql,
            boolean corrected
    ) {}
}