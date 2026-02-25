package com.xhx.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.ai.service.NlFeedbackGenerator;
import com.xhx.common.context.UserContext;
import com.xhx.common.exception.ServiceException;
import com.xhx.common.result.Result;
import com.xhx.core.model.dto.SqlChatRequest;
import com.xhx.core.model.dto.SqlChatResponse;
import com.xhx.ai.listener.ChatStreamListener;
import com.xhx.core.service.sql.ChatSessionService;
import com.xhx.core.service.sql.SqlChatApiService;
import com.xhx.core.service.sql.SqlExecutorService;
import com.xhx.core.service.sql.SqlGeneratorService;
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

    /**
     * 阻塞模式 AI 对话
     * 流程：生成 SQL → 执行 → Self-correction → 生成摘要 → 一次性返回
     */
    @PostMapping("/chat")
    public Result<SqlChatResponse> chat(@Valid @RequestBody SqlChatRequest req) {

        Long userId = UserContext.getUserId();
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

    /**
     * SSE 流式 AI 对话
     * <p>
     * 使用 POST + RequestBody，避免 question 内容出现在 URL 中被日志记录。
     * 前端使用 {@code fetch()} 发起请求，通过 {@code response.body.getReader()} 读取 SSE 流，
     * 而非原生 {@code EventSource}（EventSource 仅支持 GET）。
     * <p>
     * 注意：
     *   1. 使用 {@code @Async} 异步处理，不阻塞 Tomcat 线程。
     *   2. SseEmitter 超时设为 3 分钟，适配较慢的 SQL 生成场景。
     *   3. UserContext 基于 ThreadLocal，需要在异步线程启动前捕获当前用户 ID。
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