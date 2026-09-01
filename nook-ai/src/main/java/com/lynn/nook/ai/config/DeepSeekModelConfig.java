package com.lynn.nook.ai.config;

import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.compat.deepseek.DeepSeekFormatter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DeepSeek 模型装配：OpenAI 兼容协议 + {@link DeepSeekFormatter}。参数见
 * {@link NookAiProperties.Deepseek}（{@code nook.ai.deepseek.*}）。
 */
@Configuration
public class DeepSeekModelConfig {

    @Bean
    public Model deepSeekModel(NookAiProperties props) {
        NookAiProperties.Deepseek ds = props.getDeepseek();
        return OpenAIChatModel.builder()
                .apiKey(ds.getApiKey())
                .baseUrl(ds.getBaseUrl())
                .modelName(ds.getModel())
                .stream(true)
                .formatter(new DeepSeekFormatter())
                .build();
    }
}
