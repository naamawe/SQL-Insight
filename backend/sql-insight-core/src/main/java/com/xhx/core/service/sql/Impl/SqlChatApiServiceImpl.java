package com.xhx.core.service.sql.Impl;

import com.xhx.ai.service.NlFeedbackGenerator;
import com.xhx.ai.listener.ChatStreamListener;
import com.xhx.common.util.CommonUtil;
import com.xhx.core.service.sql.ChatSessionService;
import com.xhx.core.service.sql.SqlChatApiService;
import com.xhx.core.service.sql.SqlExecutorService;
import com.xhx.core.service.sql.SqlGeneratorService;
import com.xhx.dal.entity.ChatSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlChatApiServiceImpl implements SqlChatApiService {

    private final SqlGeneratorService sqlGeneratorService;
    private final SqlExecutorService sqlExecutorService;
    private final ChatSessionService chatSessionService;
    private final NlFeedbackGenerator nlFeedbackGenerator;

    @Async("aiExecutor")
    @Override
    public void executeChatStream(Long userId, Long sessionId, Long dataSourceId, String question, ChatStreamListener listener) {
        Long finalSessionId = null;
        try {
            finalSessionId = resolveSessionId(userId, sessionId, dataSourceId, question);
            ChatSession session = chatSessionService.getSessionDetail(userId, finalSessionId);
            Long dsId = session.getDataSourceId();

            listener.onStage("正在生成 SQL...");
            String sql = sqlGeneratorService.generate(userId, finalSessionId, question);

            if (CommonUtil.isExplain(sql)) {
                log.info("检测到非 SQL 解释内容，跳过执行环节直接返回");

                // 剥离标签
                String warmMessage = sql.replace("[EXPLAIN]", "").trim();

                listener.onStage("AI 提示");
                listener.onSummaryToken(warmMessage);

                listener.onComplete();
                return;
            }
            listener.onSql(sql, false);
            List<Map<String, Object>> data = executeWithRetry(userId, finalSessionId, dsId, sql, listener);
            listener.onData(data, finalSessionId);

            nlFeedbackGenerator.generateStream(question, sql, data, listener);
        } catch (Exception e) {
            log.error("AI 业务流执行失败, sessionId: {}", finalSessionId, e);
            listener.onError(e.getMessage());
        }
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

    private List<Map<String, Object>> executeWithRetry(Long userId, Long sessionId, Long dsId, String sql, ChatStreamListener listener) {
        try {
            listener.onStage("SQL 执行中...");
            return sqlExecutorService.execute(dsId, sql);
        } catch (Exception firstError) {
            log.warn("SQL 执行失败，启动修正: {}", firstError.getMessage());
            listener.onStage("执行出错，AI 正在自动修正...");

            String correctedSql = sqlGeneratorService.correct(userId, sessionId, firstError.getMessage(), sql);
            listener.onSql(correctedSql, true);

            listener.onStage("修正后 SQL 执行中...");
            return sqlExecutorService.execute(dsId, correctedSql);
        }
    }
}