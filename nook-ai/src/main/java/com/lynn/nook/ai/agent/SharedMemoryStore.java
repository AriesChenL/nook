package com.lynn.nook.ai.agent;

import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import io.agentscope.harness.agent.filesystem.remote.store.StoreItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 让同一 owner（userId）的多个 Agent 共享「长期事实记忆」的 {@link BaseStore} 装饰器。
 *
 * <p>{@code RemoteFilesystemSpec(USER)} 给底层 store 的 namespace 形如
 * {@code [agents, <agentId>, users, <uid>, <segment>]}。本装饰器把「记忆相关」key 的
 * {@code <agentId>} 段归一为常量 {@code _shared}，使同一 uid 下所有 Agent 命中同一份记忆；
 * 其余（sessions / tasks / skills…）原样透传，保持各 Agent 隔离。
 *
 * <p>归一规则：
 * <ul>
 *   <li>{@code segment == "memory"}（memory/ 每日流水账）→ 共享</li>
 *   <li>{@code segment == "root" && key == "/MEMORY.md"}（策划后长期记忆）→ 共享</li>
 * </ul>
 * 人格走 {@code sysPrompt}（不落 AGENTS.md），故 root 段实际只有 MEMORY.md。
 */
public class SharedMemoryStore implements BaseStore {

    static final String SHARED_AGENT = "_shared";

    private final BaseStore delegate;

    public SharedMemoryStore(BaseStore delegate) {
        this.delegate = delegate;
    }

    private static boolean isUserScoped(List<String> ns) {
        return ns.size() == 5 && "agents".equals(ns.get(0)) && "users".equals(ns.get(2));
    }

    /** 单 key 操作的命名空间归一（带 itemKey，精确区分 root 段）。 */
    private static List<String> rewrite(List<String> ns, String key) {
        if (isUserScoped(ns)) {
            String segment = ns.get(4);
            boolean shared = "memory".equals(segment)
                    || ("root".equals(segment) && "/MEMORY.md".equals(key));
            if (shared) {
                List<String> copy = new ArrayList<>(ns);
                copy.set(1, SHARED_AGENT);
                return copy;
            }
        }
        return ns;
    }

    /** search 无 itemKey，仅对 memory 段整段归一（MEMORY.md 注入走 get 而非 search）。 */
    private static List<String> rewriteSearch(List<String> ns) {
        if (isUserScoped(ns) && "memory".equals(ns.get(4))) {
            List<String> copy = new ArrayList<>(ns);
            copy.set(1, SHARED_AGENT);
            return copy;
        }
        return ns;
    }

    @Override
    public StoreItem get(List<String> namespace, String key) {
        return delegate.get(rewrite(namespace, key), key);
    }

    @Override
    public void put(List<String> namespace, String key, Map<String, Object> value) {
        delegate.put(rewrite(namespace, key), key, value);
    }

    @Override
    public List<StoreItem> search(List<String> namespace, int limit, int offset) {
        return delegate.search(rewriteSearch(namespace), limit, offset);
    }

    @Override
    public void delete(List<String> namespace, String key) {
        delegate.delete(rewrite(namespace, key), key);
    }
}
