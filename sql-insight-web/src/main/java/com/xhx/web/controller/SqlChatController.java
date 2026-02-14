package com.xhx.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.common.context.UserContext;
import com.xhx.common.result.Result;
import com.xhx.core.model.dto.SqlChatResponse;
import com.xhx.core.service.sql.ChatSessionService;
import com.xhx.core.service.sql.SqlExecutorService;
import com.xhx.core.service.sql.SqlGeneratorService;
import com.xhx.dal.entity.ChatSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.xhx.common.constant.SystemPermissionConstants.USER;

/**
 * AI对话控制器
 * @author master
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + USER + "')")
public class SqlChatController {

    private final SqlGeneratorService sqlGeneratorService;
    private final SqlExecutorService sqlExecutorService;
    private final ChatSessionService chatSessionService;

    /**
     * AI对话
     */
    @PostMapping("/chat")
    public Result<SqlChatResponse> chat(
            @RequestParam(required = false) Long sessionId,
            @RequestParam Long dataSourceId,
            @RequestParam String question) {

        Long userId = UserContext.getUserId();

        // 创建或获取会话
        if (sessionId == null) {
            sessionId = chatSessionService.createSession(userId, dataSourceId, question);
        }

        // 生成并校验 SQL
        String generatedSql = sqlGeneratorService.generate(userId, sessionId, question);

        // 执行 SQL
        List<Map<String, Object>> data = sqlExecutorService.execute(dataSourceId, generatedSql);

        return Result.success(new SqlChatResponse(sessionId, generatedSql, data));
    }

    /**
     * 获取用户的会话列表（分页）
     */
    @GetMapping("/sessions")
    public Result<Page<ChatSession>> getSessions(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size) {

        Long userId = UserContext.getUserId();
        Page<ChatSession> sessions = chatSessionService.getUserSessions(userId, current, size);
        return Result.success(sessions);
    }

    /**
     * 获取单个会话详情
     */
    @GetMapping("/sessions/{sessionId}")
    public Result<ChatSession> getSessionDetail(@PathVariable Long sessionId) {
        Long userId = UserContext.getUserId();
        ChatSession session = chatSessionService.getSessionDetail(userId, sessionId);
        return Result.success(session);
    }

    /**
     * 重命名会话
     */
    @PutMapping("/sessions/{sessionId}/title")
    public Result<Void> renameSession(
            @PathVariable Long sessionId,
            @RequestParam String title) {

        Long userId = UserContext.getUserId();
        chatSessionService.renameSession(userId, sessionId, title);
        return Result.success("会话标题已更新", null);
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        Long userId = UserContext.getUserId();
        chatSessionService.deleteSession(userId, sessionId);
        return Result.success("会话已删除", null);
    }

    /**
     * 批量删除会话
     */
    @DeleteMapping("/sessions/batch")
    public Result<Void> batchDeleteSessions(@RequestBody List<Long> sessionIds) {
        Long userId = UserContext.getUserId();
        chatSessionService.batchDeleteSessions(userId, sessionIds);
        return Result.success("批量删除成功", null);
    }
}