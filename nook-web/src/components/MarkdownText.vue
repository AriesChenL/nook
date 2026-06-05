<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps<{ content: string }>()
const html = computed(() => renderMarkdown(props.content))
</script>

<template>
  <!-- 内容已经过 DOMPurify 消毒 -->
  <div class="md" v-html="html" />
</template>

<style scoped>
.md {
  font-size: 14px;
  line-height: 1.65;
  word-break: break-word;
  overflow-wrap: anywhere;
}

/* v-html 注入的内容需用 :deep 穿透 scoped */
.md :deep(> :first-child) { margin-top: 0; }
.md :deep(> :last-child) { margin-bottom: 0; }

.md :deep(p) { margin: 0 0 10px; }
.md :deep(h1),
.md :deep(h2),
.md :deep(h3),
.md :deep(h4) {
  font-family: var(--nook-font-display);
  font-weight: 700;
  line-height: 1.3;
  margin: 16px 0 8px;
  letter-spacing: -0.01em;
}
.md :deep(h1) { font-size: 1.35em; }
.md :deep(h2) { font-size: 1.22em; }
.md :deep(h3) { font-size: 1.1em; }
.md :deep(h4) { font-size: 1em; }

.md :deep(ul),
.md :deep(ol) { margin: 0 0 10px; padding-left: 1.4em; }
.md :deep(li) { margin: 3px 0; }
.md :deep(li > ul),
.md :deep(li > ol) { margin: 3px 0; }

.md :deep(a) {
  color: var(--nook-primary-deep);
  text-decoration: none;
  border-bottom: 1px solid rgba(20, 184, 166, 0.4);
  transition: color var(--dur) var(--ease-out), border-color var(--dur) var(--ease-out);
}
.md :deep(a:hover) { color: var(--nook-accent-deep); border-color: var(--nook-accent); }

.md :deep(strong) { font-weight: 700; }
.md :deep(em) { font-style: italic; }

/* 行内代码 */
.md :deep(:not(pre) > code) {
  font-family: 'JetBrains Mono', ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  font-size: 0.88em;
  padding: 0.12em 0.4em;
  border-radius: 6px;
  background: var(--nook-surface-sunken);
  border: 1px solid var(--nook-hairline);
}

/* 代码块（hljs 主题在 main.ts 全局引入，提供配色） */
.md :deep(pre.hljs) {
  margin: 0 0 12px;
  padding: 14px 16px;
  border-radius: var(--r-sm);
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.55;
  border: 1px solid rgba(148, 163, 184, 0.18);
  box-shadow: var(--shadow-sm);
}
.md :deep(pre.hljs code) {
  font-family: 'JetBrains Mono', ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  background: none;
  padding: 0;
  border: none;
}

/* 引用块 */
.md :deep(blockquote) {
  margin: 0 0 12px;
  padding: 4px 14px;
  border-left: 3px solid var(--nook-primary-soft);
  color: var(--nook-text-muted);
  background: var(--nook-surface-sunken);
  border-radius: 0 8px 8px 0;
}

/* 表格 */
.md :deep(table) {
  border-collapse: collapse;
  margin: 0 0 12px;
  font-size: 13px;
  width: 100%;
}
.md :deep(th),
.md :deep(td) {
  border: 1px solid var(--nook-hairline);
  padding: 6px 10px;
  text-align: left;
}
.md :deep(th) { background: var(--nook-surface-sunken); font-weight: 600; }

/* 分割线 */
.md :deep(hr) {
  border: none;
  border-top: 1px solid var(--nook-hairline);
  margin: 14px 0;
}

.md :deep(img) { max-width: 100%; border-radius: var(--r-sm); }
</style>
