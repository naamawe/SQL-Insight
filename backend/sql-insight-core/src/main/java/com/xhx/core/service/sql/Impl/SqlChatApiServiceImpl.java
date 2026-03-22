package com.xhx.core.service.sql.Impl;

import com.xhx.ai.listener.ChatStreamListener;
import com.xhx.ai.model.FeedbackResponse;
import com.xhx.ai.service.NlFeedbackGenerator;
import com.xhx.core.model.vo.ChatRecordVO;
import com.xhx.core.service.chart.ChartConfigService;
import com.xhx.core.service.sql.*;
import com.xhx.dal.entity.ChartConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlChatApiServiceImpl implements SqlChatApiService {

    private final SqlChatTransactionalService transactionalService;
    private final SqlExecutorService       sqlExecutorService;
    private final ChatSessionService       chatSessionService;
    private final NlFeedbackGenerator      nlFeedbackGenerator;
    private final ChatRecordService        chatRecordService;
    private final ChartConfigService       chartConfigService;
    private final SqlSecurityService       sqlSecurityService;

    @Async("aiExecutor")
    @Override
    public void executeChatStream(Long userId, Long sessionId, Long dataSourceId,
                                  String question, ChatStreamListener listener) {
        transactionalService.execute(userId, sessionId, dataSourceId, question, listener);
    }

    /**
     * 重新执行历史记录（带事务控制）
     * <p>
     * 事务范围：缓存查询结果 → 保存图表配置
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Map<String, Object> rerunRecord(Long recordId, Long userId) {
        ChatRecordVO record = chatRecordService.getById(recordId, userId);

        // 缓存仍有效，直接返回
        if (!record.getResultExpired() && record.getResultData() != null) {
            log.info("[历史记录重执行] 缓存有效，直接返回, userId: {}, recordId: {}", userId, recordId);
            return Map.of("data", record.getResultData(),
                    "summary", record.getSummary(),
                    "total", record.getRowTotal());
        }

        Long dataSourceId = chatSessionService.getSessionDetail(userId, record.getSessionId()).getDataSourceId();
        sqlSecurityService.validate(record.getSqlText(), userId, dataSourceId);

        List<Map<String, Object>> data = sqlExecutorService.execute(dataSourceId, record.getSqlText());
        log.info("[历史记录重执行成功] userId: {}, recordId: {}, 行数: {}", userId, recordId, data.size());

        FeedbackResponse feedback = nlFeedbackGenerator.generateWithChart(
                record.getQuestion(), record.getSqlText(), data);
        String summary = feedback != null ? feedback.getSummary() : "";

        chatRecordService.cacheResult(recordId, data, summary);

        if (feedback != null && feedback.getChart() != null) {
            chartConfigService.saveOrUpdate(ChartConfig.builder()
                    .recordId(recordId)
                    .type(feedback.getChart().getType())
                    .xAxis(feedback.getChart().getXAxis())
                    .yAxis(feedback.getChart().getYAxis())
                    .title(feedback.getChart().getTitle())
                    .isUserModified(false)
                    .build());
            log.info("[历史记录重执行] 图表配置已更新，recordId: {}", recordId);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("data", data);
        result.put("summary", summary);
        result.put("total", data.size());
        return result;
    }
}