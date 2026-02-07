package com.xhx.core.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xhx.core.service.ChatMemoryStore;
import com.xhx.dal.entity.ChatMessageEntity;
import com.xhx.dal.mapper.ChatMessageMapper;
import dev.langchain4j.data.message.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        Long sessionId = (Long) memoryId;
        
        // 按照顺序读取十条记录
        List<ChatMessageEntity> entities;
        try {
            entities = chatMessageMapper.selectList(
                    new LambdaQueryWrapper<ChatMessageEntity>()
                            .eq(ChatMessageEntity::getSessionId, sessionId)
                            .orderByAsc(ChatMessageEntity::getCreateTime)
                            .last("LIMIT 10")
            );
        } catch (Exception e) {
            log.error("读取数据库历史记录失败 {}", sessionId, e);
            throw new RuntimeException("读取数据库历史记录失败", e);
        }

        // 转化为LangChain4j 的 ChatMessage 对象
        return entities.stream().map(entity -> {
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
        if (lastMessage instanceof UserMessage) {
            content = ((UserMessage) lastMessage).contents().stream()
                    .filter(c -> c instanceof TextContent)
                    .map(c -> ((TextContent) c).text())
                    .collect(Collectors.joining("\n"));
        } else if (lastMessage instanceof AiMessage) {
            content = ((AiMessage) lastMessage).text();
        } else {
            content = lastMessage.toString();
        }

        ChatMessageEntity entity = ChatMessageEntity.builder()
                .sessionId(sessionId)
                .role(lastMessage.type().name())
                .content(content)
                .build();

        chatMessageMapper.insert(entity);
        log.info("会话 {} 保存新消息成功: {}", sessionId, lastMessage.type());
    }

    @Override
    public void deleteMessages(Object memoryId) {
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, memoryId));
    }
}