package com.lynn.nook.ai.config;

import io.agentscope.core.formatter.openai.DeepSeekFormatter;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DeepSeek 模型装配：OpenAI 兼容协议 + {@link DeepSeekFormatter}。
 * 默认 {@code deepseek-v4-flash}（baseUrl {@code https://api.deepseek.com}，1M 上下文）。
 */
@Configuration
public class DeepSeekModelConfig {

    @Bean
    public Model deepSeekModel(
            @Value("${deepseek.api-key:}") String apiKey,
            @Value("${deepseek.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${deepseek.model:deepseek-v4-flash}") String modelName) {
        return OpenAIChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .stream(true)
                .formatter(new DeepSeekFormatter())
                .build();
    }
}
