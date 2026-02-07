package com.xhx.web.controller;

import com.xhx.common.result.Result;
import com.xhx.core.service.SqlGeneratorService;
import com.xhx.dal.entity.ChatSession;
import com.xhx.dal.mapper.ChatSessionMapper;
import com.xhx.web.dto.SqlChatResponse;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * @author master
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class SqlChatController {

    private final SqlGeneratorService sqlGeneratorService;
    private final ChatSessionMapper chatSessionMapper;

    /**
     * 智能对话接口
     * @param sessionId 可选。如果不传，则视为针对 dataSourceId 开启新对话
     */
    @PostMapping("/chat")
    public Result<SqlChatResponse> chat(@RequestParam(required = false) Long sessionId,
                                     @RequestParam Long dataSourceId,
                                     @RequestParam String question) {
        Long userId = 1L;

        if (sessionId == null) {
            ChatSession session = new ChatSession();
            session.setUserId(userId);
            session.setDataSourceId(dataSourceId);
            session.setTitle(question.length() > 20 ? question.substring(0, 20) + "..." : question);
            session.setCreateTime(LocalDateTime.now());
            
            chatSessionMapper.insert(session);
            sessionId = session.getId();
            log.info("为用户 {} 创建了新会话: {}", userId, sessionId);
        }

        String generatedSql = sqlGeneratorService.generate(userId, sessionId, question);

        SqlChatResponse response = new SqlChatResponse(sessionId, generatedSql);

        return Result.success(response);
    }
}