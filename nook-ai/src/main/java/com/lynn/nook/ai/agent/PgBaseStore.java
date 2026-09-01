package com.lynn.nook.ai.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import io.agentscope.harness.agent.filesystem.remote.store.StoreItem;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL 实现的 agentscope {@link BaseStore}（命名空间 KV，4 方法：get/put/search/delete）。
 *
 * <p>本类是在官方 {@code JdbcStore} 尚未发布时按其设计自实现的 PG 版：HarnessAgent 的工作区内容
 * （MEMORY.md / memory/ / sessions JSONL 等）全部经此落表 {@code agentscope_store}，满足
 * 「只读文件系统 / 全量入 PG」。
 * 注：agentscope 2.0.2 起官方 {@code agentscope-extensions-postgresql} 提供
 * {@code io.agentscope.extensions.postgresql.store.PostgresBaseStore}，后续可评估替换本自实现
 * （表结构不同，切换需迁移 {@code agentscope_store} 存量数据，暂保留现状）。
 *
 * <h2>表结构</h2>
 * <pre>{@code
 * agentscope_store(namespace_path, item_key, value_json TEXT, version BIGINT, updated_at BIGINT,
 *                  PRIMARY KEY(namespace_path, item_key))
 * }</pre>
 * version 列仅用于写侧记账；读侧构造 {@link StoreItem} 不依赖它（2.0 起 StoreItem 带 version，此处不暴露）。
 *
 * <h2>命名空间编码</h2>
 * 段之间用 ASCII unit-separator {@code 0x1F} 连接并以分隔符结尾，从而 {@code search([a,b])} 可用
 * {@code namespace_path LIKE 'ab%' ESCAPE '!'} 命中该命名空间及其所有子命名空间。
 */
public class PgBaseStore implements BaseStore {

    /** ASCII unit separator (U+001F)，命名空间段连接符。 */
    private static final char SEP = (char) 0x1F;
    private static final char ESC = '!';

    private final DataSource dataSource;
    private final String table;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PgBaseStore(DataSource dataSource, String table) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must not be null");
        }
        if (table == null || !table.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("非法表名: " + table);
        }
        this.dataSource = dataSource;
        this.table = table;
        initSchema();
    }

    private void initSchema() {
        String ddl = "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "namespace_path VARCHAR(1024) NOT NULL,"
                + "item_key       VARCHAR(512)  NOT NULL,"
                + "value_json     TEXT          NOT NULL,"
                + "version        BIGINT        NOT NULL,"
                + "updated_at     BIGINT        NOT NULL,"
                + "PRIMARY KEY (namespace_path, item_key))";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(ddl)) {
            ps.execute();
        } catch (SQLException e) {
            throw new IllegalStateException("初始化 " + table + " 失败", e);
        }
    }

    private static String nsPath(List<String> namespace) {
        StringBuilder sb = new StringBuilder();
        for (String seg : namespace) {
            if (seg.indexOf(SEP) >= 0) {
                throw new IllegalArgumentException("命名空间段不得包含 0x1F: " + seg);
            }
            sb.append(seg).append(SEP);
        }
        return sb.toString();
    }

    private static String escapeLike(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == ESC || ch == '%' || ch == '_') {
                sb.append(ESC);
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 value 失败", e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("反序列化 value 失败", e);
        }
    }

    @Override
    public StoreItem get(List<String> namespace, String key) {
        String sql = "SELECT value_json FROM " + table
                + " WHERE namespace_path = ? AND item_key = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, nsPath(namespace));
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new StoreItem(key, fromJson(rs.getString(1)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("get 失败", e);
        }
    }

    @Override
    public void put(List<String> namespace, String key, Map<String, Object> value) {
        String sql = "INSERT INTO " + table
                + " (namespace_path, item_key, value_json, version, updated_at) VALUES (?, ?, ?, 1, ?)"
                + " ON CONFLICT (namespace_path, item_key) DO UPDATE SET"
                + " value_json = EXCLUDED.value_json, version = " + table + ".version + 1,"
                + " updated_at = EXCLUDED.updated_at";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, nsPath(namespace));
            ps.setString(2, key);
            ps.setString(3, toJson(value));
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("put 失败", e);
        }
    }

    @Override
    public List<StoreItem> search(List<String> namespace, int limit, int offset) {
        String like = escapeLike(nsPath(namespace)) + "%";
        String sql = "SELECT item_key, value_json FROM " + table
                + " WHERE namespace_path LIKE ? ESCAPE '!' ORDER BY item_key LIMIT ? OFFSET ?";
        List<StoreItem> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, like);
            ps.setInt(2, limit < 0 ? Integer.MAX_VALUE : limit);
            ps.setInt(3, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new StoreItem(rs.getString(1), fromJson(rs.getString(2))));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("search 失败", e);
        }
        return result;
    }

    @Override
    public void delete(List<String> namespace, String key) {
        String sql = "DELETE FROM " + table + " WHERE namespace_path = ? AND item_key = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, nsPath(namespace));
            ps.setString(2, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("delete 失败", e);
        }
    }
}
