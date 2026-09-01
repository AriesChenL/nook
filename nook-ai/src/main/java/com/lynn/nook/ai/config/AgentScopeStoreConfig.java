package com.lynn.nook.ai.config;

import com.lynn.nook.ai.agent.SharedMemoryStore;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.postgresql.state.PostgresAgentStateStore;
import io.agentscope.extensions.postgresql.store.PostgresBaseStore;
import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * agentscope 存储装配：全部复用 nook-ai 的 PG {@link DataSource}，用 agentscope 官方
 * {@code agentscope-extensions-postgresql} 实现，满足「只读文件系统 / 全量入 PG」。
 *
 * <ul>
 *   <li>{@link PostgresBaseStore}：HarnessAgent 工作区裸对象（MEMORY.md / memory/ 每日流水账等）。
 *       外层再叠 {@link SharedMemoryStore} 做「同 owner 跨 Agent 共享长期记忆」的命名空间归一。</li>
 *   <li>{@link PostgresAgentStateStore}：会话状态快照，按 {@code (agentId, sessionId, key)} 寻址。</li>
 * </ul>
 *
 * <p>表名 / schema 见 {@link NookAiProperties.Store}（{@code nook.ai.store.*}）。
 * 表结构由官方实现按需自建；旧的手写 {@code agentscope_store} 已由 Flyway V4 删除。
 */
@Configuration
public class AgentScopeStoreConfig {

    /** 工作区裸对象存储（官方 PG BaseStore）。 */
    @Bean
    public PostgresBaseStore agentScopeRawStore(DataSource dataSource, NookAiProperties props) {
        return PostgresBaseStore.builder(dataSource)
                .schemaName(props.getStore().getSchema())
                .tableName(props.getStore().getFsTable())
                .initializeSchema(true)
                .build();
    }

    /** 文件系统用：在官方 store 外叠加「同 owner 跨 Agent 共享长期记忆」的命名空间归一。 */
    @Bean
    @Primary
    public BaseStore agentScopeStore(PostgresBaseStore agentScopeRawStore) {
        return new SharedMemoryStore(agentScopeRawStore);
    }

    /** 会话状态持久化（官方 PG AgentStateStore）。 */
    @Bean(destroyMethod = "close")
    public AgentStateStore agentStateStore(DataSource dataSource, NookAiProperties props) {
        return PostgresAgentStateStore.builder(dataSource)
                .schemaName(props.getStore().getSchema())
                .tableName(props.getStore().getStateTable())
                .createIfNotExist(true)
                .build();
    }
}
