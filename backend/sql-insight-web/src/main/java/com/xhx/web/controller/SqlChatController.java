package com.xhx.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.common.context.UserContext;
import com.xhx.common.result.Result;
import com.xhx.core.model.dto.SqlChatRequest;
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

    private final SqlExecutorService  sqlExecutorService;
    private final ChatSessionService  chatSessionService;
    private final SqlChatApiService   sqlChatApiService;
    private final ChatRecordService   chatRecordService;

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
}