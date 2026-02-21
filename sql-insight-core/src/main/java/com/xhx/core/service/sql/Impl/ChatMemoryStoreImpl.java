package com.xhx.core.service.sql.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.core.service.sql.ChatMemoryStore;
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
 * @author master
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMemoryStoreImpl implements ChatMemoryStore {

    private final ChatMessageMapper chatMessageMapper;

    /** 每次加载的历史消息条数上限 */
    private static final int MEMORY_SIZE = 10;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        Long sessionId = (Long) memoryId;

        // 先倒序取最近 MEMORY_SIZE 条
        Page<ChatMessageEntity> page = chatMessageMapper.selectPage(
                new Page<>(1, MEMORY_SIZE),
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getSessionId, sessionId)
                        .orderByDesc(ChatMessageEntity::getCreateTime)
        );

        // 再反转，恢复时间正序后交给 AI
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