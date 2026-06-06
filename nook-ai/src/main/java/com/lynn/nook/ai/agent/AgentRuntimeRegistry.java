package com.lynn.nook.ai.agent;

import com.lynn.nook.ai.entity.AiAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.session.Session;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.store.BaseStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 agentId 懒加载并缓存 {@link HarnessAgent} 实例。一个实例服务该 Agent 的所有 owner/会话
 * （靠 {@code RuntimeContext} 区分）；记忆按 owner 跨 Agent 共享、会话按 sessionId 隔离。
 *
 * <p>只读环境适配：工作区/会话全部经 PG store 持久化；构建时禁用依赖本地 SQLite 的 memory_search，
 * 不配 {@code workspaceIndex}（退化为 PG 全量扫描），临时 workspace 仅作占位空目录。
 */
@Slf4j
@Component
public class AgentRuntimeRegistry implements DisposableBean {

    private final BaseStore sharedStore;   // 文件系统用（记忆跨 Agent 共享）
    private final Session session;         // 会话状态持久化到 PG
    private final Model model;
    private final Path workspaceTmp;

    private final Map<Long, HarnessAgent> cache = new ConcurrentHashMap<>();

    public AgentRuntimeRegistry(
            BaseStore sharedStore,
            PgBaseStore agentScopeRawStore,
            Model model,
            @Value("${nook.ai.workspace-tmp:${java.io.tmpdir}/nook-ai-workspace}") String workspaceTmp) {
        this.sharedStore = sharedStore;
        this.session = new StoreBackedSession(agentScopeRawStore);
        this.model = model;
        this.workspaceTmp = Paths.get(workspaceTmp);
        try {
            Files.createDirectories(this.workspaceTmp);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建 agentscope 临时工作区: " + this.workspaceTmp, e);
        }
    }

    /** 取或构建该 Agent 的 HarnessAgent 运行时。 */
    public HarnessAgent get(AiAgent agent) {
        return cache.computeIfAbsent(agent.getId(), id -> build(agent));
    }

    /** persona / 配置变更后调用：移除并关闭旧实例，下次重建。 */
    public void invalidate(Long agentId) {
        closeQuietly(cache.remove(agentId));
    }

    /** HarnessAgent 2.0 起实现 AutoCloseable，移除/关停时显式释放（替代旧版依赖 GC）。 */
    private void closeQuietly(HarnessAgent agent) {
        if (agent == null) return;
        try {
            agent.close();
        } catch (Exception e) {
            log.warn("关闭 HarnessAgent 失败: {}", e.getMessage());
        }
    }

    private HarnessAgent build(AiAgent agent) {
        log.info("构建 HarnessAgent agentId=agent-{} name={}", agent.getId(), agent.getName());
        return HarnessAgent.builder()
                // name 作为内部 agentId（唯一稳定）；展示名走 ai_agent.name。
                // 2.0 起 builder 也支持 agentId()，此处仍以 name 承载稳定 id，避免改动已持久化的会话键
                .name("agent-" + agent.getId())
                .sysPrompt(agent.getPersona() == null ? "" : agent.getPersona())
                .model(model)
                .workspace(workspaceTmp)
                .filesystem(new RemoteFilesystemSpec(sharedStore).isolationScope(IsolationScope.USER))
                .session(session)
                .compaction(CompactionConfig.builder().triggerMessages(40).keepMessages(15).build())
                .disableMemoryTools()   // 去掉依赖本地 SQLite MemoryIndex 的 memory_search
                .disableShellTool()     // 只读环境，无 shell
                .build();
    }

    @Override
    public void destroy() {
        cache.values().forEach(this::closeQuietly);
        cache.clear();
    }
}
