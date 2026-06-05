import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'

// 对话消息用的 Markdown 渲染：解析 → 代码高亮 → 消毒（防 XSS）
const md = new MarkdownIt({
  html: false, // 不信任原始 HTML，只认 Markdown 语法
  linkify: true, // 裸链接自动成超链接
  breaks: true, // 单换行也转 <br>，更符合聊天直觉
  highlight(code, lang): string {
    if (lang && hljs.getLanguage(lang)) {
      try {
        const out = hljs.highlight(code, { language: lang, ignoreIllegals: true }).value
        return `<pre class="hljs"><code class="language-${lang}">${out}</code></pre>`
      } catch {
        /* 落到下面的转义分支 */
      }
    }
    return `<pre class="hljs"><code>${md.utils.escapeHtml(code)}</code></pre>`
  }
})

// 链接统一新标签打开 + 安全 rel
const defaultLinkOpen =
  md.renderer.rules.link_open ||
  ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))
md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  tokens[idx].attrSet('target', '_blank')
  tokens[idx].attrSet('rel', 'noopener noreferrer')
  return defaultLinkOpen(tokens, idx, options, env, self)
}

/** 把 Markdown 文本渲染为消毒后的 HTML 字符串，供 v-html 使用 */
export function renderMarkdown(src: string): string {
  const dirty = md.render(src ?? '')
  return DOMPurify.sanitize(dirty, { ADD_ATTR: ['target', 'rel', 'class'] })
}
