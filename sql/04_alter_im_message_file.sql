-- 迁移：messages 表新增文件消息字段（图片/音视频/文档）。
-- 幂等：对已存在的库手动执行；docker 初始化（仅首次建库）按文件名顺序在 03 之后执行，无害。

ALTER TABLE messages ADD COLUMN IF NOT EXISTS file_url   VARCHAR(1024);
ALTER TABLE messages ADD COLUMN IF NOT EXISTS file_name  VARCHAR(255);
ALTER TABLE messages ADD COLUMN IF NOT EXISTS file_size  BIGINT;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS media_type VARCHAR(128);

COMMENT ON COLUMN messages.media_type IS '文件消息 MIME，前端据此渲染图片/视频/音频/文件';
