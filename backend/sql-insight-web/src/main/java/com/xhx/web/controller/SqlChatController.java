package com.xhx.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.ai.service.NlFeedbackGenerator;
import com.xhx.common.context.UserContext;
import com.xhx.common.exception.ServiceException;
import com.xhx.common.result.Result;
import com.xhx.core.model.dto.SqlChatRequest;
import com.xhx.core.model.dto.SqlChatResponse;
import com.xhx.ai.listener.ChatStreamListener;
import com.xhx.core.model.vo.ChatRecordVO;
import com.xhx.core.service.sql.*;
import com.xhx.dal.entity.ChatSession;
import com.xhx.web.adapter.SseChatAdapter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

import static com.xhx.common.constant.SystemPermissionConstants.USER;

/**
 * @author master
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + USER + "')")
public class SqlChatController {

    private final SqlGeneratorService sqlGeneratorService;
    private final SqlExecutorService  sqlExecutorService;
    private final ChatSessionService  chatSessionService;
    private final NlFeedbackGenerator nlFeedbackGenerator;
    private final SqlChatApiService   sqlChatApiService;
    private final ChatRecordService   chatRecordService;

    /**
     * 阻塞模式 AI 对话
     */
    @PostMapping("/chat")
    public Result<SqlChatResponse> chat(@Valid @RequestBody SqlChatRequest req) {
        Long userId    = UserContext.getUserId();
        Long sessionId = req.getSessionId();
        String question = req.getQuestion();

        if (sessionId == null) {
            if (req.getDataSourceId() == null) {
                return Result.error(400, "新会话必须传入 dataSourceId");
            }
            sessionId = chatSessionService.createSession(userId, req.getDataSourceId(), question);
        }

        ChatSession session = chatSessionService.getSessionDetail(userId, sessionId);
        Long dsId = session.getDataSourceId();
        String sql = sqlGeneratorService.generate(userId, sessionId, question);

        try {
            List<Map<String, Object>> data = sqlExecutorService.execute(dsId, sql);
            String summary = nlFeedbackGenerator.generate(question, sql, data);
            return Result.success(buildResponse(sessionId, sql, data, summary));

        } catch (Exception firstError) {
            log.warn("SQL 首次执行失败，触发 Self-correction，sessionId: {}", sessionId);
            try {
                String correctedSql = sqlGeneratorService.correct(
                        userId, sessionId, firstError.getMessage(), sql);
                List<Map<String, Object>> data = sqlExecutorService.execute(dsId, correctedSql);
                String summary = nlFeedbackGenerator.generate(question, correctedSql, data);
                return Result.success(buildResponse(sessionId, correctedSql, data, summary));
            } catch (Exception secondError) {
                log.error("Self-correction 后仍失败，sessionId: {}", sessionId);
                throw new ServiceException(500,
                        "SQL 执行失败，请尝试换一种问法：" + secondError.getMessage());
            }
        }
    }

    /**
     * SSE 流式 AI 对话
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody SqlChatRequest req) {
        SseEmitter emitter = new SseEmitter(300_000L);
        ChatStreamListener adapter = new SseChatAdapter(emitter);

        sqlChatApiService.executeChatStream(
                UserContext.getUserId(),
                req.getSessionId(),
                req.getDataSourceId(),
                req.getQuestion(),
                adapter
        );
        return emitter;
    }

    // ==================== 会话管理 ====================

    @GetMapping("/sessions")
    public Result<Page<ChatSession>> getSessions(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(chatSessionService.getUserSessions(
                UserContext.getUserId(), current, size));
    }

    @GetMapping("/sessions/{sessionId}")
    public Result<ChatSession> getSessionDetail(@PathVariable Long sessionId) {
        return Result.success(chatSessionService.getSessionDetail(
                UserContext.getUserId(), sessionId));
    }

    @PutMapping("/sessions/{sessionId}/title")
    public Result<Void> renameSession(
            @PathVariable Long sessionId,
            @RequestParam String title) {
        chatSessionService.renameSession(UserContext.getUserId(), sessionId, title);
        return Result.success("会话标题已更新", null);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        chatSessionService.deleteSession(UserContext.getUserId(), sessionId);
        return Result.success("会话已删除", null);
    }

    @DeleteMapping("/sessions/batch")
    public Result<Void> batchDeleteSessions(@RequestBody List<Long> sessionIds) {
        chatSessionService.batchDeleteSessions(UserContext.getUserId(), sessionIds);
        return Result.success("批量删除成功", null);
    }

    // ==================== 历史记录 ====================

    /**
     * 获取某个会话下的完整对话记录
     */
    @GetMapping("/sessions/{sessionId}/records")
    public Result<List<ChatRecordVO>> getSessionRecords(@PathVariable Long sessionId) {
        // getSessionDetail 内部已做归属权校验
        chatSessionService.getSessionDetail(UserContext.getUserId(), sessionId);
        return Result.success(chatRecordService.getBySessionId(sessionId));
    }

    /**
     * 重新执行历史 SQL（缓存过期后使用）
     */
    @PostMapping("/records/{recordId}/rerun")
    public Result<List<Map<String, Object>>> rerunRecord(@PathVariable Long recordId) {
        Long userId = UserContext.getUserId();

        // getById 内部已做归属权校验
        ChatRecordVO record = chatRecordService.getById(recordId, userId);

        List<Map<String, Object>> data = sqlExecutorService.execute(
                chatSessionService.getSessionDetail(userId, record.getSessionId()).getDataSourceId(),
                record.getSqlText()
        );

        chatRecordService.cacheResult(recordId, data);
        return Result.success(data);
    }

    // ==================== 私有方法 ====================

    private SqlChatResponse buildResponse(Long sessionId, String sql,
                                          List<Map<String, Object>> data, String summary) {
        return SqlChatResponse.builder()
                .sessionId(sessionId)
                .sql(sql)
                .data(data)
                .total(data.size())
                .summary(summary)
                .build();
    }
}