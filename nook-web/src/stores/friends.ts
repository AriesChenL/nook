import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listFriendRequests, listFriends, type Friend } from '@/api/user'

// 全局「待处理好友申请」计数：供侧栏「联系人」角标使用，
// 由 AppLayout 全局轮询刷新，使 B 在任何页面都能感知到新申请。
export const useFriendStore = defineStore('friends', () => {
  const pendingCount = ref(0)

  // 好友备注表（userId → 备注）：联系人页编辑后写入，聊天列表 / 聊天室复用，
  // 使备注像微信一样在「能看到这个好友的任何地方」生效。
  const remarks = ref<Record<string, string>>({})
  let remarksLoaded = false

  async function refreshPending() {
    try {
      const rs = await listFriendRequests()
      pendingCount.value = rs.filter((r) => r.status === 0).length
    } catch {
      /* 静默：轮询失败不打扰用户 */
    }
  }

  // 用已拉取到的好友列表覆盖备注表（联系人页拉完好友后调用，避免重复请求）
  function syncRemarks(friends: Pick<Friend, 'userId' | 'remark'>[]) {
    const map: Record<string, string> = {}
    for (const f of friends) if (f.remark) map[f.userId] = f.remark
    remarks.value = map
    remarksLoaded = true
  }

  // 懒加载备注表：聊天页等自身不拉好友的视图调用，首次拉一次后缓存
  async function loadRemarks(force = false) {
    if (remarksLoaded && !force) return
    try {
      syncRemarks(await listFriends())
    } catch {
      /* 静默：失败不影响聊天主流程 */
    }
  }

  // 单条更新（编辑备注成功后即时生效，无需整表重拉）
  function setRemark(userId: string, remark: string) {
    const next = { ...remarks.value }
    if (remark) next[userId] = remark
    else delete next[userId]
    remarks.value = next
  }

  // 取某好友的备注（无则返回 undefined，由调用方回退到昵称）
  function remarkFor(userId?: string): string | undefined {
    return userId ? remarks.value[userId] : undefined
  }

  return { pendingCount, refreshPending, remarks, loadRemarks, syncRemarks, setRemark, remarkFor }
})
