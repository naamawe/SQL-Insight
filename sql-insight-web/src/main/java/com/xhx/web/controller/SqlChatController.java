package com.xhx.web.controller;

import com.xhx.common.context.UserContext;
import com.xhx.common.result.Result;
import com.xhx.core.service.SqlExecutorService;
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
import java.util.List;
import java.util.Map;

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
    private final SqlExecutorService sqlExecutorService;

    @PostMapping("/chat")
    public Result<SqlChatResponse> chat(@RequestParam(required = false) Long sessionId,
                                        @RequestParam Long dataSourceId,
                                        @RequestParam String question) {
        Long userId = UserContext.getUserId();

        // 创建会话 (如果不存在)
        if (sessionId == null) {
            ChatSession session = new ChatSession();
            session.setUserId(userId);
            session.setDataSourceId(dataSourceId);
            session.setTitle(question.length() > 20 ? question.substring(0, 20) : question);
            chatSessionMapper.insert(session);
            sessionId = session.getId();
        }

        // 生成并校验 SQL
        String generatedSql = sqlGeneratorService.generate(userId, sessionId, question);

        // 3. 执行 SQL 拿到数据
        List<Map<String, Object>> data = sqlExecutorService.execute(dataSourceId, generatedSql);

        // 4. 返回结果
        return Result.success(new SqlChatResponse(sessionId, generatedSql, data));
    }

}