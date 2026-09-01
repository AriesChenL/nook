package com.lynn.nook.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * nook-ai 的可调参数，绑定 application.yml 的 {@code nook.ai.*}：DeepSeek 模型、agentscope
 * 官方 PG 存储落表位置、上下文压缩阈值。代码里不留裸字面量。
 */
@Data
@Component
@ConfigurationProperties(prefix = "nook.ai")
public class NookAiProperties {

    private Deepseek deepseek = new Deepseek();
    private Store store = new Store();
    private Compaction compaction = new Compaction();

    /** DeepSeek（OpenAI 兼容协议）。api-key 走 .env / 环境变量，不入库。 */
    @Data
    public static class Deepseek {
        private String apiKey = "";
        private String baseUrl = "https://api.deepseek.com";
        /** 模型名，默认 deepseek-v4-flash（1M 上下文）。 */
        private String model = "deepseek-v4-flash";
    }

    /** 官方 PG 存储（PostgresBaseStore / PostgresAgentStateStore）落表位置。 */
    @Data
    public static class Store {
        /** 建表所在 schema。 */
        private String schema = "public";
        /** 工作区裸对象表（PostgresBaseStore）。 */
        private String fsTable = "agentscope_fs_store";
        /** 会话状态快照表（PostgresAgentStateStore）。 */
        private String stateTable = "agentscope_state";
    }

    /** 对话上下文结构化压缩（CompactionConfig）。 */
    @Data
    public static class Compaction {
        /** 累计消息数达到该值触发一次压缩。 */
        private int triggerMessages = 40;
        /** 压缩后保留的最近消息数。 */
        private int keepMessages = 15;
    }
}
