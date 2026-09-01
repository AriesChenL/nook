package com.lynn.nook.ai.config;

import com.lynn.nook.ai.agent.PersonaMiddleware;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;
import io.agentscope.harness.agent.gateway.channel.chatui.ChatUiChannel;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * nook-ai 的 agentscope 运行时：**一个单例** {@link HarnessAgent} + 绑定其上的
 * {@link ChatUiChannel}（Gateway 由 {@code agent.channel(...)} 懒加载并注册 agent）。
 *
 * <p>对话统一走 {@code channel.sendStream(SendOptions.of(userId, sessionId), ...)}：
 * <ul>
 *   <li>会话管理 + per-session 排队由 Gateway 负责；</li>
 *   <li>persona 随请求经 {@link PersonaMiddleware} 逐轮注入（{@code sysPrompt} 留空），
 *       故无需按 agentId 缓存多实例；</li>
 *   <li>记忆按 owner（{@code SendOptions.userId}）跨会话共享，工作区 / 会话状态全部入 PG。</li>
 * </ul>
 * 只读环境：关闭 agent 主动记忆工具（仅留自动 consolidation 钩子）、无 shell；
 * 本地工作区用 agentscope 默认 {@code ./.agentscope/workspace}（{@code AGENTSCOPE_WORKSPACE} 可覆盖），仅索引锚点。
 */
@Configuration
public class AgentScopeRuntimeConfig {

    /** 单例 HarnessAgent；sysPrompt 留空，persona 走 middleware 逐轮注入。 */
    @Bean(destroyMethod = "close")
    public HarnessAgent nookHarnessAgent(BaseStore agentScopeStore,
                                         AgentStateStore agentStateStore,
                                         Model deepSeekModel,
                                         NookAiProperties props) {
        NookAiProperties.Compaction c = props.getCompaction();
        return HarnessAgent.builder()
                .name("nook-ai")
                .sysPrompt("")
                .model(deepSeekModel)
                .filesystem(new RemoteFilesystemSpec(agentScopeStore).isolationScope(IsolationScope.USER))
                .stateStore(agentStateStore)
                .compaction(CompactionConfig.builder()
                        .triggerMessages(c.getTriggerMessages())
                        .keepMessages(c.getKeepMessages())
                        .build())
                .middleware(new PersonaMiddleware())
                .disableMemoryTools()
                .disableShellTool()
                .build();
    }

    /** 绑定在单例 agent 内部 Gateway 上的 ChatUI channel；对话入口。 */
    @Bean(destroyMethod = "stop")
    public ChatUiChannel nookChatChannel(HarnessAgent nookHarnessAgent) {
        return nookHarnessAgent.channel(ChatUiChannel.create());
    }
}
