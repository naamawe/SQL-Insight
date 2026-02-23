package com.xhx.web.adapter;

import com.xhx.ai.listener.ChatStreamListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author master
 */
@Slf4j
@RequiredArgsConstructor
public class SseChatAdapter implements ChatStreamListener {

    private final SseEmitter emitter;

    @Override
    public void onStage(String message) {
        send("stage", Map.of("message", message));
    }

    @Override
    public void onSql(String sql, boolean corrected) {
        send("sql", Map.of("sql", sql, "corrected", corrected));
    }

    @Override
    public void onData(List<Map<String, Object>> data, Long sessionId) {
        send("data", Map.of("rows", data, "total", data.size(), "sessionId", sessionId));
    }

    @Override
    public void onSummaryToken(String token) {
        send("summary", Map.of("token", token));
    }

    @Override
    public void onComplete() {
        try {
            emitter.send(SseEmitter.event().name("done").data("{}"));
            emitter.complete();
        } catch (IOException e) {
            log.warn("SSE complete send failed");
        }
    }

    @Override
    public void onError(String message) {
        try {
            send("error", Map.of("message", message));
            emitter.complete();
        } catch (Exception ignored) {}
    }

    private void send(String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.warn("SSE connection closed by client");
        }
    }
}