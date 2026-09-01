-- ============================================================
-- nook-ai：agentscope 存储层切换到官方实现（agentscope-extensions-postgresql 2.0.2）
-- ============================================================
-- 此前工作区 + 会话状态都落在手写 PgBaseStore 建的 agentscope_store 表（运行时 CREATE，
-- 不在 Flyway 管辖内）。现改用官方 PostgresBaseStore（表 agentscope_fs_store）
-- 和 PostgresAgentStateStore（表 agentscope_state），表结构不同、由官方实现启动时自建。
-- 旧表仅 dev 端到端验证数据，直接丢弃。
DROP TABLE IF EXISTS agentscope_store;
