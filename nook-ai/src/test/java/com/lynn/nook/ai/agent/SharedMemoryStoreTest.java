package com.lynn.nook.ai.agent;

import io.agentscope.harness.agent.filesystem.remote.store.InMemoryStore;
import io.agentscope.harness.agent.filesystem.remote.store.StoreItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 验证 {@link SharedMemoryStore} 的命名空间归一：同一 owner 的多个 Agent 共享长期记忆
 * （MEMORY.md / memory/），而会话等其它内容按 Agent 隔离，不同 owner 之间完全隔离。
 *
 * <p>namespace 形如 {@code [agents, <agentId>, users, <uid>, <segment>]}。
 */
class SharedMemoryStoreTest {

    private static List<String> ns(String agentId, String uid, String segment) {
        return List.of("agents", agentId, "users", uid, segment);
    }

    @Test
    void memoryMd_sharedAcrossAgentsOfSameUser() {
        SharedMemoryStore store = new SharedMemoryStore(new InMemoryStore());

        store.put(ns("agent-1", "alice", "root"), "/MEMORY.md", Map.of("text", "主人叫天宇"));

        // agent-2（同一 owner alice）应能读到 agent-1 写的 MEMORY.md
        StoreItem it = store.get(ns("agent-2", "alice", "root"), "/MEMORY.md");
        assertNotNull(it, "MEMORY.md 应跨 Agent 共享");
        assertEquals("主人叫天宇", it.value().get("text"));
    }

    @Test
    void memoryDailyLog_sharedAcrossAgents() {
        SharedMemoryStore store = new SharedMemoryStore(new InMemoryStore());

        store.put(ns("agent-1", "alice", "memory"), "/2026-05-31.md", Map.of("text", "今天养了猫"));

        StoreItem it = store.get(ns("agent-2", "alice", "memory"), "/2026-05-31.md");
        assertNotNull(it, "memory/ 流水账应跨 Agent 共享");
        assertEquals("今天养了猫", it.value().get("text"));
    }

    @Test
    void sessions_isolatedPerAgent() {
        SharedMemoryStore store = new SharedMemoryStore(new InMemoryStore());

        store.put(ns("agent-1", "alice", "sessions"), "/s.jsonl", Map.of("a", "b"));

        // 会话内容按 Agent 隔离：agent-2 看不到 agent-1 的会话
        assertNull(store.get(ns("agent-2", "alice", "sessions"), "/s.jsonl"),
                "sessions 应按 Agent 隔离");
    }

    @Test
    void memory_isolatedAcrossUsers() {
        SharedMemoryStore store = new SharedMemoryStore(new InMemoryStore());

        store.put(ns("agent-1", "alice", "root"), "/MEMORY.md", Map.of("text", "alice 的记忆"));

        // 不同 owner（bob）应完全隔离
        assertNull(store.get(ns("agent-1", "bob", "root"), "/MEMORY.md"),
                "不同 owner 的记忆应隔离");
    }

    @Test
    void rootNonMemoryFile_notShared() {
        SharedMemoryStore store = new SharedMemoryStore(new InMemoryStore());

        // root 段里非 MEMORY.md（如 tools.json）按 Agent 隔离
        store.put(ns("agent-1", "alice", "root"), "/tools.json", Map.of("x", "y"));

        assertNull(store.get(ns("agent-2", "alice", "root"), "/tools.json"),
                "root 段里非 MEMORY.md 的文件不应被共享");
    }
}
