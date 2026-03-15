package com.xhx.core.service.sql.pipeline;

import com.xhx.ai.model.AiResponse;
import com.xhx.common.model.TableMetadata;
import com.xhx.dal.entity.ChatSession;
import com.xhx.dal.entity.DataSource;
import lombok.Data;

import java.util.List;

/**
 * Pipeline 共享上下文，贯穿所有 Stage
 * @author master
 */
@Data
public class GeneratePipelineContext {

    // ===== 输入参数 =====
    private Long userId;
    private Long sessionId;
    private String question;

    /** correct 流程专用 */
    private String errorMessage;
    private String wrongSql;

    /** 是否为纠错流程 */
    private boolean correctMode;

    // ===== Stage 逐步填充 =====
    private ChatSession session;
    private DataSource dataSource;
    private Long roleId;

    private List<String> allowedTables;
    private List<TableMetadata> allMetadata;
    private List<TableMetadata> linkedMetadata;

    private String systemPrompt;
    private AiResponse aiResponse;

    // ===== 工厂方法 =====

    public static GeneratePipelineContext forGenerate(Long userId, Long sessionId, String question) {
        GeneratePipelineContext ctx = new GeneratePipelineContext();
        ctx.userId = userId;
        ctx.sessionId = sessionId;
        ctx.question = question;
        ctx.correctMode = false;
        return ctx;
    }

    public static GeneratePipelineContext forCorrect(Long userId, Long sessionId,
                                                      String errorMessage, String wrongSql) {
        GeneratePipelineContext ctx = new GeneratePipelineContext();
        ctx.userId = userId;
        ctx.sessionId = sessionId;
        ctx.errorMessage = errorMessage;
        ctx.wrongSql = wrongSql;
        ctx.correctMode = true;
        return ctx;
    }
}
