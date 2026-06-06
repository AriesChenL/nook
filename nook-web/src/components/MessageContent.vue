<script setup lang="ts">
import { computed } from 'vue'
import { fileKind, type Message } from '@/api/im'

const props = defineProps<{ message: Message }>()

// 文件消息：contentType 2=图片 3=文件（音视频/文档按 MIME 细分）
const isFileMsg = computed(() => props.message.contentType === 2 || props.message.contentType === 3)
const kind = computed(() => fileKind(props.message.mediaType))

function fmtSize(n?: number): string {
  if (!n) return ''
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(1)} MB`
}

function openImage() {
  if (props.message.fileUrl) window.open(props.message.fileUrl, '_blank', 'noopener')
}
</script>

<template>
  <!-- 撤回 / 文本 / 系统：纯文本 -->
  <span v-if="message.recalled || !isFileMsg" class="text">{{ message.content }}</span>

  <!-- 图片 -->
  <img
    v-else-if="kind === 'image'"
    class="msg-image"
    :src="message.fileUrl"
    :alt="message.fileName"
    loading="lazy"
    @click="openImage"
  />

  <!-- 视频 -->
  <video
    v-else-if="kind === 'video'"
    class="msg-video"
    :src="message.fileUrl"
    controls
    preload="metadata"
  />

  <!-- 音频 -->
  <audio
    v-else-if="kind === 'audio'"
    class="msg-audio"
    :src="message.fileUrl"
    controls
    preload="metadata"
  />

  <!-- 其它文件：下载卡片 -->
  <a
    v-else
    class="msg-file"
    :href="message.fileUrl"
    :download="message.fileName"
    target="_blank"
    rel="noopener"
  >
    <span class="file-icon">
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" /><polyline points="14 2 14 8 20 8" />
      </svg>
    </span>
    <span class="file-info">
      <span class="file-name">{{ message.fileName }}</span>
      <span class="file-size">{{ fmtSize(message.fileSize) }}</span>
    </span>
  </a>
</template>

<style scoped>
.text {
  white-space: pre-wrap;
  word-break: break-word;
}
.msg-image {
  display: block;
  max-width: 260px;
  max-height: 300px;
  border-radius: var(--r-sm);
  border: 1px solid var(--nook-surface-border);
  box-shadow: var(--shadow-sm);
  object-fit: cover;
  cursor: zoom-in;
  transition: filter var(--dur) var(--ease-out), transform var(--dur) var(--ease-out),
    box-shadow var(--dur) var(--ease-out);
}
.msg-image:hover {
  filter: brightness(1.03);
  transform: scale(1.01);
  box-shadow: var(--shadow-md);
}
.msg-video {
  display: block;
  max-width: 300px;
  max-height: 320px;
  border-radius: var(--r-sm);
  border: 1px solid var(--nook-surface-border);
  background: #000;
}
.msg-audio {
  width: 260px;
  height: 40px;
}
.msg-file {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 200px;
  max-width: 280px;
  padding: 2px;
  border-radius: var(--r-sm);
  text-decoration: none;
  color: inherit;
  transition: opacity var(--dur) var(--ease-out);
}
.msg-file:hover { opacity: 0.92; }
/* 文件图标做成柔青底圆角芯片 */
.file-icon {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--r-sm);
  background: var(--nook-gradient-wash);
  color: var(--nook-primary-deep);
}
:global(html.dark) .file-icon { color: var(--nook-primary-soft); }
.file-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 2px;
}
.file-name {
  font-size: var(--text-sm);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-size {
  font-size: var(--text-xs);
  opacity: 0.7;
}
</style>
