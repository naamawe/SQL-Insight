package com.xhx.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.ai.service.NlFeedbackGenerator;
import com.xhx.common.context.UserContext;
import com.xhx.common.exception.ServiceException;
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
    private final NlFeedbackGenerator nlFeedbackGenerator;

    /**
     * AI 对话
     * <p>
     * 流程：
     *   1. 生成 SQL
     *   2. 执行 SQL
     *   3. 执行失败 → Self-correction 重试一次
     *   4. 重试仍失败 → 返回错误信息给用户
     *   5. 执行成功 → 异步生成自然语言摘要（失败时静默降级）
     * <p>
     * 安全说明：
     *   新会话时 dataSourceId 由前端传入用于创建 session
     *   续会话时 dataSourceId 从 session 里取，不信任前端传入的值，防止越权
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

        // 从 session 取 dataSourceId，防止前端篡改
        ChatSession session = chatSessionService.getSessionDetail(userId, sessionId);
        Long dsId = session.getDataSourceId();

        // 第一次生成 SQL
        String sql = sqlGeneratorService.generate(userId, sessionId, question);

        // 第一次执行
        try {
            List<Map<String, Object>> data = sqlExecutorService.execute(dsId, sql);
            String summary = nlFeedbackGenerator.generate(question, sql, data);
            return Result.success(buildResponse(sessionId, sql, data, summary));

        } catch (Exception firstError) {
            log.warn("SQL 首次执行失败，触发 Self-correction，sessionId: {}, error: {}",
                    sessionId, firstError.getMessage());

            // Self-correction：把错误信息传给 generator 重新生成
            try {
                String correctedSql = sqlGeneratorService.correct(
                        userId, sessionId, firstError.getMessage(), sql);

                List<Map<String, Object>> data = sqlExecutorService.execute(dsId, correctedSql);
                String summary = nlFeedbackGenerator.generate(question, correctedSql, data);
                log.info("Self-correction 成功，sessionId: {}", sessionId);
                return Result.success(buildResponse(sessionId, correctedSql, data, summary));

            } catch (Exception secondError) {
                // 重试仍失败，返回友好错误信息
                log.error("Self-correction 后仍失败，sessionId: {}, error: {}",
                        sessionId, secondError.getMessage());
                throw new ServiceException(500,
                        "SQL 执行失败，请尝试换一种问法：" + secondError.getMessage());
            }
        }
    }

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

    // ==================== 私有工具方法 ====================

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