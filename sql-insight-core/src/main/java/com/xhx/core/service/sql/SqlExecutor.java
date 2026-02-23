package com.xhx.core.service.sql;

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
 * @author master
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlExecutor {

    private final ChatLanguageModel chatLanguageModel;
    private final ChatMemoryStore chatMemoryStore;

    /**
     * 正常执行一轮 AI 对话
     *
     * @param sessionId    会话ID
     * @param systemPrompt 完整的系统提示词（由 PromptBuilder 拼装）
     * @param question     用户原始问题
     * @return AI 返回的原始文本
     */
    public String execute(Long sessionId, String systemPrompt, String question) {
        List<ChatMessage> history = chatMemoryStore.getMessages(sessionId);

        List<ChatMessage> messages = buildMessages(systemPrompt, history,
                UserMessage.from(question));

        log.debug("正常对话，history 条数: {}, 总消息数（含 system）: {}",
                history.size(), messages.size());

        AiMessage aiReply = callAi(messages);

        // 持久化本轮 user + ai
        persistTurn(sessionId, history, UserMessage.from(question), aiReply);

        log.info("AI 响应完成，sessionId: {}", sessionId);
        return aiReply.text();
    }

    /**
     * 纠错对话：将上一次的执行错误告知 AI，让其修正 SQL
     * <p>
     * 注意：
     *   纠错消息不存入 ChatMemory，因为它只是一次临时的修正动作。
     *   如果存入，后续对话的历史里会出现大量错误信息，干扰 AI 理解上下文。
     *   纠错成功后，最终正确的 SQL 也不再额外存储（上一轮的 user+ai 已经存了）。
     *
     * @param sessionId    会话ID
     * @param systemPrompt 完整的系统提示词
     * @param errorMessage 上一次 SQL 执行时的报错信息
     * @param wrongSql     上一次生成的错误 SQL
     * @return AI 修正后的 SQL 文本
     */
    public String executeWithCorrection(Long sessionId, String systemPrompt,
                                        String errorMessage, String wrongSql) {
        log.warn("SQL 执行失败，触发 Self-correction，sessionId: {}, error: {}",
                sessionId, errorMessage);

        // 加载历史消息（包含上一轮已存的 user 问题和错误 SQL）
        List<ChatMessage> history = chatMemoryStore.getMessages(sessionId);

        // 构造纠错提示，告知 AI 错在哪里，让它修正
        String correctionPrompt = String.format(
                """
                        你上一次生成的 SQL 执行报错了，请修正后只返回正确的 SQL，不要包含任何解释。

                        错误的 SQL：
                        %s

                        报错信息：
                        %s""",
                wrongSql, errorMessage
        );

        List<ChatMessage> messages = buildMessages(systemPrompt, history,
                UserMessage.from(correctionPrompt));

        log.debug("纠错对话，history 条数: {}, 总消息数（含 system）: {}",
                history.size(), messages.size());

        AiMessage aiReply = callAi(messages);

        // 纠错消息不存入 ChatMemory，保持历史干净
        log.info("Self-correction 完成，sessionId: {}", sessionId);
        return aiReply.text();
    }

    // ==================== 私有工具方法 ====================

    /**
     * 构造完整消息列表：[SystemMessage] + [历史] + [本轮消息]
     */
    private List<ChatMessage> buildMessages(String systemPrompt,
                                            List<ChatMessage> history,
                                            UserMessage currentMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        messages.addAll(history);
        messages.add(currentMessage);
        return messages;
    }

    /**
     * 调用 AI
     */
    private AiMessage callAi(List<ChatMessage> messages) {
        Response<AiMessage> response = chatLanguageModel.generate(messages);
        return response.content();
    }

    /**
     * 持久化一轮对话（user + ai 各存一次）
     * ChatMemoryStoreImpl.updateMessages 只取列表最后一条存入 DB，所以分两次调用
     */
    private void persistTurn(Long sessionId, List<ChatMessage> history,
                             UserMessage userMessage, AiMessage aiMessage) {
        List<ChatMessage> userTurn = new ArrayList<>(history);
        userTurn.add(userMessage);
        chatMemoryStore.updateMessages(sessionId, userTurn);

        List<ChatMessage> aiTurn = new ArrayList<>(userTurn);
        aiTurn.add(aiMessage);
        chatMemoryStore.updateMessages(sessionId, aiTurn);
    }
}