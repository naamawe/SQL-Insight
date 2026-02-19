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
 * AI 对话控制器
 * @author master
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + USER + "')")
public class SqlChatController {

    private final SqlGeneratorService sqlGeneratorService;
    private final SqlExecutorService sqlExecutorService;
    private final ChatSessionService chatSessionService;

    /**
     * AI 对话
     * <p>
     * 安全说明：
     * - 新会话时，dataSourceId 由前端传入，用于创建 session 并绑定
     * - 续会话时，dataSourceId 从 session 里取，不信任前端重复传入的值
     *   这样可防止用户篡改 dataSourceId 越权访问其他数据源
     */
    @PostMapping("/chat")
    public Result<SqlChatResponse> chat(
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) Long dataSourceId,
            @RequestParam String question) {

        Long userId = UserContext.getUserId();

        // 新会话：dataSourceId 必须传
        if (sessionId == null) {
            if (dataSourceId == null) {
                return Result.error(400, "新会话必须传入 dataSourceId");
            }
            sessionId = chatSessionService.createSession(userId, dataSourceId, question);
        }

        // 生成 SQL（内部使用 session 绑定的 dataSourceId，不使用前端传入的值）
        String generatedSql = sqlGeneratorService.generate(userId, sessionId, question);

        // 从 session 中取出 dataSourceId 执行，防止前端篡改
        ChatSession session = chatSessionService.getSessionDetail(userId, sessionId);
        List<Map<String, Object>> data = sqlExecutorService.execute(session.getDataSourceId(), generatedSql);

        return Result.success(new SqlChatResponse(sessionId, generatedSql, data));
    }

    @GetMapping("/sessions")
    public Result<Page<ChatSession>> getSessions(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(chatSessionService.getUserSessions(UserContext.getUserId(), current, size));
    }

    @GetMapping("/sessions/{sessionId}")
    public Result<ChatSession> getSessionDetail(@PathVariable Long sessionId) {
        return Result.success(chatSessionService.getSessionDetail(UserContext.getUserId(), sessionId));
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
}