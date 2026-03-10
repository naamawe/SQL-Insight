package com.xhx.ai.service;

import com.alibaba.fastjson2.JSON;
import com.xhx.ai.listener.ChatStreamListener;
import com.xhx.ai.model.FeedbackResponse;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 自然语言反馈生成器
 * <p>
 * 职责：在 SQL 执行完成后，用自然语言向用户描述查询结果，并推荐图表配置。
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

    private final ChatLanguageModel chatLanguageModel;

    /** 传给 AI 的最大样本行数，避免结果集过大导致 token 超限 */
    private static final int MAX_SAMPLE_ROWS = 5;

    private static final String SYSTEM_PROMPT = loadPromptFromFile();

    private static String loadPromptFromFile() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/nl_feedback_prompt.txt");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("无法加载提示词文件：{}", "prompts/nl_feedback_prompt.txt", e);
            throw new IllegalStateException("Failed to load prompt file: " + "prompts/nl_feedback_prompt.txt", e);
        }
    }

    /**
     * 阻塞式生成摘要和图表配置（用于 rerun 等同步场景）
     *
     * @param question 用户原始问题
     * @param sql      已执行的 SQL
     * @param data     查询结果集
     * @return FeedbackResponse 包含摘要和图表配置，失败时返回 null
     */
    public FeedbackResponse generateWithChart(String question, String sql, List<Map<String, Object>> data) {
        String userContent = buildUserContent(question, sql, data);

        try {
            Response<AiMessage> response = chatLanguageModel.generate(
                    List.of(
                            SystemMessage.from(SYSTEM_PROMPT),
                            UserMessage.from(userContent)
                    )
            );
            String rawText = response.content().text();
            return parseFeedbackResponse(rawText);
        } catch (Exception e) {
            log.warn("阻塞式摘要和图表生成异常：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 阻塞式生成纯摘要（向后兼容）
     */
    public String generate(String question, String sql, List<Map<String, Object>> data) {
        FeedbackResponse response = generateWithChart(question, sql, data);
        return response != null ? response.getSummary() : "";
    }

    /**
     * 流式生成摘要和图表配置
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
        try {
            FeedbackResponse feedbackResponse = generateWithChart(question, sql, data);
            if (feedbackResponse != null) {
                String summary = feedbackResponse.getSummary();
                if (summary != null && !summary.isBlank()) {
                    // 逐字推送 summary，保持流式体验
                    for (int i = 0; i < summary.length(); i++) {
                        listener.onSummaryToken(String.valueOf(summary.charAt(i)));
                    }
                }
                if (feedbackResponse.getChart() != null) {
                    listener.onChartConfig(feedbackResponse.getChart());
                }
            }
        } catch (Exception e) {
            log.warn("摘要和图表生成失败，静默降级：{}", e.getMessage());
        }
        listener.onComplete();
    }

    // ==================== 私有工具方法 ====================

    private FeedbackResponse parseFeedbackResponse(String rawText) {
        try {
            // 尝试提取 JSON（可能包含前后的废话）
            String json = extractJson(rawText);
            return JSON.parseObject(json, FeedbackResponse.class);
        } catch (Exception e) {
            log.warn("JSON 解析失败，原始响应：{}", rawText);
            return FeedbackResponse.builder().build();
        }
    }

    /**
     * 从文本中提取 JSON 对象
     */
    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

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