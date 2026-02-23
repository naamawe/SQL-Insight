package com.xhx.ai.service;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 自然语言反馈生成器
 * <p>
 * 职责：在 SQL 执行完成后，用一句自然语言向用户描述查询结果。
 * <p>
 * 设计原则：
 *   - 使用独立的一次性对话，不写入 ChatMemory，不影响对话历史
 *   - 结果数据只传摘要信息（行数 + 前几行样本），避免数据量过大撑爆 token
 *   - 生成失败时静默降级，不影响主流程返回
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
     * 根据问题、SQL、执行结果生成自然语言摘要
     *
     * @param question 用户原始问题
     * @param sql      执行的 SQL
     * @param data     SQL 执行结果
     * @return 自然语言摘要；生成失败时返回 null（调用方自行处理降级）
     */
    public String generate(String question, String sql, List<Map<String, Object>> data) {
        try {
            String userContent = buildUserContent(question, sql, data);

            var response = chatLanguageModel.generate(
                    List.of(
                            SystemMessage.from(SYSTEM_PROMPT),
                            UserMessage.from(userContent)
                    )
            );

            String summary = response.content().text();
            log.debug("自然语言摘要生成成功: {}", summary);
            return summary;

        } catch (Exception e) {
            // 摘要生成失败不影响主流程，静默降级
            log.warn("自然语言摘要生成失败，降级跳过: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 构造传给 AI 的 user 内容
     * 只传前 MAX_SAMPLE_ROWS 行作为样本，避免 token 超限
     */
    private String buildUserContent(String question, String sql,
                                    List<Map<String, Object>> data) {
        int totalRows = data.size();
        List<Map<String, Object>> sample = data.stream()
                .limit(MAX_SAMPLE_ROWS)
                .toList();

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