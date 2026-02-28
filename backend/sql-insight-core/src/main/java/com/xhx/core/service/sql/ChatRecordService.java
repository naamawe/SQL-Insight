package com.xhx.core.service.sql;

import com.xhx.core.model.vo.ChatRecordVO;

import java.util.List;
import java.util.Map;

/**
 * @author master
 */
public interface ChatRecordService {

    /**
     * 保存一条完整对话记录
     *
     * @return 记录 ID，用于后续缓存结果
     */
    Long save(Long sessionId, String question, String sql,
              int rowTotal, String summary, boolean corrected);

    /**
     * 将查询结果缓存到 Redis（最多 100 行，TTL 24 小时）
     * 同时更新数据库中的 summary 和 rowTotal
     *
     * @param recordId 对话记录 ID
     * @param data     完整查询结果
     * @param summary  AI 生成的摘要（可选，为 null 时不更新）
     */
    void cacheResult(Long recordId, List<Map<String, Object>> data, String summary);

    /**
     * 查询某个会话下的所有对话记录，附带 Redis 结果缓存状态
     *
     * @param sessionId 会话 ID
     * @return 对话记录列表（按时间正序）
     */
    List<ChatRecordVO> getBySessionId(Long sessionId);

    /**
     * 根据 ID 查询单条记录（同时校验会话归属权）
     */
    ChatRecordVO getById(Long recordId, Long userId);
}