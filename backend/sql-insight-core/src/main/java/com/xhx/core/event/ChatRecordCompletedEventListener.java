package com.xhx.core.event;

import com.xhx.core.service.sql.ChatRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

/**
 * 聊天记录完成事件监听器
 * <p>
 * 职责：
 * <ul>
 *   <li>缓存查询结果到 Redis（TTL 24h）</li>
 *   <li>更新对话记录的摘要和行数</li>
 *   <li>保存图表配置（如果有）</li>
 * </ul>
 * <p>
 * 使用 {@code @Async} 异步执行，不阻塞 SSE 响应主线程
 * <p>
 * 使用 {@code @Transactional} 确保缓存和图表配置的原子性
 *
 * @author master
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRecordCompletedEventListener {

    private final ChatRecordService   chatRecordService;

    /**
     * 监听聊天记录完成事件，异步处理缓存
     * <p>
     * 使用 {@code @TransactionalEventListener(phase = AFTER_COMMIT)} 确保主事务提交后再执行
     * 使用 {@code @Async} 异步执行，不阻塞主线程
     */
    @Async("aiExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatRecordCompleted(ChatRecordCompletedEvent event) {
        Long recordId = event.getRecordId();
        List<Map<String, Object>> data = event.getData();
        String summary = event.getSummary();

        try {
            // 缓存查询结果（TTL 24h）
            chatRecordService.cacheResult(recordId, data, summary);
            log.debug("[ChatRecordCompleted] 缓存保存成功，recordId: {}, 行数：{}",
                    recordId, data.size());

        } catch (Exception e) {
            // 缓存失败不影响主业务，仅记录日志
            log.error("[ChatRecordCompleted] 缓存保存失败，recordId: {}",
                    recordId, e);
        }
    }
}