package com.xhx.core.service.sql;

import com.xhx.ai.listener.ChatStreamListener;

/**
 * AI 对话业务接口
 * @author master
 */
public interface SqlChatApiService {
    
    /**
     * 执行流式对话
     * * @param userId       用户ID
     * @param sessionId    会话ID（可为空）
     * @param dataSourceId 数据源ID（新会话必传）
     * @param question     用户提问
     * @param listener     业务流监听器（用于解耦传输协议）
     */
    void executeChatStream(Long userId, Long sessionId, Long dataSourceId, String question, ChatStreamListener listener);}