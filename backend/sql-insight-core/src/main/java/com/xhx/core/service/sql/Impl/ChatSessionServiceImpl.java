package com.xhx.core.service.sql.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.common.exception.NotExistException;
import com.xhx.core.service.cache.CacheService;
import com.xhx.core.service.sql.ChatSessionService;
import com.xhx.dal.entity.ChatMessageEntity;
import com.xhx.dal.entity.ChatRecord;
import com.xhx.dal.entity.ChatSession;
import com.xhx.dal.entity.DataSource;
import com.xhx.dal.mapper.ChatMessageMapper;
import com.xhx.dal.mapper.ChatRecordMapper;
import com.xhx.dal.mapper.ChatSessionMapper;
import com.xhx.dal.mapper.DataSourceMapper;
import com.xhx.core.model.vo.ChatSessionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final CacheService      cacheService;
    private final DataSourceMapper  dataSourceMapper;

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
    public Page<ChatSessionVO> getUserSessions(Long userId, int current, int size) {
        // 查询会话列表
        Page<ChatSession> sessionPage = chatSessionMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .orderByDesc(ChatSession::getCreateTime)
        );

        // 获取所有数据源ID
        List<Long> dataSourceIds = sessionPage.getRecords().stream()
                .map(ChatSession::getDataSourceId)
                .distinct()
                .toList();

        // 批量查询数据源信息
        Map<Long, String> dataSourceNameMap = dataSourceIds.isEmpty() ? Map.of() :
                dataSourceMapper.selectBatchIds(dataSourceIds).stream()
                        .collect(Collectors.toMap(DataSource::getId, DataSource::getConnName));

        // 转换为 VO
        Page<ChatSessionVO> voPage = new Page<>(sessionPage.getCurrent(), sessionPage.getSize(), sessionPage.getTotal());
        List<ChatSessionVO> voList = sessionPage.getRecords().stream().map(session -> {
            ChatSessionVO vo = new ChatSessionVO();
            BeanUtils.copyProperties(session, vo);
            vo.setDataSourceName(dataSourceNameMap.getOrDefault(session.getDataSourceId(), "未知数据源"));
            return vo;
        }).toList();
        voPage.setRecords(voList);

        return voPage;
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

        List<Long> recordIds = chatRecordMapper.selectList(
                new LambdaQueryWrapper<ChatRecord>()
                        .select(ChatRecord::getId)
                        .eq(ChatRecord::getSessionId, sessionId)
        ).stream().map(ChatRecord::getId).toList();

        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId));

        chatRecordMapper.delete(new LambdaQueryWrapper<ChatRecord>()
                .eq(ChatRecord::getSessionId, sessionId));

        chatSessionMapper.deleteById(sessionId);

        cacheService.evictQueryResults(recordIds);

        log.info("==> 会话 {} 已删除，清理了 {} 条记录的查询结果缓存",
                sessionId, recordIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteSessions(Long userId, List<Long> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }

        // 防止越权，只处理属于当前用户的会话
        List<Long> ownedSessionIds = chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .select(ChatSession::getId)
                        .eq(ChatSession::getUserId, userId)
                        .in(ChatSession::getId, sessionIds)
        ).stream().map(ChatSession::getId).toList();

        if (ownedSessionIds.isEmpty()) {
            return;
        }

        List<Long> recordIds = chatRecordMapper.selectList(
                new LambdaQueryWrapper<ChatRecord>()
                        .select(ChatRecord::getId)
                        .in(ChatRecord::getSessionId, ownedSessionIds)
        ).stream().map(ChatRecord::getId).toList();

        chatSessionMapper.delete(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .in(ChatSession::getId, ownedSessionIds));

        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessageEntity>()
                .in(ChatMessageEntity::getSessionId, ownedSessionIds));

        chatRecordMapper.delete(new LambdaQueryWrapper<ChatRecord>()
                .in(ChatRecord::getSessionId, ownedSessionIds));

        cacheService.evictQueryResults(recordIds);

        log.info("==> 批量删除会话成功，userId: {}，共删除 {} 个会话，清理了 {} 条记录的查询结果缓存",
                userId, ownedSessionIds.size(), recordIds.size());
    }
}