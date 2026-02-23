package com.xhx.core.service.sql;

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
 *   1. 启动时预加载所有 prompt 资源文件到内存（避免每次请求都读磁盘）
 *   2. 根据数据库类型返回对应的 Few-shot 示例文本
 *   3. 将 base prompt、few-shot、schema、policy 拼装成完整的 systemPrompt
 * <p>
 * 资源文件路径（均在 sql-insight-ai/src/main/resources/ 下）：
 *   prompts/system-prompt-base.txt
 *   prompts/few-shot-mysql.txt
 *   prompts/few-shot-postgresql.txt
 *   prompts/few-shot-oracle.txt
 *   prompts/few-shot-sqlserver.txt
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
            {"mysql", "postgresql", "oracle", "sqlserver"};

    @PostConstruct
    public void init() {
        baseTemplate = loadResource("prompts/system-prompt-base.txt");

        for (String dbType : SUPPORTED_DB_TYPES) {
            String path = "prompts/few-shot-" + dbType + ".txt";
            try {
                fewShotMap.put(dbType, loadResource(path));
                log.info("Few-shot 示例加载成功: {}", path);
            } catch (Exception e) {
                // 示例文件缺失时降级为空，不影响启动
                log.warn("Few-shot 示例文件缺失，降级为空示例: {}", path);
                fewShotMap.put(dbType, "");
            }
        }
    }

    /**
     * 拼装完整的 systemPrompt
     *
     * @param dbType  数据库类型（mysql / postgresql / oracle / sqlserver）
     * @param schema  经过 SchemaLinker 过滤并格式化后的 schema 文本
     * @param policy  格式化后的 policy 约束文本
     * @return 完整的 systemPrompt 字符串，直接传给 SqlExecutor.execute()
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