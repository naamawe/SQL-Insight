package com.xhx.ai.listener;

import java.util.List;
import java.util.Map;

/**
 * 业务流监听器：解耦业务逻辑与传输协议
 * @author master
 */
public interface ChatStreamListener {
    void onStage(String message);
    void onSql(String sql, boolean corrected);
    void onData(List<Map<String, Object>> data, Long sessionId);
    void onSummaryToken(String token);
    void onComplete();
    void onError(String message);

    /**
     * 可视化配置回调(可选实现)
     * 使用 Object 类型避免模块循环依赖，实际传递 VisualizationConfig
     * @param config 可视化配置
     */
    default void onVisualization(Object config) {
        // 默认空实现，保持向后兼容
    }
}