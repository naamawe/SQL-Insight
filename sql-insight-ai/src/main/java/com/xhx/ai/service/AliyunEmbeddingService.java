package com.xhx.ai.service;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.xhx.ai.config.AliyunAiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AliyunEmbeddingService {

    private final AliyunAiProperties properties;

    /**
     * 将文本转化为向量
     * @param text 用户问题或表描述
     * @return 1024维的 Float 向量列表
     */
    public List<Float> getVector(String text) {
        try {
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .apiKey(properties.getApiKey())
                    .model(properties.getModel())
                    .texts(Collections.singletonList(text))
                    .build();

            TextEmbedding embedding = new TextEmbedding();
            TextEmbeddingResult result = embedding.call(param);

            if (result.getOutput() == null || result.getOutput().getEmbeddings().isEmpty()) {
                throw new RuntimeException("AI Embedding 返回结果为空");
            }

            return result.getOutput().getEmbeddings().get(0).getEmbedding()
                    .stream()
                    .map(Double::floatValue)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("调用阿里云 Embedding 接口失败: {}", e.getMessage());
            throw new RuntimeException("语义向量化失败", e);
        }
    }
}