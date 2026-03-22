package com.xhx.core.service.sql.Impl;

import com.xhx.ai.listener.ChatStreamListener;
import com.xhx.ai.model.AiResponse;
import com.xhx.ai.service.NlFeedbackGenerator;
import com.xhx.common.util.CommonUtil;
import com.xhx.core.listener.FeedbackCaptureListener;
import com.xhx.core.service.chart.ChartConfigService;
import com.xhx.core.service.sql.*;
import com.xhx.dal.entity.ChatSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlChatTransactionalService {

    private final SqlGeneratorService       sqlGeneratorService;
    private final SqlExecutorService        sqlExecutorService;
    private final ChatSessionService        chatSessionService;
    private final NlFeedbackGenerator       nlFeedbackGenerator;
    private final ChatRecordService         chatRecordService;
    private final ChartConfigService        chartConfigService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate       transactionTemplate;

    @Transactional(rollbackFor = Exception.class)
    public void execute(Long userId, Long sessionId, Long dataSourceId,
                        String question, ChatStreamListener listener) {
        Long finalSessionId = null;
        try {
            finalSessionId = resolveSessionId(userId, sessionId, dataSourceId, question);
            ChatSession session = chatSessionService.getSessionDetail(userId, finalSessionId);
            Long dsId = session.getDataSourceId();

            listener.onStage("正在生成 SQL...");
            String sql = sqlGeneratorService.generate(userId, finalSessionId, question);

            if (CommonUtil.isExplain(sql)) {
                String warmMessage = sql.replace("[EXPLAIN]", "").trim();
                listener.onStage("AI 提示");
                listener.onSummaryToken(warmMessage);
                listener.onComplete();
                saveBlockedRecord(finalSessionId, question, warmMessage);
                return;
            }

            listener.onSql(sql, false);

            ExecuteResult result = executeWithRetry(userId, finalSessionId, dsId, sql, listener);
            if (result == null) {
                saveBlockedRecord(finalSessionId, question, "SQL 自动修正后仍无法执行");
                return;
            }
            final List<Map<String, Object>> data = result.data();
            final String finalSql = result.finalSql();
            final boolean corrected = result.corrected();

            Long recordId = chatRecordService.save(
                    finalSessionId, question, finalSql, data.size(), null, corrected);

            listener.onData(data, finalSessionId, recordId);

            ChatStreamListener internalListener = new FeedbackCaptureListener(
                    listener, new AtomicLong(recordId), data, eventPublisher, chartConfigService);

            nlFeedbackGenerator.generateStream(question, finalSql, data, internalListener);

        } catch (Exception e) {
            log.error("AI 业务流执行失败，sessionId: {}", finalSessionId, e);
            listener.onError(e.getMessage());
            if (finalSessionId != null) {
                try {
                    chatRecordService.save(finalSessionId, question, null, 0, e.getMessage(), false);
                } catch (Exception ex) {
                    log.warn("拦截记录保存失败：{}", ex.getMessage());
                }
            }
            throw e;
        }
    }

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

    private record ExecuteResult(List<Map<String, Object>> data, String finalSql, boolean corrected) {}
}