// src/stores/notification.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '@/services/api'
import { realtime } from '@/services/realtime'
import type { Notification } from '@/types'

export const useNotificationStore = defineStore('notification', () => {
  const list = ref<Notification[]>([])
  const unreadCount = ref(0)
  let timer: ReturnType<typeof setInterval> | null = null
  let unsub: (() => void) | null = null

  const fetchUnread = async () => {
    try {
      const res = await api.unreadNotificationCount()
      unreadCount.value = res?.count ?? 0
    } catch {
      // 静默失败：通知是旁路功能，拉取失败不打扰用户
    }
  }

  const fetchList = async () => {
    try {
      list.value = await api.notifications()
    } catch {
      list.value = []
    }
  }

  const markRead = async (id: number) => {
    const item = list.value.find(n => n.id === id)
    if (item && !item.isRead) {
      item.isRead = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    }
    try {
      await api.markNotificationRead(id)
    } catch {
      // 失败时下一次拉取会自动校正
    }
  }

  const markAllRead = async () => {
    list.value.forEach(n => (n.isRead = true))
    unreadCount.value = 0
    try {
      await api.markAllNotificationsRead()
    } catch {
      /* 忽略 */
    }
  }

  /** 登录后启动：建立 WS 连接实时接收通知；HTTP 轮询降级为 5 分钟兜底。 */
  const start = () => {
    fetchUnread()
    // 实时推送：订阅本用户的私有通知队列
    realtime.connect()
    if (!unsub) {
      unsub = realtime.subscribe('/user/queue/notifications', (payload) => {
        const n = payload as Notification
        if (n && typeof n === 'object') {
          list.value = [n, ...list.value]
          unreadCount.value += 1
        } else {
          // 兜底：拿不到具体通知时至少刷新未读数
          fetchUnread()
        }
      })
    }
    // 兜底轮询，防止 WS 漏推/断连期间的偏差（低频）
    if (timer) return
    timer = setInterval(fetchUnread, 300000)
  }

  const stop = () => {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    if (unsub) {
      unsub()
      unsub = null
    }
    list.value = []
    unreadCount.value = 0
  }

  return { list, unreadCount, fetchUnread, fetchList, markRead, markAllRead, start, stop }
})
