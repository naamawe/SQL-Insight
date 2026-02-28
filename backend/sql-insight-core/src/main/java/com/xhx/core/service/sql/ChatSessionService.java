package com.xhx.core.service.sql;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.dal.entity.ChatSession;
import com.xhx.core.model.vo.ChatSessionVO;
import java.util.List;

/**
 * 会话管理服务
 * @author master
 */
public interface ChatSessionService {

    /**
     * 创建会话
     */
    Long createSession(Long userId, Long dataSourceId, String question);

    /**
     * 获取用户的会话列表（分页）
     */
    Page<ChatSessionVO> getUserSessions(Long userId, int current, int size);

    /**
     * 获取会话详情
     */
    ChatSession getSessionDetail(Long userId, Long sessionId);

    /**
     * 重命名会话
     */
    void renameSession(Long userId, Long sessionId, String title);

    /**
     * 删除会话
     */
    void deleteSession(Long userId, Long sessionId);

    /**
     * 批量删除会话
     */
    void batchDeleteSessions(Long userId, List<Long> sessionIds);
}