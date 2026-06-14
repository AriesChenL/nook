-- AI 对话消息：记录 assistant 的推理过程（思考分段 + 工具调用），刷新后可还原展示。
-- 仅 assistant 行写入；内容为前后端约定的 steps JSON 数组：
--   [{"kind":"think","id":"<blockId>","text":"..."},
--    {"kind":"tool","id":"<toolCallId>","name":"...","args":"...","result":"..."}]
-- 用 TEXT 整存整取（不在库内查询内部字段，避免 jsonb 绑定开销）。
ALTER TABLE ai_message ADD COLUMN IF NOT EXISTS trace TEXT;
