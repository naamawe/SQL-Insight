package com.xhx.core.service.sql.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.ai.service.ChatMemoryStore;
import com.xhx.dal.entity.ChatMessageEntity;
import com.xhx.dal.mapper.ChatMessageMapper;
import dev.langchain4j.data.message.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话记忆存储实现
 *
 * <p>定义在 ai 模块的 {@link ChatMemoryStore} 接口继承自
 * LangChain4j 的 {@code ChatMemoryStore}，本类是其 core 层实现，
 * 依赖 {@link ChatMessageMapper} 将对话历史持久化到 MySQL。
 *
 * @author master
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMemoryStoreImpl implements ChatMemoryStore {

    private final ChatMessageMapper chatMessageMapper;

    /** 每次加载的最大历史消息条数（滑动窗口，取最近 N 条） */
    private static final int MEMORY_SIZE = 10;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        Long sessionId = (Long) memoryId;

        Page<ChatMessageEntity> page = chatMessageMapper.selectPage(
                new Page<>(1, MEMORY_SIZE),
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getSessionId, sessionId)
                        .orderByDesc(ChatMessageEntity::getCreateTime)
        );

        List<ChatMessageEntity> records = page.getRecords();
        Collections.reverse(records);

        return records.stream().map(entity -> {
            if (ChatMessageType.USER.name().equals(entity.getRole())) {
                return UserMessage.from(entity.getContent());
            } else {
                return AiMessage.from(entity.getContent());
            }
        }).collect(Collectors.toList());
    }

    /**
     * 追加持久化列表中的最后一条消息。
     *
     * <p><b>接口语义与实现语义的差异：</b><br>
     * LangChain4j 的 {@code ChatMemoryStore.updateMessages(List)} 原意是
     * "用给定列表全量覆盖当前记忆"，但本实现采用<b>追加语义</b>——
     * 每次只将列表中最后一条消息 INSERT 到数据库。
     *
     * <p>选择追加语义的原因：
     * <ul>
     *   <li>调用方 {@code SqlExecutor} 每次只传入一条新消息（用户发言或 AI 回复），
     *       不需要全量覆盖。</li>
     *   <li>全量回写会产生大量无效的 DELETE + INSERT，对本项目规模不合适。</li>
     *   <li>历史消息在首次写入时已持久化，不需要重复写入。</li>
     * </ul>
     *
     * @param memoryId 会话 ID
     * @param messages 本轮新增的消息列表，通常只含一条（USER 或 AI）
     */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return;
        }

        Long sessionId = (Long) memoryId;
        ChatMessage lastMessage = messages.get(messages.size() - 1);

        String content;
        if (lastMessage instanceof UserMessage userMsg) {
            content = userMsg.contents().stream()
                    .filter(c -> c instanceof TextContent)
                    .map(c -> ((TextContent) c).text())
                    .collect(Collectors.joining("\n"));
        } else if (lastMessage instanceof AiMessage aiMsg) {
            content = aiMsg.text();
        } else {
            content = lastMessage.toString();
        }

        ChatMessageEntity entity = ChatMessageEntity.builder()
                .sessionId(sessionId)
                .role(lastMessage.type().name())
                .content(content)
                .build();

        chatMessageMapper.insert(entity);
        log.info("会话 {} 保存新消息: {}", sessionId, lastMessage.type());
    }

    @Override
    public void deleteMessages(Object memoryId) {
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, memoryId));
    }
}