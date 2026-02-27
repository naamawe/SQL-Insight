package com.xhx.core.service.sql.Impl;

import com.xhx.ai.listener.ChatStreamListener;
import com.xhx.ai.service.NlFeedbackGenerator;
import com.xhx.common.util.CommonUtil;
import com.xhx.core.service.sql.*;
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
    private final SqlExecutorService  sqlExecutorService;
    private final ChatSessionService  chatSessionService;
    private final NlFeedbackGenerator nlFeedbackGenerator;
    private final ChatRecordService   chatRecordService;

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
                return;
            }

            listener.onSql(sql, false);

            // 执行 SQL，内部处理自动纠错，返回最终结果
            ExecuteResult result = executeWithRetry(userId, finalSessionId, dsId, sql, listener);
            final List<Map<String, Object>> data    = result.data();
            final String                   finalSql = result.finalSql();
            final boolean                  corrected = result.corrected();
            final Long                     capturedSessionId = finalSessionId;

            listener.onData(data, finalSessionId);

            // 收集流式摘要 token，完成后统一落库
            final StringBuilder summaryBuffer = new StringBuilder();

            nlFeedbackGenerator.generateStream(question, finalSql, data, new ChatStreamListener() {

                @Override
                public void onStage(String message) {
                    listener.onStage(message);
                }

                @Override
                public void onSql(String s, boolean c) {
                    listener.onSql(s, c);
                }

                @Override
                public void onData(List<Map<String, Object>> d, Long sid) {
                    listener.onData(d, sid);
                }

                @Override
                public void onSummaryToken(String token) {
                    summaryBuffer.append(token); // 攒起来
                    listener.onSummaryToken(token);
                }

                @Override
                public void onComplete() {
                    // 摘要流结束，所有数据已就绪，统一落库 + 缓存结果
                    saveRecord(capturedSessionId, question, finalSql,
                            data, summaryBuffer.toString(), corrected);
                    listener.onComplete();
                }

                @Override
                public void onError(String message) {
                    // 摘要生成失败，summary 为 null，其他数据仍然保存
                    log.warn("流式摘要失败，仍保存对话记录: {}", message);
                    saveRecord(capturedSessionId, question, finalSql,
                            data, null, corrected);
                    listener.onComplete(); // 静默降级，不暴露摘要错误给用户
                }
            });

        } catch (Exception e) {
            log.error("AI 业务流执行失败, sessionId: {}", finalSessionId, e);
            listener.onError(e.getMessage());
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 保存对话记录 + 缓存结果，失败不影响主流程
     */
    private void saveRecord(Long sessionId, String question, String sql,
                            List<Map<String, Object>> data, String summary, boolean corrected) {
        try {
            Long recordId = chatRecordService.save(
                    sessionId, question, sql, data.size(), summary, corrected);
            chatRecordService.cacheResult(recordId, data);
        } catch (Exception e) {
            log.warn("对话记录保存失败，不影响用户体验: {}", e.getMessage());
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

    /**
     * 执行 SQL，失败时触发一次 Self-correction，返回最终结果和元信息
     */
    private ExecuteResult executeWithRetry(Long userId, Long sessionId, Long dsId,
                                           String sql, ChatStreamListener listener) {
        try {
            listener.onStage("SQL 执行中...");
            List<Map<String, Object>> data = sqlExecutorService.execute(dsId, sql);
            return new ExecuteResult(data, sql, false);

        } catch (Exception firstError) {
            log.warn("SQL 执行失败，启动修正: {}", firstError.getMessage());
            listener.onStage("执行出错，AI 正在自动修正...");

            String correctedSql = sqlGeneratorService.correct(
                    userId, sessionId, firstError.getMessage(), sql);
            listener.onSql(correctedSql, true);

            listener.onStage("修正后 SQL 执行中...");
            List<Map<String, Object>> data = sqlExecutorService.execute(dsId, correctedSql);
            return new ExecuteResult(data, correctedSql, true);
        }
    }

    /** 封装执行结果，携带最终 SQL 和是否纠错标记 */
    private record ExecuteResult(
            List<Map<String, Object>> data,
            String finalSql,
            boolean corrected
    ) {}
}