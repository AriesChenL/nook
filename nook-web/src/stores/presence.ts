import { defineStore } from 'pinia'
import { ref } from 'vue'

// 好友在线状态：
//  - 进页面/重连时调 setSnapshot 拉一次「当前在线」初始快照（HTTP /im/presence/online）；
//  - 之后由 WS 推送的 {type:"presence", data:{userId, online}} 增量更新。
// 没有快照时，已在线的好友要等他下次重连才会被感知，会一直显示离线——这是修复前的 bug。
export const usePresenceStore = defineStore('presence', () => {
  const onlineIds = ref<Set<string>>(new Set())

  function setOnline(userId: string, online: boolean) {
    if (!userId) return
    const next = new Set(onlineIds.value)
    if (online) next.add(userId)
    else next.delete(userId)
    onlineIds.value = next
  }

  // 应用初始快照（来自 /im/presence/online）。与当前集合取并集而非整体替换，
  // 避免在「先订阅 WS、后到快照」的时间窗里，把刚到的上线事件覆盖掉。
  function setSnapshot(userIds: string[]) {
    const next = new Set(onlineIds.value)
    for (const id of userIds) if (id) next.add(id)
    onlineIds.value = next
  }

  function isOnline(userId?: string): boolean {
    return userId != null && onlineIds.value.has(userId)
  }

  function reset() {
    onlineIds.value = new Set()
  }

  return { onlineIds, setOnline, setSnapshot, isOnline, reset }
})
