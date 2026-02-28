package com.xhx.ai.service;

import com.xhx.ai.listener.ChatStreamListener;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 自然语言反馈生成器
 * <p>
 * 职责：在 SQL 执行完成后，用自然语言向用户描述查询结果。
 * <p>
 * 设计原则：
 *   - 使用独立的一次性对话，不写入 ChatMemory，不影响对话历史
 *   - 只传摘要信息（行数 + 前 MAX_SAMPLE_ROWS 行），避免大结果集超 token 限制
 *   - 失败时静默降级，不影响主流程
 *
 * @author master
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NlFeedbackGenerator {

    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final ChatLanguageModel chatLanguageModel;

    /** 传给 AI 的最大样本行数，避免结果集过大导致 token 超限 */
    private static final int MAX_SAMPLE_ROWS = 5;

    private static final String SYSTEM_PROMPT = """
            你是一个数据库查询助手，擅长用简洁的自然语言描述 SQL 查询结果。
            请根据用户的问题、执行的 SQL 和查询结果，用一句话总结查询结论。
            要求：
            1. 直接描述结论，不要说"根据查询结果"这类废话开头
            2. 包含关键数字（如条数、金额、日期等）
            3. 不超过 50 个字
            4. 如果结果为空，说明未找到符合条件的数据
            """;

    /**
     * 阻塞式生成摘要（用于 rerun 等同步场景）
     *
     * @param question 用户原始问题
     * @param sql      已执行的 SQL
     * @param data     查询结果集
     * @return 生成的摘要文本，失败时返回空字符串
     */
    public String generate(String question, String sql, List<Map<String, Object>> data) {
        String userContent = buildUserContent(question, sql, data);

        try {
            Response<dev.langchain4j.data.message.AiMessage> response = chatLanguageModel.generate(
                    List.of(
                            SystemMessage.from(SYSTEM_PROMPT),
                            UserMessage.from(userContent)
                    )
            );
            return response.content().text();
        } catch (Exception e) {
            log.warn("阻塞式摘要生成异常: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 流式生成摘要，通过监听器逐 token 推送结论。
     * <p>
     * 设计意图：
     * - 采用非阻塞模式，利用 {@link StreamingChatLanguageModel} 异步驱动。
     * - 业务逻辑与传输协议解耦，通过 {@link ChatStreamListener} 抽象输出通道。
     * <p>
     * 生命周期与异常处理：
     * - 正常结束：触发 {@code listener.onComplete()}。
     * - 降级处理：若摘要生成失败（非核心链路故障），将记录 Warn 日志并静默结束，
     * 确保主业务流程（如 SQL 执行结果展示）不被中断。
     * - 中断处理：若底层连接断开，回调将捕获异常并终止任务流。
     *
     * @param question 用户原始问题，用于对齐上下文。
     * @param sql      已执行的 SQL，用于提供技术依据。
     * @param data     查询结果集，仅提取样本以降低 Token 消耗。
     * @param listener 业务流监听器，用于接收并转发异步生成的 Token。
     */
    public void generateStream(String question, String sql,
                               List<Map<String, Object>> data, ChatStreamListener listener) {
        String userContent = buildUserContent(question, sql, data);

        streamingChatLanguageModel.generate(
                List.of(
                        SystemMessage.from(SYSTEM_PROMPT),
                        UserMessage.from(userContent)
                ),
                new StreamingResponseHandler<>() {
                    @Override
                    public void onNext(String token) {
                        // 通过监听器推送 token
                        listener.onSummaryToken(token);
                    }

                    @Override
                    public void onComplete(Response<dev.langchain4j.data.message.AiMessage> response) {
                        // 告知监听器流程结束
                        listener.onComplete();
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.warn("流式摘要生成失败，静默降级: {}", error.getMessage());
                        // 即使报错，也推 complete，让前端关闭连接
                        listener.onComplete();
                    }
                }
        );
    }

    // ==================== 私有工具方法 ====================

    private String buildUserContent(String question, String sql, List<Map<String, Object>> data) {
        int totalRows = data.size();
        List<Map<String, Object>> sample = data.stream().limit(MAX_SAMPLE_ROWS).toList();

        StringBuilder sb = new StringBuilder();
        sb.append("用户问题：").append(question).append("\n\n");
        sb.append("执行的 SQL：\n").append(sql).append("\n\n");
        sb.append("查询结果（共 ").append(totalRows).append(" 条");
        if (totalRows > MAX_SAMPLE_ROWS) {
            sb.append("，以下仅展示前 ").append(MAX_SAMPLE_ROWS).append(" 条样本");
        }
        sb.append("）：\n");

        if (data.isEmpty()) {
            sb.append("（无数据）");
        } else {
            for (Map<String, Object> row : sample) {
                sb.append(row).append("\n");
            }
        }
        return sb.toString();
    }
}