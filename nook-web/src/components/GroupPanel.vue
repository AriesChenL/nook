<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listFriends, type Friend } from '@/api/user'
import {
  ROLE,
  listMembers,
  addMembers,
  removeMember,
  setMemberRole,
  leaveGroup,
  transferOwner,
  updateGroup,
  type Conversation,
  type Member
} from '@/api/im'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{ modelValue: boolean; conv: Conversation }>()
const emit = defineEmits<{
  'update:modelValue': [boolean]
  refresh: [] // 群名/成员变更，通知父重新加载会话
  left: [] // 已退群，父跳走
}>()

const auth = useAuthStore()
const meId = computed(() => auth.user?.userId ?? 0)

const members = ref<Member[]>([])
const loading = ref(false)
const view = ref<'members' | 'add'>('members')
const nameDraft = ref('')
const savingName = ref(false)

// 加成员视图
const friends = ref<Friend[]>([])
const friendsLoading = ref(false)
const picked = ref<Set<number>>(new Set())
const adding = ref(false)

const myRole = computed(() => props.conv.myRole ?? ROLE.MEMBER)
const canManage = computed(() => myRole.value >= ROLE.ADMIN)
const isOwner = computed(() => myRole.value === ROLE.OWNER)

const memberIdSet = computed(() => new Set(members.value.map((m) => m.userId)))
const addableFriends = computed(() => friends.value.filter((f) => !memberIdSet.value.has(f.userId)))

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    view.value = 'members'
    nameDraft.value = props.conv.name
    await loadMembers()
  }
)

async function loadMembers() {
  loading.value = true
  try {
    members.value = await listMembers(props.conv.id)
  } catch (e: any) {
    ElMessage.error(e?.message ?? '加载成员失败')
  } finally {
    loading.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}

function roleLabel(role: number): string {
  if (role === ROLE.OWNER) return '群主'
  if (role === ROLE.ADMIN) return '管理员'
  return ''
}

// 能否对某成员执行「移出」
function canKick(m: Member): boolean {
  if (!canManage.value) return false
  if (m.userId === meId.value) return false // 自己走退群
  if (m.role === ROLE.OWNER) return false
  if (myRole.value === ROLE.ADMIN && m.role === ROLE.ADMIN) return false // 管理员不能踢管理员
  return true
}

async function onSaveName() {
  const name = nameDraft.value.trim()
  if (!name || name === props.conv.name) return
  savingName.value = true
  try {
    await updateGroup(props.conv.id, { name })
    ElMessage.success('群名已更新')
    emit('refresh')
  } catch (e: any) {
    ElMessage.error(e?.message ?? '更新失败')
  } finally {
    savingName.value = false
  }
}

async function onKick(m: Member) {
  try {
    await ElMessageBox.confirm(`将「${m.nickname}」移出群聊？`, '移出成员', {
      type: 'warning',
      confirmButtonText: '移出',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await removeMember(props.conv.id, m.userId)
    members.value = members.value.filter((x) => x.userId !== m.userId)
    emit('refresh')
  } catch (e: any) {
    ElMessage.error(e?.message ?? '移出失败')
  }
}

async function onToggleAdmin(m: Member) {
  const next = m.role === ROLE.ADMIN ? ROLE.MEMBER : ROLE.ADMIN
  try {
    await setMemberRole(props.conv.id, m.userId, next)
    m.role = next
    members.value = [...members.value].sort((a, b) => b.role - a.role)
    ElMessage.success(next === ROLE.ADMIN ? '已设为管理员' : '已取消管理员')
  } catch (e: any) {
    ElMessage.error(e?.message ?? '操作失败')
  }
}

async function onTransfer(m: Member) {
  try {
    await ElMessageBox.confirm(`将群主转让给「${m.nickname}」？转让后你将变为普通成员。`, '转让群主', {
      type: 'warning',
      confirmButtonText: '转让',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await transferOwner(props.conv.id, m.userId)
    ElMessage.success('群主已转让')
    emit('refresh')
    await loadMembers()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '转让失败')
  }
}

async function onLeave() {
  if (isOwner.value) {
    ElMessage.warning('群主需先转让群主才能退群')
    return
  }
  try {
    await ElMessageBox.confirm('确定退出该群聊？', '退群', {
      type: 'warning',
      confirmButtonText: '退群',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await leaveGroup(props.conv.id)
    ElMessage.success('已退出群聊')
    emit('left')
  } catch (e: any) {
    ElMessage.error(e?.message ?? '退群失败')
  }
}

// ─── 加成员 ───
async function openAdd() {
  view.value = 'add'
  picked.value = new Set()
  if (!friends.value.length) {
    friendsLoading.value = true
    try {
      friends.value = await listFriends()
    } finally {
      friendsLoading.value = false
    }
  }
}

function togglePick(id: number) {
  const s = new Set(picked.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  picked.value = s
}

async function onConfirmAdd() {
  const ids = [...picked.value]
  if (!ids.length) return
  adding.value = true
  try {
    await addMembers(props.conv.id, ids)
    ElMessage.success(`已添加 ${ids.length} 位成员`)
    view.value = 'members'
    emit('refresh')
    await loadMembers()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '添加失败')
  } finally {
    adding.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="drawer">
      <div v-if="modelValue" class="scrim" @click.self="close">
        <aside class="drawer" role="dialog" aria-modal="true" aria-label="群设置">
          <header class="d-head">
            <button v-if="view === 'add'" class="back" type="button" aria-label="返回" @click="view = 'members'">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6" /></svg>
            </button>
            <h3>{{ view === 'add' ? '添加成员' : '群设置' }}</h3>
            <button class="x" type="button" aria-label="关闭" @click="close">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></svg>
            </button>
          </header>

          <!-- 成员/设置视图 -->
          <div v-if="view === 'members'" class="d-body">
            <!-- 群名 -->
            <section class="block">
              <label class="block-label">群名称</label>
              <div class="name-row">
                <input v-model="nameDraft" :disabled="!canManage" maxlength="30" />
                <button
                  v-if="canManage"
                  class="btn primary sm"
                  :disabled="savingName || !nameDraft.trim() || nameDraft.trim() === conv.name"
                  @click="onSaveName"
                >
                  保存
                </button>
              </div>
            </section>

            <!-- 成员列表 -->
            <section class="block">
              <div class="block-head">
                <label class="block-label">成员 {{ members.length }}</label>
                <button v-if="canManage" class="add-link" type="button" @click="openAdd">+ 加成员</button>
              </div>

              <el-skeleton v-if="loading" :rows="5" animated />
              <div v-else class="members">
                <div v-for="m in members" :key="m.userId" class="m-row">
                  <span class="avatar">{{ (m.nickname[0] ?? '?').toUpperCase() }}</span>
                  <div class="m-info">
                    <div class="m-name">
                      {{ m.nickname }}
                      <span v-if="m.userId === meId" class="me-tag">我</span>
                      <span v-if="roleLabel(m.role)" :class="['role', { owner: m.role === ROLE.OWNER }]">{{ roleLabel(m.role) }}</span>
                    </div>
                  </div>
                  <div class="m-acts">
                    <button v-if="isOwner && m.userId !== meId" class="op" type="button" @click="onToggleAdmin(m)">
                      {{ m.role === ROLE.ADMIN ? '取消管理' : '设管理' }}
                    </button>
                    <button v-if="isOwner && m.userId !== meId" class="op" type="button" @click="onTransfer(m)">转让</button>
                    <button v-if="canKick(m)" class="op danger" type="button" @click="onKick(m)">移出</button>
                  </div>
                </div>
              </div>
            </section>

            <!-- 退群 -->
            <section class="block">
              <button class="leave" type="button" @click="onLeave">退出群聊</button>
            </section>
          </div>

          <!-- 加成员视图 -->
          <div v-else class="d-body">
            <el-skeleton v-if="friendsLoading" :rows="6" animated />
            <div v-else-if="!addableFriends.length" class="empty">没有可添加的好友</div>
            <div v-else class="members">
              <button
                v-for="f in addableFriends"
                :key="f.userId"
                type="button"
                :class="['pick-row', { on: picked.has(f.userId) }]"
                @click="togglePick(f.userId)"
              >
                <span class="check" :class="{ on: picked.has(f.userId) }">
                  <svg v-if="picked.has(f.userId)" viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12" /></svg>
                </span>
                <span class="avatar">{{ (f.nickname[0] ?? '?').toUpperCase() }}</span>
                <span class="m-name">{{ f.nickname }}</span>
              </button>
            </div>
            <div class="add-foot">
              <button class="btn primary" :disabled="adding || !picked.size" @click="onConfirmAdd">
                {{ adding ? '添加中…' : `添加 (${picked.size})` }}
              </button>
            </div>
          </div>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.scrim {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  justify-content: flex-end;
  background: rgba(15, 23, 42, 0.35);
  backdrop-filter: blur(3px);
}
.drawer {
  display: flex;
  flex-direction: column;
  width: 360px;
  max-width: 88vw;
  height: 100%;
  border-left: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
  backdrop-filter: blur(20px) saturate(160%);
  -webkit-backdrop-filter: blur(20px) saturate(160%);
  box-shadow: -20px 0 50px -20px rgba(15, 118, 110, 0.4);
}
.d-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--nook-surface-border);
}
.d-head h3 {
  flex: 1;
  margin: 0;
  font-family: var(--nook-font-display);
  font-size: 16px;
  font-weight: 700;
  color: var(--nook-text);
}
.back,
.x {
  display: inline-flex;
  width: 30px;
  height: 30px;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  border: none;
  background: transparent;
  color: var(--nook-text-muted);
  cursor: pointer;
}
.back:hover,
.x:hover {
  background: rgba(20, 184, 166, 0.1);
  color: var(--nook-text);
}

.d-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 18px 24px;
}
.d-body::-webkit-scrollbar {
  width: 5px;
}
.d-body::-webkit-scrollbar-thumb {
  background: rgba(20, 184, 166, 0.25);
  border-radius: 3px;
}

.block + .block {
  margin-top: 22px;
}
.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.block-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--nook-text-muted);
  letter-spacing: 0.02em;
}
.add-link {
  border: none;
  background: transparent;
  color: var(--nook-primary-deep);
  font: inherit;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
}
html.dark .add-link {
  color: var(--nook-primary-soft);
}

.name-row {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
.name-row input {
  flex: 1;
  height: 38px;
  padding: 0 12px;
  border-radius: 10px;
  border: 1px solid var(--nook-surface-border);
  background: rgba(255, 255, 255, 0.6);
  font: inherit;
  font-size: 14px;
  color: var(--nook-text);
  outline: none;
}
html.dark .name-row input {
  background: rgba(4, 47, 46, 0.5);
}
.name-row input:focus {
  border-color: var(--nook-primary);
}
.name-row input:disabled {
  opacity: 0.7;
  cursor: default;
}

.members {
  margin-top: 8px;
}
.m-row,
.pick-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 8px 6px;
  border: none;
  border-radius: 10px;
  background: transparent;
  text-align: left;
  font: inherit;
}
.pick-row {
  cursor: pointer;
  transition: background 150ms ease;
}
.pick-row:hover {
  background: rgba(20, 184, 166, 0.08);
}
.avatar {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #14b8a6, #fb923c);
  color: #fff;
  font-weight: 700;
  font-family: var(--nook-font-display);
  font-size: 14px;
}
.m-info {
  flex: 1;
  min-width: 0;
}
.m-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 500;
  color: var(--nook-text);
}
.me-tag {
  font-size: 10.5px;
  font-weight: 600;
  color: var(--nook-text-muted);
  background: rgba(20, 184, 166, 0.1);
  padding: 1px 5px;
  border-radius: 5px;
}
.role {
  font-size: 10.5px;
  font-weight: 600;
  color: var(--nook-primary-deep);
  background: rgba(20, 184, 166, 0.12);
  padding: 1px 6px;
  border-radius: 5px;
}
.role.owner {
  color: #b45309;
  background: rgba(251, 146, 60, 0.16);
}
html.dark .role {
  color: var(--nook-primary-soft);
}

.m-acts {
  display: flex;
  flex-shrink: 0;
  gap: 4px;
}
.op {
  padding: 4px 8px;
  border-radius: 7px;
  border: 1px solid var(--nook-surface-border);
  background: transparent;
  color: var(--nook-text-muted);
  font: inherit;
  font-size: 11.5px;
  cursor: pointer;
  transition: background 150ms ease, color 150ms ease, border-color 150ms ease;
}
.op:hover {
  background: rgba(20, 184, 166, 0.1);
  color: var(--nook-primary-deep);
  border-color: var(--nook-primary);
}
.op.danger:hover {
  background: rgba(239, 68, 68, 0.08);
  color: #ef4444;
  border-color: #ef4444;
}

.leave {
  width: 100%;
  height: 40px;
  border-radius: 11px;
  border: 1px solid rgba(239, 68, 68, 0.3);
  background: transparent;
  color: #ef4444;
  font: inherit;
  font-size: 13.5px;
  font-weight: 600;
  cursor: pointer;
  transition: background 160ms ease;
}
.leave:hover {
  background: rgba(239, 68, 68, 0.08);
}

.check {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 1.8px solid var(--nook-surface-border);
  color: #fff;
  transition: background 150ms ease, border-color 150ms ease;
}
.check.on {
  background: var(--nook-primary);
  border-color: var(--nook-primary);
}

.empty {
  padding: 40px 0;
  text-align: center;
  color: var(--nook-text-muted);
  font-size: 13px;
}
.add-foot {
  margin-top: 14px;
}
.btn {
  height: 38px;
  padding: 0 16px;
  border-radius: 10px;
  border: none;
  font: inherit;
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
  transition: filter 180ms ease;
}
.btn.sm {
  height: 38px;
}
.btn.primary {
  width: 100%;
  background: linear-gradient(135deg, #14b8a6, #0f766e);
  color: #fff;
}
.name-row .btn.primary {
  width: auto;
}
.btn.primary:hover:not(:disabled) {
  filter: brightness(1.06);
}
.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.drawer-enter-active,
.drawer-leave-active {
  transition: opacity 220ms ease;
}
.drawer-enter-active .drawer,
.drawer-leave-active .drawer {
  transition: transform 240ms cubic-bezier(0.22, 1, 0.36, 1);
}
.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;
}
.drawer-enter-from .drawer,
.drawer-leave-to .drawer {
  transform: translateX(100%);
}
@media (prefers-reduced-motion: reduce) {
  .drawer-enter-active,
  .drawer-leave-active,
  .drawer-enter-active .drawer,
  .drawer-leave-active .drawer {
    transition: none;
  }
}
</style>
