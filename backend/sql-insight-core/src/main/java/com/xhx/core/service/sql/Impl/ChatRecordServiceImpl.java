package com.xhx.core.service.sql.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xhx.common.exception.NotExistException;
import com.xhx.core.model.vo.ChatRecordVO;
import com.xhx.core.service.cache.CacheService;
import com.xhx.core.service.sql.ChatRecordService;
import com.xhx.core.service.sql.ChatSessionService;
import com.xhx.dal.entity.ChatRecord;
import com.xhx.dal.mapper.ChatRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRecordServiceImpl implements ChatRecordService {

    private final ChatRecordMapper   chatRecordMapper;
    private final CacheService       cacheService;
    private final ChatSessionService chatSessionService;

    @Override
    public Long save(Long sessionId, String question, String sql,
                     int rowTotal, String summary, boolean corrected) {
        ChatRecord record = ChatRecord.builder()
                .sessionId(sessionId)
                .question(question)
                .sqlText(sql)
                .rowTotal(rowTotal)
                .summary(summary)
                .corrected(corrected ? (short) 1 : (short) 0)
                .build();

        chatRecordMapper.insert(record);
        log.info("对话记录已保存，sessionId: {}，recordId: {}", sessionId, record.getId());
        return record.getId();
    }

    @Override
    public void cacheResult(Long recordId, List<Map<String, Object>> data, String summary) {
        try {
            cacheService.putQueryResult(recordId, data);

            // 如果提供了新的摘要，同时更新数据库中的 summary 和 rowTotal
            if (summary != null) {
                ChatRecord record = chatRecordMapper.selectById(recordId);
                if (record != null) {
                    record.setSummary(summary);
                    record.setRowTotal(data.size());
                    chatRecordMapper.updateById(record);
                    log.info("对话记录已更新，recordId: {}，新行数: {}，摘要已刷新", recordId, data.size());
                }
            }
        } catch (Exception e) {
            log.warn("查询结果缓存失败，recordId: {}，原因: {}", recordId, e.getMessage());
        }
    }

    @Override
    public List<ChatRecordVO> getBySessionId(Long sessionId) {
        List<ChatRecord> records = chatRecordMapper.selectList(
                new LambdaQueryWrapper<ChatRecord>()
                        .eq(ChatRecord::getSessionId, sessionId)
                        .orderByAsc(ChatRecord::getCreateTime)
        );
        
        if (records.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量获取所有记录的缓存结果，避免N+1查询
        List<Long> recordIds = records.stream().map(ChatRecord::getId).toList();
        Map<Long, List<Map<String, Object>>> cacheMap = cacheService.batchGetQueryResults(recordIds);

        log.debug("[历史记录查询] sessionId: {}, 记录数: {}, 缓存命中: {}", 
                sessionId, records.size(), 
                cacheMap.values().stream().filter(Objects::nonNull).count());

        // 组装VO
        return records.stream()
                .map(record -> toVO(record, cacheMap.get(record.getId())))
                .toList();
    }

    @Override
    public ChatRecordVO getById(Long recordId, Long userId) {
        ChatRecord record = chatRecordMapper.selectById(recordId);
        if (record == null) {
            throw new NotExistException("对话记录不存在");
        }
        chatSessionService.getSessionDetail(userId, record.getSessionId());
        return toVO(record);
    }

    // ==================== 私有方法 ====================

    /**
     * 转换为VO（单条记录查询时使用）
     */
    private ChatRecordVO toVO(ChatRecord record) {
        List<Map<String, Object>> resultData = cacheService.getQueryResult(record.getId());
        return toVO(record, resultData);
    }

    /**
     * 转换为VO（批量查询时使用，避免重复查询缓存）
     */
    private ChatRecordVO toVO(ChatRecord record, List<Map<String, Object>> resultData) {
        boolean hasCache = resultData != null;

        return ChatRecordVO.builder()
                .id(record.getId())
                .sessionId(record.getSessionId())
                .question(record.getQuestion())
                .sqlText(record.getSqlText())
                .summary(record.getSummary())
                .rowTotal(record.getRowTotal())
                .corrected(record.getCorrected() != null && record.getCorrected() == 1)
                .createTime(record.getCreateTime())
                .resultData(resultData)
                .resultExpired(!hasCache)
                .build();
    }
}