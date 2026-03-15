package com.xhx.core.service.sql.pipeline.stage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xhx.common.exception.NotExistException;
import com.xhx.core.service.sql.pipeline.GeneratePipelineContext;
import com.xhx.core.service.sql.pipeline.PipelineStage;
import com.xhx.dal.entity.ChatSession;
import com.xhx.dal.mapper.ChatSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Stage 1：解析并校验会话
 * @author master
 */
@Component
@RequiredArgsConstructor
public class SessionStage implements PipelineStage {

    private final ChatSessionMapper chatSessionMapper;

    @Override
    public void process(GeneratePipelineContext ctx) {
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, ctx.getSessionId())
                        .eq(ChatSession::getUserId, ctx.getUserId()));
        if (session == null) {
            throw new NotExistException(404, "会话不存在或无权访问");
        }
        ctx.setSession(session);
    }
}
