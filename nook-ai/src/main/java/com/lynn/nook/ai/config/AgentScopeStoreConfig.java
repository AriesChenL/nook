package com.lynn.nook.ai.config;

import com.lynn.nook.ai.agent.PgBaseStore;
import com.lynn.nook.ai.agent.SharedMemoryStore;
import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * agentscope 工作区存储装配：复用 nook-ai 的 PG {@link DataSource}，将 HarnessAgent 的
 * 工作区内容（MEMORY.md / memory/ / sessions JSONL / skills…）全部落到表 {@code agentscope_store}。
 */
@Configuration
public class AgentScopeStoreConfig {

    /** 底层 PG KV；构造时幂等建 {@code agentscope_store} 表。会话状态持久化直接用它。 */
    @Bean
    public PgBaseStore agentScopeRawStore(DataSource dataSource) {
        return new PgBaseStore(dataSource, "agentscope_store");
    }

    /** 文件系统用：在底层 store 外叠加「同 owner 跨 Agent 共享长期记忆」的命名空间归一。 */
    @Bean
    @Primary
    public BaseStore agentScopeStore(PgBaseStore agentScopeRawStore) {
        return new SharedMemoryStore(agentScopeRawStore);
    }
}
