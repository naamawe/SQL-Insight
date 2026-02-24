package com.xhx.ai.service;

import com.xhx.ai.model.AiResponse;
import com.xhx.common.util.CommonUtil;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 执行器（底层 AI 调用组件）
 * <p>
 * 职责：
 *   - 构造消息列表（system + history + current）
 *   - 调用 ChatLanguageModel 完成一次推理
 *   - 将本轮对话持久化到 ChatMemoryStore
 * <p>
 * 此类属于 AI 能力层，不引入任何业务实体（DataSource、QueryPolicy 等）
 *
 * @author master
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlExecutor {

    private final ChatLanguageModel chatLanguageModel;
    private final ChatMemoryStore chatMemoryStore;

    /**
     * 正常执行一轮 AI 对话，持久化 user + ai 消息
     *
     * @param sessionId    会话ID
     * @param systemPrompt 完整系统提示词（由 PromptBuilder 拼装）
     * @param question     用户原始问题
     * @return AI 返回的原始文本
     */
    public AiResponse execute(Long sessionId, String systemPrompt, String question) {
        List<ChatMessage> history = chatMemoryStore.getMessages(sessionId);
        List<ChatMessage> messages = buildMessages(systemPrompt, history, UserMessage.from(question));

        AiMessage aiReply = callAi(messages);
        String raw = aiReply.text();

        String cleaned = CommonUtil.cleanSql(raw);
        boolean isExplain = cleaned.startsWith("[EXPLAIN]");

        String contentToSave = isExplain ? cleaned.replace("[EXPLAIN]", "").trim() : cleaned;

        chatMemoryStore.updateMessages(sessionId, List.of(UserMessage.from(question)));
        chatMemoryStore.updateMessages(sessionId, List.of(AiMessage.from(contentToSave)));

        log.info("AI 响应完成，原始长度: {}, 记忆长度: {}", raw.length(), contentToSave.length());

        return new AiResponse(raw, cleaned, isExplain);
    }

    /**
     * 纠错对话：将上一次的执行错误告知 AI，让其修正 SQL
     * <p>
     * 纠错消息不存入 ChatMemory，保持历史干净，
     * 避免后续对话的上下文里出现大量错误信息干扰 AI。
     *
     * @param sessionId    会话ID
     * @param systemPrompt 完整系统提示词
     * @param errorMessage 上一次 SQL 执行时的报错信息
     * @param wrongSql     上一次生成的错误 SQL
     * @return AI 修正后的 SQL 文本
     */
    public AiResponse executeWithCorrection(Long sessionId, String systemPrompt,
                                            String errorMessage, String wrongSql) {
        log.warn("SQL 执行失败，触发 Self-correction，sessionId: {}, error: {}",
                sessionId, errorMessage);

        List<ChatMessage> history = chatMemoryStore.getMessages(sessionId);

        // 构造纠错请求
        String correctionPrompt = """
            你上一次生成的 SQL 执行报错了，请修正。
            错误的 SQL：%s
            报错信息：%s""".formatted(wrongSql, errorMessage);

        List<ChatMessage> messages = buildMessages(systemPrompt, history, UserMessage.from(correctionPrompt));

        // 调用 AI
        AiMessage aiReply = callAi(messages);
        String raw = aiReply.text();
        String cleaned = CommonUtil.cleanSql(raw);
        boolean isExplain = cleaned.startsWith("[EXPLAIN]");

        String contentToSave = isExplain ? cleaned.replace("[EXPLAIN]", "").trim() : cleaned;

        chatMemoryStore.updateMessages(sessionId, List.of(UserMessage.from("修正之前的查询错误")));
        chatMemoryStore.updateMessages(sessionId, List.of(AiMessage.from(contentToSave)));

        log.info("Self-correction 完成并已存入记忆，sessionId: {}", sessionId);

        return new AiResponse(raw, cleaned, isExplain);
    }

    // ==================== 私有工具方法 ====================

    private List<ChatMessage> buildMessages(String systemPrompt,
                                            List<ChatMessage> history,
                                            UserMessage currentMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        messages.addAll(history);
        messages.add(currentMessage);
        return messages;
    }

    private AiMessage callAi(List<ChatMessage> messages) {
        Response<AiMessage> response = chatLanguageModel.generate(messages);
        return response.content();
    }


}