<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import NookModal from '@/components/NookModal.vue'
import { confirm } from '@/composables/useConfirm'
import {
  aiPresets,
  chat,
  createAgent,
  deleteAgent,
  listAgents,
  listAgentMessages,
  listSessions,
  updateAgent,
  type Agent,
  type ChatSession
} from '@/api/ai'
import MarkdownText from '@/components/MarkdownText.vue'

interface ChatTurn {
  id: number
  role: 'user' | 'assistant'
  content: string
}

// ───── 状态 ─────
const agents = ref<Agent[]>([])
const currentAgentId = ref<string | null>(null)
const sessions = ref<ChatSession[]>([])
const currentSessionId = ref<string | null>(null)
// 会话 → 对话流。首次进入某 Agent 时从后端拉历史填充；之后增量追加。
// key 为 session public_id 字符串，或新建会话前的临时桶 NEW_BUCKET。
const turnsBySession = reactive<Record<string, ChatTurn[]>>({})

const draft = ref('')
const sending = ref(false)
const composerEl = ref<HTMLTextAreaElement | null>(null)
const loadingAgents = ref(false)
const scroller = ref<HTMLDivElement | null>(null)

// 新建会话前的临时桶 key（后端首次对话才分配真实 sessionId）
const NEW_BUCKET = '__new__'

const currentAgent = computed(() => agents.value.find((a) => a.id === currentAgentId.value) ?? null)
const bucketKey = computed(() => currentSessionId.value ?? NEW_BUCKET)
const turns = computed<ChatTurn[]>(() => turnsBySession[bucketKey.value] ?? [])

function avatarText(name: string): string {
  return (name?.[0] ?? 'A').toUpperCase()
}

async function scrollBottom() {
  await nextTick()
  if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight
}

// ───── 加载 ─────
async function loadAgents(selectId?: string) {
  loadingAgents.value = true
  try {
    agents.value = await listAgents()
    if (agents.value.length) {
      const target = selectId && agents.value.some((a) => a.id === selectId) ? selectId : agents.value[0].id
      await selectAgent(target)
    } else {
      currentAgentId.value = null
      sessions.value = []
      currentSessionId.value = null
    }
  } catch (e: any) {
    ElMessage.error(e?.message ?? '加载 Agent 失败')
  } finally {
    loadingAgents.value = false
  }
}

async function selectAgent(id: string) {
  if (sending.value) return
  currentAgentId.value = id
  try {
    sessions.value = await listSessions(id)
    currentSessionId.value = sessions.value.length ? sessions.value[0].id : null
  } catch (e: any) {
    ElMessage.error(e?.message ?? '加载会话失败')
    sessions.value = []
    currentSessionId.value = null
  }
  // 加载该 Agent 之前的对话历史（仅首次进入时拉，已有内存对话则不覆盖）
  const sid = currentSessionId.value
  if (sid && !turnsBySession[sid]) {
    try {
      const msgs = await listAgentMessages(id)
      turnsBySession[sid] = msgs.map((m, i) => ({ id: i + 1, role: m.role, content: m.content }))
      scrollBottom()
    } catch {
      /* 历史加载失败不阻塞当前会话 */
    }
  }
}

// ───── 对话（后端同步返回完整回复）─────
async function ask(prompt: string) {
  const agentId = currentAgentId.value
  if (sending.value || !prompt.trim() || !agentId) return

  const key = bucketKey.value
  // 经响应式代理（reactive 对象的 getter）取数组，push / 下标赋值才会触发视图更新
  if (!turnsBySession[key]) turnsBySession[key] = []
  const bucket = turnsBySession[key]
  const baseId = Date.now()
  bucket.push({ id: baseId, role: 'user', content: prompt })
  bucket.push({ id: baseId + 1, role: 'assistant', content: '' })
  const aiIndex = bucket.length - 1
  draft.value = ''
  sending.value = true
  scrollBottom()

  try {
    const res = await chat(agentId, prompt, currentSessionId.value ?? undefined)
    bucket[aiIndex].content = res.reply || '（无回复）'
    // 首次对话：后端自动建了默认会话，把临时桶迁移到真实 sessionId
    if (currentSessionId.value == null && res.sessionId != null) {
      turnsBySession[res.sessionId] = bucket
      delete turnsBySession[NEW_BUCKET]
      currentSessionId.value = res.sessionId
      listSessions(agentId).then((s) => (sessions.value = s)).catch(() => {})
    }
  } catch (e: any) {
    bucket[aiIndex].content = `⚠ 出错了：${e?.message ?? '未知错误'}`
    ElMessage.error('AI 请求失败')
  } finally {
    sending.value = false
    scrollBottom()
    // 发送完成（textarea 因 disabled 失焦）后重新聚焦，方便连续输入
    await nextTick()
    composerEl.value?.focus()
  }
}

function onSubmit() {
  ask(draft.value.trim())
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    onSubmit()
  }
}

// ───── Agent 增改删 ─────
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const form = reactive({ id: '', name: '', persona: '', avatarUrl: '', modelName: 'deepseek-v4-flash' })
const saving = ref(false)

function openCreate() {
  dialogMode.value = 'create'
  Object.assign(form, { id: '', name: '', persona: '', avatarUrl: '', modelName: 'deepseek-v4-flash' })
  dialogVisible.value = true
}

function openEdit(a: Agent) {
  dialogMode.value = 'edit'
  Object.assign(form, {
    id: a.id,
    name: a.name,
    persona: a.persona ?? '',
    avatarUrl: a.avatarUrl ?? '',
    modelName: a.modelName
  })
  dialogVisible.value = true
}

async function saveAgent() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写 Agent 名称')
    return
  }
  saving.value = true
  try {
    if (dialogMode.value === 'create') {
      const a = await createAgent({
        name: form.name.trim(),
        persona: form.persona.trim() || undefined,
        avatarUrl: form.avatarUrl.trim() || undefined,
        modelName: form.modelName.trim() || undefined
      })
      ElMessage.success('已创建')
      dialogVisible.value = false
      await loadAgents(a.id)
    } else {
      const a = await updateAgent(form.id, {
        name: form.name.trim(),
        persona: form.persona.trim(),
        avatarUrl: form.avatarUrl.trim()
      })
      const idx = agents.value.findIndex((x) => x.id === a.id)
      if (idx >= 0) agents.value[idx] = a
      ElMessage.success('已保存')
      dialogVisible.value = false
    }
  } catch (e: any) {
    ElMessage.error(e?.message ?? '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeAgent(a: Agent) {
  const ok = await confirm({
    title: '删除确认',
    message: `删除 Agent「${a.name}」？其对话线程会一并删除，但与其它 Agent 共享的长期记忆仍保留。`,
    confirmText: '删除',
    danger: true
  })
  if (!ok) return
  try {
    await deleteAgent(a.id)
    ElMessage.success('已删除')
    const nextId = agents.value.find((x) => x.id !== a.id)?.id
    await loadAgents(nextId)
  } catch (e: any) {
    ElMessage.error(e?.message ?? '删除失败')
  }
}

onMounted(() => loadAgents())
</script>

<template>
  <div class="ai">
    <!-- 左：Agent 列表 -->
    <aside class="rail">
      <header class="rail-head">
        <h2>我的 Agent</h2>
        <button class="round-btn" title="新建 Agent" @click="openCreate">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" /></svg>
        </button>
      </header>

      <div class="rail-body">
        <p v-if="loadingAgents" class="rail-hint">加载中…</p>
        <p v-else-if="!agents.length" class="rail-hint">还没有 Agent<br />点右上角 ＋ 创建一个</p>
        <button
          v-for="a in agents"
          :key="a.id"
          :class="['agent-item', { active: a.id === currentAgentId }]"
          @click="selectAgent(a.id)"
        >
          <span class="a-avatar">
            <img v-if="a.avatarUrl" :src="a.avatarUrl" alt="" />
            <template v-else>{{ avatarText(a.name) }}</template>
          </span>
          <span class="a-meta">
            <span class="a-name">{{ a.name }}</span>
            <span class="a-sub">{{ a.persona || a.modelName }}</span>
          </span>
          <span class="a-ops" @click.stop>
            <i title="编辑" @click="openEdit(a)">
              <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" /><path d="M18.5 2.5a2.12 2.12 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5Z" /></svg>
            </i>
            <i title="删除" class="danger" @click="removeAgent(a)">
              <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6" /><path d="M19 6 l-1 14 a2 2 0 0 1 -2 2 H8 a2 2 0 0 1 -2 -2 L5 6" /></svg>
            </i>
          </span>
        </button>
      </div>
    </aside>

    <!-- 右：对话区 -->
    <section class="chat">
      <!-- 无 Agent -->
      <div v-if="!currentAgent" class="empty-stage">
        <img src="/logo.svg" alt="" width="56" height="56" />
        <h3>创建你的第一个 AI 伙伴</h3>
        <p>每个 Agent 像好友一样拥有长期记忆，同一账号下的多个 Agent 共享对你的了解</p>
        <button class="primary-btn" @click="openCreate">＋ 新建 Agent</button>
      </div>

      <template v-else>
        <header class="chat-head">
          <div class="title">
            <span class="h-avatar">
              <img v-if="currentAgent.avatarUrl" :src="currentAgent.avatarUrl" alt="" />
              <template v-else>{{ avatarText(currentAgent.name) }}</template>
            </span>
            <div>
              <h2>{{ currentAgent.name }}</h2>
              <p>{{ currentAgent.persona || '（未设定人格）' }} · {{ currentAgent.modelName }}</p>
            </div>
          </div>
        </header>

        <div ref="scroller" class="chat-body">
          <div v-if="!turns.length" class="welcome">
            <div class="hero">
              <img src="/logo.svg" alt="" width="48" height="48" />
              <h3>和 {{ currentAgent.name }} 聊点什么</h3>
              <p>它会记住你说过的重要事情</p>
            </div>
            <div class="presets">
              <button v-for="p in aiPresets" :key="p" class="preset" @click="ask(p)">{{ p }}</button>
            </div>
          </div>

          <div v-else class="turns">
            <div v-for="t in turns" :key="t.id" :class="['turn', t.role]">
              <span class="role-tag">{{ t.role === 'user' ? '我' : currentAgent.name }}</span>
              <div class="bubble">
                <template v-if="t.content">
                  <MarkdownText v-if="t.role === 'assistant'" :content="t.content" />
                  <pre v-else class="user-text">{{ t.content }}</pre>
                </template>
                <span v-else class="typing"><i /><i /><i /></span>
              </div>
            </div>
          </div>
        </div>

        <footer class="composer">
          <div class="composer-inner">
            <textarea
              ref="composerEl"
              v-model="draft"
              rows="1"
              placeholder="说点什么，Enter 发送 · Shift+Enter 换行"
              :disabled="sending"
              @keydown="onKeydown"
            />
            <button class="send-btn" :disabled="!draft.trim() || sending" @click="onSubmit">
              <svg v-if="!sending" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13" /><polygon points="22 2 15 22 11 13 2 9 22 2" /></svg>
              <span v-else class="typing"><i /><i /><i /></span>
              <span>{{ sending ? '思考中…' : '发送' }}</span>
            </button>
          </div>
        </footer>
      </template>
    </section>

    <!-- 新建 / 编辑 Agent -->
    <NookModal
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建 Agent' : '编辑 Agent'"
      :width="460"
    >
      <div class="nk-field">
        <label class="nk-label">名称<span class="req">*</span></label>
        <input v-model="form.name" class="nk-input" maxlength="64" placeholder="例如：小柚" />
      </div>
      <div class="nk-field">
        <label class="nk-label">人格设定</label>
        <textarea
          v-model="form.persona"
          class="nk-textarea"
          rows="3"
          placeholder="例如：你是一个元气满满、爱用颜文字的桌面助手"
        />
        <p class="nk-hint">注入系统提示，决定 Agent 的语气与性格</p>
      </div>
      <div class="nk-field">
        <label class="nk-label">头像 URL（可选）</label>
        <input v-model="form.avatarUrl" class="nk-input" maxlength="512" placeholder="https://..." />
      </div>
      <div v-if="dialogMode === 'create'" class="nk-field">
        <label class="nk-label">模型</label>
        <input v-model="form.modelName" class="nk-input" maxlength="64" placeholder="deepseek-v4-flash" />
      </div>

      <template #footer>
        <button class="nk-btn nk-btn--ghost" type="button" @click="dialogVisible = false">取消</button>
        <button class="nk-btn nk-btn--primary" type="button" :disabled="saving" @click="saveAgent">
          {{ saving ? '保存中…' : dialogMode === 'create' ? '创建' : '保存' }}
        </button>
      </template>
    </NookModal>
  </div>
</template>

<style scoped>
.ai {
  display: grid;
  grid-template-columns: 260px 1fr;
  height: 100vh;
  height: 100dvh;
  min-height: 0;
}

/* ───── 左侧 Agent rail ───── */
.rail {
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
  min-height: 0;
}
.rail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 12px;
  border-bottom: 1px solid var(--nook-surface-border);
}
.rail-head h2 {
  margin: 0;
  font-family: var(--nook-font-display);
  font-size: 16px;
  font-weight: 700;
  color: var(--nook-text);
}
.round-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  border: none;
  background: var(--nook-gradient-brand);
  color: #fff;
  cursor: pointer;
  transition: filter 160ms ease;
}
.round-btn:hover { filter: brightness(1.08); }

.rail-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.rail-hint {
  margin: 28px 12px;
  text-align: center;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--nook-text-muted);
}
.agent-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border-radius: 12px;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: background 160ms ease, border-color 160ms ease;
}
.agent-item:hover { background: rgba(20, 184, 166, 0.08); }
.agent-item.active {
  background: linear-gradient(135deg, rgba(20, 184, 166, 0.18), rgba(251, 146, 60, 0.12));
  border-color: rgba(20, 184, 166, 0.3);
}
.a-avatar {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 11px;
  background: var(--nook-gradient-teal);
  color: #fff;
  font-family: var(--nook-font-display);
  font-weight: 700;
  font-size: 15px;
  overflow: hidden;
}
.a-avatar img { width: 100%; height: 100%; object-fit: cover; }
.a-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}
.a-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--nook-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.a-sub {
  font-size: 12px;
  color: var(--nook-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.a-ops {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 140ms ease;
}
.agent-item:hover .a-ops { opacity: 1; }
.a-ops i {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  color: var(--nook-text-muted);
  cursor: pointer;
}
.a-ops i:hover { background: rgba(0, 0, 0, 0.06); color: var(--nook-text); }
.a-ops i.danger:hover { color: #ef4444; }

/* ───── 右侧对话 ───── */
.chat {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.empty-stage {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px;
  text-align: center;
}
.empty-stage img { border-radius: 16px; box-shadow: 0 20px 40px -16px rgba(15, 118, 110, 0.45); margin-bottom: 4px; }
.empty-stage h3 {
  margin: 0;
  font-family: var(--nook-font-display);
  font-size: 22px;
  background: linear-gradient(135deg, #0f766e, #fb923c);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.empty-stage p { margin: 0; max-width: 380px; color: var(--nook-text-muted); font-size: 13.5px; line-height: 1.6; }
.primary-btn {
  margin-top: 8px;
  height: 40px;
  padding: 0 22px;
  border-radius: 12px;
  border: none;
  background: var(--nook-gradient-brand);
  color: #fff;
  font-family: var(--nook-font-display);
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
}
.primary-btn:hover { filter: brightness(1.06); }

.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 24px;
  border-bottom: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
}
.chat-head .title { display: flex; align-items: center; gap: 12px; min-width: 0; }
.h-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 11px;
  background: var(--nook-gradient-teal);
  color: #fff;
  font-family: var(--nook-font-display);
  font-weight: 700;
  overflow: hidden;
}
.h-avatar img { width: 100%; height: 100%; object-fit: cover; }
.chat-head h2 { margin: 0; font-size: 16px; font-weight: 700; color: var(--nook-text); }
.chat-head .title p {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--nook-text-muted);
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chat-body { flex: 1; overflow-y: auto; padding: 24px 24px 28px; min-height: 0; display: flex; flex-direction: column; }
.chat-body::-webkit-scrollbar { width: 6px; }
.chat-body::-webkit-scrollbar-thumb { background: rgba(20, 184, 166, 0.25); border-radius: 3px; }

/* 空状态在可用高度内垂直居中，不再顶在上方 */
.welcome { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 28px; min-height: 0; padding: 24px 16px; text-align: center; }
.hero { position: relative; }
/* logo 背后的柔光晕 */
.hero::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 10px;
  width: 160px;
  height: 160px;
  transform: translateX(-50%);
  background: radial-gradient(circle, rgba(20, 184, 166, 0.4), transparent 68%);
  filter: blur(18px);
  z-index: -1;
  pointer-events: none;
}
.hero img { position: relative; border-radius: 16px; box-shadow: 0 18px 40px -16px rgba(15, 118, 110, 0.5); margin-bottom: 14px; }
.hero h3 { margin: 0; font-family: var(--nook-font-display); font-size: 25px; font-weight: 700; letter-spacing: -0.02em; color: var(--nook-text); }
.hero p { margin: 8px 0 0; color: var(--nook-text-muted); font-size: 13.5px; }
.presets { display: grid; grid-template-columns: repeat(2, minmax(0, 280px)); gap: 12px; }
@media (max-width: 640px) { .presets { grid-template-columns: 1fr; } }
.preset {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 15px 16px;
  border-radius: var(--r-md);
  border: 1px solid var(--nook-surface-border);
  background: var(--nook-surface-raised);
  color: var(--nook-text);
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  backdrop-filter: blur(8px);
  transition: border-color var(--dur) var(--ease-out), transform var(--dur) var(--ease-out),
    box-shadow var(--dur) var(--ease-out), background var(--dur) var(--ease-out);
}
/* 前导箭头，hover 时滑入 */
.preset::before {
  content: '→';
  flex-shrink: 0;
  color: var(--nook-primary);
  font-weight: 700;
  opacity: 0;
  transform: translateX(-6px);
  transition: opacity var(--dur) var(--ease-out), transform var(--dur) var(--ease-out);
}
.preset:hover {
  border-color: var(--nook-primary);
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
  background: var(--nook-gradient-wash);
}
.preset:hover::before { opacity: 1; transform: none; }

.turns { display: flex; flex-direction: column; gap: 18px; width: 100%; max-width: 820px; margin: 0 auto; }
.turn { display: flex; flex-direction: column; gap: 6px; align-items: flex-start; animation: nook-rise var(--dur-slow) var(--ease-out) both; }
.turn.user { align-items: flex-end; }
.role-tag { font-family: var(--nook-font-display); font-size: 11.5px; letter-spacing: 0.04em; color: var(--nook-text-muted); padding: 0 4px; }
.bubble {
  padding: 13px 17px;
  border-radius: 18px 18px 18px 6px;
  max-width: 85%;
  border: 1px solid var(--nook-surface-border);
  background: var(--nook-surface-raised);
  box-shadow: var(--shadow-sm);
  backdrop-filter: blur(8px);
}
.turn.user .bubble {
  border-radius: 18px 18px 6px 18px;
  background: var(--nook-bubble-mine-bg);
  border-color: var(--nook-bubble-mine-border);
  color: var(--nook-bubble-mine-fg);
  box-shadow: var(--nook-bubble-mine-shadow);
}
.bubble pre { margin: 0; font-family: var(--nook-font-sans); font-size: 14px; line-height: 1.6; color: inherit; white-space: pre-wrap; word-break: break-word; }

.typing { display: inline-flex; gap: 4px; align-items: center; }
.typing i { width: 6px; height: 6px; border-radius: 50%; background: currentColor; animation: blink 1s infinite ease-in-out; }
.typing i:nth-child(2) { animation-delay: 0.15s; }
.typing i:nth-child(3) { animation-delay: 0.3s; }
@keyframes blink { 0%, 80%, 100% { opacity: 0.3; transform: scale(0.6); } 40% { opacity: 1; transform: scale(1); } }

.composer {
  padding: 12px 24px 18px;
  border-top: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
}
/* 输入栏内容约束到与对话同宽的居中阅读列，超宽屏不再拉满 */
.composer-inner {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  width: 100%;
  max-width: 820px;
  margin: 0 auto;
}
.composer textarea {
  flex: 1;
  resize: none;
  min-height: 44px;
  max-height: 200px;
  padding: 11px 14px;
  border-radius: 14px;
  border: 1px solid var(--nook-surface-border);
  background: rgba(255, 255, 255, 0.6);
  font: inherit;
  font-size: 14px;
  line-height: 1.5;
  color: var(--nook-text);
  outline: none;
}
html.dark .composer textarea { background: rgba(4, 47, 46, 0.5); }
.composer textarea:focus {
  border-color: var(--nook-primary);
  box-shadow: 0 0 0 4px rgba(20, 184, 166, 0.16);
}
.send-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 44px;
  padding: 0 18px;
  border-radius: var(--r-sm);
  border: none;
  background: var(--nook-gradient-brand);
  color: #fff;
  font-family: var(--nook-font-display);
  font-weight: 600;
  font-size: 14px;
  letter-spacing: 0.02em;
  cursor: pointer;
  box-shadow: 0 10px 22px -12px rgba(20, 184, 166, 0.7);
  transition: filter var(--dur) var(--ease-out), transform var(--dur-fast) var(--ease-out),
    box-shadow var(--dur) var(--ease-out);
}
.send-btn:hover:not(:disabled) { filter: brightness(1.06); transform: translateY(-1px); box-shadow: 0 16px 30px -12px rgba(20, 184, 166, 0.8); }
.send-btn:active:not(:disabled) { transform: translateY(0); }
.send-btn:disabled { opacity: 0.6; cursor: not-allowed; }

@media (max-width: 768px) {
  .ai { grid-template-columns: 200px 1fr; }
}
</style>
