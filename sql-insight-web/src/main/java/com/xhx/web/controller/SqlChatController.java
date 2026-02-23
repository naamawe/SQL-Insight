package com.xhx.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.ai.service.NlFeedbackGenerator;
import com.xhx.common.context.UserContext;
import com.xhx.common.exception.ServiceException;
import com.xhx.common.result.Result;
import com.xhx.core.model.dto.SqlChatResponse;
import com.xhx.ai.listener.ChatStreamListener;
import com.xhx.core.service.sql.ChatSessionService;
import com.xhx.core.service.sql.SqlChatApiService;
import com.xhx.core.service.sql.SqlExecutorService;
import com.xhx.core.service.sql.SqlGeneratorService;
import com.xhx.dal.entity.ChatSession;
import com.xhx.web.adapter.SseChatAdapter;
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
 * AI 对话控制器
 *
 * <p>提供两个入口：
 * <ul>
 *   <li>{@code POST /api/ai/chat}：阻塞模式，一次性返回完整 JSON（兼容旧版前端）</li>
 *   <li>{@code GET  /api/ai/chat/stream}：SSE 流式模式，分阶段推送事件</li>
 * </ul>
 *
 * <p>SSE 事件流设计：
 * <pre>
 *   event: stage    data: {"message":"正在生成 SQL..."}      ← 阶段进度提示
 *   event: sql      data: {"sql":"SELECT ..."}               ← 完整 SQL（可能推两次，第二次含 corrected:true）
 *   event: stage    data: {"message":"SQL 执行中..."}
 *   event: data     data: {"rows":[...],"total":10,"sessionId":1}   ← 完整结果集
 *   event: summary  data: {"token":"共"}                     ← 摘要逐 token 多次推送
 *   event: summary  data: {"token":"查询到"}
 *   ...
 *   event: done     data: {}                                 ← 正常结束
 *   event: error    data: {"message":"..."}                  ← 出错时替代 done
 * </pre>
 *
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
    private final SqlChatApiService sqlChatApiService;

    // ==================== 阻塞模式 ====================

    /**
     * 阻塞模式 AI 对话
     * 流程：生成 SQL → 执行 → Self-correction → 生成摘要 → 一次性返回
     */
    @PostMapping("/chat")
    public Result<SqlChatResponse> chat(
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) Long dataSourceId,
            @RequestParam String question) {

        Long userId = UserContext.getUserId();

        if (sessionId == null) {
            if (dataSourceId == null) {
                return Result.error(400, "新会话必须传入 dataSourceId");
            }
            sessionId = chatSessionService.createSession(userId, dataSourceId, question);
        }

        ChatSession session = chatSessionService.getSessionDetail(userId, sessionId);
        Long dsId = session.getDataSourceId();

        String sql = sqlGeneratorService.generate(userId, sessionId, question);

        try {
            List<Map<String, Object>> data = sqlExecutorService.execute(dsId, sql);
            String summary = nlFeedbackGenerator.generate(question, sql, data);
            return Result.success(buildResponse(sessionId, sql, data, summary));

        } catch (Exception firstError) {
            log.warn("SQL 首次执行失败，触发 Self-correction，sessionId: {}, error: {}",
                    sessionId, firstError.getMessage());
            try {
                String correctedSql = sqlGeneratorService.correct(
                        userId, sessionId, firstError.getMessage(), sql);
                List<Map<String, Object>> data = sqlExecutorService.execute(dsId, correctedSql);
                String summary = nlFeedbackGenerator.generate(question, correctedSql, data);
                return Result.success(buildResponse(sessionId, correctedSql, data, summary));
            } catch (Exception secondError) {
                log.error("Self-correction 后仍失败，sessionId: {}, error: {}",
                        sessionId, secondError.getMessage());
                throw new ServiceException(500,
                        "SQL 执行失败，请尝试换一种问法：" + secondError.getMessage());
            }
        }
    }

    // ==================== SSE 流式模式 ====================

    /**
     * SSE 流式 AI 对话
     * <p>
     * SQL 生成和执行仍为阻塞（必须等完整 SQL 才能执行），
     * 但用户可以通过 stage 事件实时感知进度。
     * 只有最后的摘要部分是真正的流式 token 推送。
     * <p>
     * 注意：
     *   1. Spring Security 已配置 {@code csrf.disable()}，GET 接口无需额外处理 CSRF。
     *   2. 使用 {@code CompletableFuture.runAsync()} 异步处理，不阻塞 Tomcat 线程。
     *   3. SseEmitter 超时设为 3 分钟，适配较慢的 SQL 生成场景。
     *   4. UserContext 基于 ThreadLocal，需要在异步线程启动前捕获当前用户。
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) Long dataSourceId,
            @RequestParam String question) {

        // 初始化 Emitter
        SseEmitter emitter = new SseEmitter(300_000L);

        // 创建适配器
        ChatStreamListener adapter = new SseChatAdapter(emitter);

        // 执行异步业务流
        sqlChatApiService.executeChatStream(
                UserContext.getUserId(),
                sessionId,
                dataSourceId,
                question,
                adapter
        );

        return emitter;
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