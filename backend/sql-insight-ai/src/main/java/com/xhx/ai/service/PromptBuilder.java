package com.xhx.ai.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt 拼装器
 * <p>
 * 职责：
 *   1. 启动时预加载所有 prompt 资源文件到内存
 *   2. 根据数据库类型选取对应的 Few-shot 示例文本
 *   3. 将 base template、few-shot、schema、policy 拼装成完整 systemPrompt
 * <p>
 * 资源文件（sql-insight-ai/src/main/resources/prompts/ 下）：
 *   system-prompt-base.txt
 *   few-shot-mysql.txt
 *   few-shot-postgresql.txt
 *   few-shot-sqlserver.txt
 * <p>
 * 此类属于 AI 能力层，不引入任何业务实体
 *
 * @author master
 */
@Slf4j
@Component
public class PromptBuilder {

    /** base prompt 模板，含 {{schema}} {{policy}} {{few_shot}} 占位符 */
    private String baseTemplate;

    /** dbType -> few-shot 文本 */
    private final Map<String, String> fewShotMap = new ConcurrentHashMap<>();

    private static final String[] SUPPORTED_DB_TYPES =
            {"mysql", "postgresql", "sqlserver"};

    @PostConstruct
    public void init() {
        baseTemplate = loadResource("prompts/system-prompt-base.txt");

        for (String dbType : SUPPORTED_DB_TYPES) {
            String path = "prompts/few-shot-" + dbType + ".txt";
            try {
                fewShotMap.put(dbType, loadResource(path));
                log.info("Few-shot 示例加载成功: {}", path);
            } catch (Exception e) {
                log.warn("Few-shot 示例文件缺失，降级为空示例: {}", path);
                fewShotMap.put(dbType, "");
            }
        }
    }

    /**
     * 拼装完整的 systemPrompt
     *
     * @param dbType  数据库类型（mysql / postgresql / sqlserver）
     * @param schema  经 SchemaLinker 过滤并格式化的 schema 文本
     * @param policy  格式化后的策略约束文本
     * @return 完整 systemPrompt，直接传给 SqlExecutor
     */
    public String build(String dbType, String schema, String policy) {
        String fewShot = fewShotMap.getOrDefault(
                dbType.toLowerCase(),
                fewShotMap.getOrDefault("mysql", "")
        );

        return baseTemplate
                .replace("{{schema}}", schema)
                .replace("{{policy}}", policy)
                .replace("{{few_shot}}", fewShot);
    }

    private String loadResource(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("无法加载 Prompt 资源文件: " + path, e);
        }
    }
}