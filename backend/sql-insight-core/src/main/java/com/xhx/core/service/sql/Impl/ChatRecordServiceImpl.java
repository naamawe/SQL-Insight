package com.xhx.core.service.sql.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xhx.core.model.vo.ChatRecordVO;
import com.xhx.core.service.cache.CacheService;
import com.xhx.core.service.sql.ChatRecordService;
import com.xhx.dal.entity.ChatRecord;
import com.xhx.dal.mapper.ChatRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRecordServiceImpl implements ChatRecordService {

    private final ChatRecordMapper chatRecordMapper;
    private final CacheService     cacheService;

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
    public void cacheResult(Long recordId, List<Map<String, Object>> data) {
        try {
            cacheService.putQueryResult(recordId, data);
        } catch (Exception e) {
            // 缓存失败不影响主流程，用户点重新执行即可
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

        return records.stream().map(this::toVO).toList();
    }

    @Override
    public ChatRecordVO getById(Long recordId, Long userId) {
        ChatRecord record = chatRecordMapper.selectById(recordId);
        if (record == null) {
            throw new com.xhx.common.exception.NotExistException("对话记录不存在");
        }

        return toVO(record);
    }

    // ==================== 私有方法 ====================

    private ChatRecordVO toVO(ChatRecord record) {
        boolean hasCache = cacheService.hasQueryResult(record.getId());
        List<Map<String, Object>> resultData = hasCache
                ? cacheService.getQueryResult(record.getId())
                : null;

        return ChatRecordVO.builder()
                .id(record.getId())
                .sessionId(record.getSessionId())
                .question(record.getQuestion())
                .sqlText(record.getSqlText())
                .summary(record.getSummary())
                .rowTotal(record.getRowTotal())
                .corrected(record.getCorrected() == 1)
                .createTime(record.getCreateTime())
                .resultData(resultData)
                .resultExpired(!hasCache)
                .build();
    }
}