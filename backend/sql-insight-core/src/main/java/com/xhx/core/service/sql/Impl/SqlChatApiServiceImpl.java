package com.xhx.core.service.sql.Impl;

import com.xhx.ai.listener.ChatStreamListener;
import com.xhx.ai.model.AiResponse;
import com.xhx.ai.model.FeedbackResponse;
import com.xhx.ai.service.NlFeedbackGenerator;
import com.xhx.common.util.CommonUtil;
import com.xhx.core.listener.FeedbackCaptureListener;
import com.xhx.core.model.vo.ChatRecordVO;
import com.xhx.core.service.chart.ChartConfigService;
import com.xhx.core.service.sql.*;
import com.xhx.dal.entity.ChartConfig;
import com.xhx.dal.entity.ChatSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
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

    private final SqlGeneratorService      sqlGeneratorService;
    private final SqlExecutorService       sqlExecutorService;
    private final ChatSessionService       chatSessionService;
    private final NlFeedbackGenerator      nlFeedbackGenerator;
    private final ChatRecordService        chatRecordService;
    private final ChartConfigService       chartConfigService;
    private final SqlSecurityService       sqlSecurityService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate      transactionTemplate;

    @Async("aiExecutor")
    @Override
    public void executeChatStream(Long userId, Long sessionId, Long dataSourceId,
                                  String question, ChatStreamListener listener) {
        // 异步方法只负责开启线程，事务控制在内部同步方法中
        executeChatStreamInternal(userId, sessionId, dataSourceId, question, listener);
    }

    /**
     * 核心业务逻辑（带事务控制）
     * <p>
     * 事务范围：从保存对话记录 → 缓存查询结果 → 保存图表配置
     * 任何一步失败都会回滚，确保数据一致性
     */
    @Transactional(rollbackFor = Exception.class)
    private void executeChatStreamInternal(Long userId, Long sessionId, Long dataSourceId,
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

            // 先保存对话记录，获取 recordId（事务起点）
            Long recordId = chatRecordService.save(
                    finalSessionId, question, finalSql, data.size(), null, corrected);

            listener.onData(data, finalSessionId, recordId);

            ChatStreamListener internalListener = new FeedbackCaptureListener(
                    listener, new AtomicLong(recordId), data, eventPublisher, chartConfigService);

            // 调用流式生成（内部会触发 onSummaryToken 累积和 onChartConfig 保存）
            nlFeedbackGenerator.generateStream(question, finalSql, data, internalListener);
            // generateStream 完成后，FeedbackCaptureListener.onComplete() 会被调用
            // 该方法会发布 ChatRecordCompletedEvent 事件，由监听器异步处理缓存

        } catch (Exception e) {
            log.error("AI 业务流执行失败，sessionId: {}", finalSessionId, e);
            listener.onError(e.getMessage());
            // 会话已建立但流程中断（如 SQL 安全校验拦截），仍保存一条错误记录
            if (finalSessionId != null) {
                try {
                    chatRecordService.save(finalSessionId, question, null, 0, e.getMessage(), false);
                } catch (Exception ex) {
                    log.warn("拦截记录保存失败：{}", ex.getMessage());
                }
            }
            // 重新抛出异常，触发事务回滚
            throw e;
        }
    }

    /**
     * 重新执行历史记录（带事务控制）
     * <p>
     * 事务范围：缓存查询结果 → 保存图表配置
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Map<String, Object> rerunRecord(Long recordId, Long userId) {
        ChatRecordVO record = chatRecordService.getById(recordId, userId);

        // 缓存仍有效，直接返回
        if (!record.getResultExpired() && record.getResultData() != null) {
            log.info("[历史记录重执行] 缓存有效，直接返回, userId: {}, recordId: {}", userId, recordId);
            return Map.of("data", record.getResultData(),
                    "summary", record.getSummary(),
                    "total", record.getRowTotal());
        }

        Long dataSourceId = chatSessionService.getSessionDetail(userId, record.getSessionId()).getDataSourceId();
        sqlSecurityService.validate(record.getSqlText(), userId, dataSourceId);

        List<Map<String, Object>> data = sqlExecutorService.execute(dataSourceId, record.getSqlText());
        log.info("[历史记录重执行成功] userId: {}, recordId: {}, 行数: {}", userId, recordId, data.size());

        FeedbackResponse feedback = nlFeedbackGenerator.generateWithChart(
                record.getQuestion(), record.getSqlText(), data);
        String summary = feedback != null ? feedback.getSummary() : "";

        chatRecordService.cacheResult(recordId, data, summary);

        if (feedback != null && feedback.getChart() != null) {
            chartConfigService.saveOrUpdate(ChartConfig.builder()
                    .recordId(recordId)
                    .type(feedback.getChart().getType())
                    .xAxis(feedback.getChart().getXAxis())
                    .yAxis(feedback.getChart().getYAxis())
                    .title(feedback.getChart().getTitle())
                    .isUserModified(false)
                    .build());
            log.info("[历史记录重执行] 图表配置已更新，recordId: {}", recordId);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("data", data);
        result.put("summary", summary);
        result.put("total", data.size());
        return result;
    }

    // ==================== 私有方法 ====================

    /**
     * 保存被拦截/异常中断的对话记录，sql 为 null，summary 存错误原因
     * <p>
     * 使用独立事务（REQUIRES_NEW），确保记录能被保存，不受主事务回滚影响
     */
    private void saveBlockedRecord(Long sessionId, String question, String errorMessage) {
        transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(@NotNull TransactionStatus status) {
                try {
                    chatRecordService.save(sessionId, question, null, 0, errorMessage, false);
                } catch (Exception e) {
                    log.warn("拦截记录保存失败：{}", e.getMessage());
                }
            }
        });
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