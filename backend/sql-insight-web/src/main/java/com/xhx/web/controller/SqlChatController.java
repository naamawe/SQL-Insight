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
    private final SqlSecurityService  sqlSecurityService;

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
        // 限制单次最多查询100条，防止恶意请求
        if (size > 100) {
            size = 100;
        }
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
        Long userId = UserContext.getUserId();
        
        // 参数校验
        if (title == null || title.trim().isEmpty()) {
            return Result.error(400, "会话标题不能为空");
        }
        if (title.length() > 100) {
            return Result.error(400, "会话标题不能超过100个字符");
        }
        
        log.info("[会话重命名] userId: {}, sessionId: {}, 新标题: {}", userId, sessionId, title);
        
        chatSessionService.renameSession(userId, sessionId, title.trim());
        return Result.success("会话标题已更新", null);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        chatSessionService.deleteSession(UserContext.getUserId(), sessionId);
        return Result.success("会话已删除", null);
    }

    @DeleteMapping("/sessions/batch")
    public Result<Void> batchDeleteSessions(@RequestBody List<Long> sessionIds) {
        Long userId = UserContext.getUserId();
        
        // 参数校验
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Result.error(400, "删除列表不能为空");
        }
        if (sessionIds.size() > 100) {
            return Result.error(400, "单次最多删除100个会话");
        }
        
        log.info("[批量删除会话] userId: {}, 会话数: {}", userId, sessionIds.size());
        
        chatSessionService.batchDeleteSessions(userId, sessionIds);
        return Result.success("批量删除成功", null);
    }

    // ==================== 历史记录 ====================

    /**
     * 获取某个会话下的完整对话记录
     */
    @GetMapping("/sessions/{sessionId}/records")
    public Result<List<ChatRecordVO>> getSessionRecords(@PathVariable Long sessionId) {
        Long userId = UserContext.getUserId();
        log.info("[历史记录查询] userId: {}, sessionId: {}", userId, sessionId);
        
        chatSessionService.getSessionDetail(userId, sessionId);
        List<ChatRecordVO> records = chatRecordService.getBySessionId(sessionId);
        
        log.info("[历史记录查询完成] userId: {}, sessionId: {}, 记录数: {}", 
                userId, sessionId, records.size());
        
        return Result.success(records);
    }

    /**
     * 重新执行历史 SQL（缓存过期后使用）
     */
    @PostMapping("/records/{recordId}/rerun")
    public Result<List<Map<String, Object>>> rerunRecord(@PathVariable Long recordId) {
        Long userId = UserContext.getUserId();
        
        log.info("[历史记录重执行] 开始执行, userId: {}, recordId: {}", userId, recordId);

        ChatRecordVO record = chatRecordService.getById(recordId, userId);
        
        // 如果缓存仍然有效，直接返回，避免不必要的数据库查询
        if (!record.getResultExpired() && record.getResultData() != null) {
            log.info("[历史记录重执行] 缓存仍然有效，直接返回, userId: {}, recordId: {}", userId, recordId);
            return Result.success(record.getResultData());
        }
        
        Long dataSourceId = chatSessionService.getSessionDetail(userId, record.getSessionId()).getDataSourceId();
        
        // 重新校验 SQL 安全性，防止用户权限变更后越权访问
        try {
            sqlSecurityService.validate(record.getSqlText(), userId, dataSourceId);
        } catch (Exception e) {
            log.warn("[历史记录重执行失败] 安全校验未通过, userId: {}, recordId: {}, sql: {}, reason: {}", 
                    userId, recordId, record.getSqlText(), e.getMessage());
            throw e;
        }

        List<Map<String, Object>> data = sqlExecutorService.execute(dataSourceId, record.getSqlText());
        
        log.info("[历史记录重执行成功] userId: {}, recordId: {}, 原始行数: {}, 当前行数: {}", 
                userId, recordId, record.getRowTotal(), data.size());

        chatRecordService.cacheResult(recordId, data);
        return Result.success(data);
    }
}