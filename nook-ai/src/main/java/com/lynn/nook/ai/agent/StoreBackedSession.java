package com.lynn.nook.ai.agent;

import io.agentscope.core.session.Session;
import io.agentscope.core.state.SessionKey;
import io.agentscope.core.state.State;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.harness.agent.store.BaseStore;
import io.agentscope.harness.agent.store.StoreItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 基于 agentscope {@link BaseStore}（PG JdbcStore）的会话状态持久化，替代默认写本地磁盘的
 * {@code WorkspaceSession}，满足「只读文件系统 / 全量入 PG」。
 *
 * <p>序列化复用 agentscope 官方 {@link JsonUtils#getJsonCodec()}（与框架其它部分一致，
 * 正确处理 State 多态 / Msg 等）。命名空间用 {@code [ai-session, <sessionId>]}，首段非
 * {@code "agents"}，不会被 {@link SharedMemoryStore} 的记忆归一规则误改；会话状态天然按
 * sessionId 隔离，各 Agent / 各对话线程互不干扰。
 */
public class StoreBackedSession implements Session {

    private static final String NS_ROOT = "ai-session";
    private static final String SINGLE = "single"; // 单值 payload 字段
    private static final String ITEMS = "items";   // 列表 payload 字段

    private final BaseStore store;

    public StoreBackedSession(BaseStore store) {
        this.store = store;
    }

    private static List<String> ns(SessionKey sessionKey) {
        return List.of(NS_ROOT, sessionKey.toIdentifier());
    }

    @Override
    public void save(SessionKey sessionKey, String key, State value) {
        String json = JsonUtils.getJsonCodec().toJson(value);
        store.put(ns(sessionKey), key, Map.of(SINGLE, json));
    }

    @Override
    public void save(SessionKey sessionKey, String key, List<? extends State> values) {
        List<String> jsons = new ArrayList<>(values.size());
        for (State v : values) {
            jsons.add(JsonUtils.getJsonCodec().toJson(v));
        }
        store.put(ns(sessionKey), key, Map.of(ITEMS, jsons));
    }

    @Override
    public <T extends State> Optional<T> get(SessionKey sessionKey, String key, Class<T> type) {
        StoreItem item = store.get(ns(sessionKey), key);
        if (item == null) {
            return Optional.empty();
        }
        Object payload = item.value().get(SINGLE);
        if (!(payload instanceof String json)) {
            return Optional.empty();
        }
        return Optional.of(JsonUtils.getJsonCodec().fromJson(json, type));
    }

    @Override
    public <T extends State> List<T> getList(SessionKey sessionKey, String key, Class<T> itemType) {
        StoreItem item = store.get(ns(sessionKey), key);
        if (item == null) {
            return List.of();
        }
        Object payload = item.value().get(ITEMS);
        if (!(payload instanceof List<?> raw)) {
            return List.of();
        }
        List<T> result = new ArrayList<>(raw.size());
        for (Object o : raw) {
            if (o instanceof String json) {
                result.add(JsonUtils.getJsonCodec().fromJson(json, itemType));
            }
        }
        return result;
    }

    @Override
    public boolean exists(SessionKey sessionKey) {
        return !store.search(ns(sessionKey), 1, 0).isEmpty();
    }

    @Override
    public void delete(SessionKey sessionKey) {
        for (StoreItem item : store.search(ns(sessionKey), Integer.MAX_VALUE, 0)) {
            store.delete(ns(sessionKey), item.key());
        }
    }

    @Override
    public void delete(SessionKey sessionKey, String key) {
        store.delete(ns(sessionKey), key);
    }

    @Override
    public Set<SessionKey> listSessionKeys() {
        // nook-ai 用 ai_chat_session 业务表管理会话列表；StoreItem 不携带 namespace，无法在此枚举。
        return Set.of();
    }
}
