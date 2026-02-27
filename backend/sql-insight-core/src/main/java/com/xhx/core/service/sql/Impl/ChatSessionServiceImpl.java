package com.xhx.core.service.sql.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.common.exception.NotExistException;
import com.xhx.core.service.sql.ChatSessionService;
import com.xhx.dal.entity.ChatMessageEntity;
import com.xhx.dal.entity.ChatRecord;
import com.xhx.dal.entity.ChatSession;
import com.xhx.dal.mapper.ChatMessageMapper;
import com.xhx.dal.mapper.ChatRecordMapper;
import com.xhx.dal.mapper.ChatSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatRecordMapper  chatRecordMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSession(Long userId, Long dataSourceId, String question) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setDataSourceId(dataSourceId);
        session.setTitle(question.length() > 20 ? question.substring(0, 20) + "..." : question);

        chatSessionMapper.insert(session);
        log.info("==> 创建会话成功，ID: {}, 用户: {}", session.getId(), userId);
        return session.getId();
    }

    @Override
    public Page<ChatSession> getUserSessions(Long userId, int current, int size) {
        return chatSessionMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .orderByDesc(ChatSession::getCreateTime)
        );
    }

    @Override
    public ChatSession getSessionDetail(Long userId, Long sessionId) {
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId)
        );
        if (session == null) {
            throw new NotExistException(404, "会话不存在或无权访问");
        }
        return session;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renameSession(Long userId, Long sessionId, String title) {
        ChatSession session = getSessionDetail(userId, sessionId);
        session.setTitle(title);
        chatSessionMapper.updateById(session);
        log.info("==> 会话 {} 标题已更新为: {}", sessionId, title);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long userId, Long sessionId) {
        // 防止越权
        getSessionDetail(userId, sessionId);

        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId));

        chatRecordMapper.delete(new LambdaQueryWrapper<ChatRecord>()
                .eq(ChatRecord::getSessionId, sessionId));

        chatSessionMapper.deleteById(sessionId);
        log.info("==> 会话 {} 已删除", sessionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteSessions(Long userId, List<Long> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }

        // 防止越权
        List<Long> ownedSessionIds = chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .select(ChatSession::getId)
                        .eq(ChatSession::getUserId, userId)
                        .in(ChatSession::getId, sessionIds)
        ).stream().map(ChatSession::getId).toList();

        if (ownedSessionIds.isEmpty()) {
            return;
        }

        chatSessionMapper.delete(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .in(ChatSession::getId, ownedSessionIds));

        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessageEntity>()
                .in(ChatMessageEntity::getSessionId, ownedSessionIds));

        chatRecordMapper.delete(new LambdaQueryWrapper<ChatRecord>()
                .in(ChatRecord::getSessionId, ownedSessionIds));

        log.info("==> 批量删除会话成功，userId: {}，共删除 {} 个", userId, ownedSessionIds.size());
    }
}