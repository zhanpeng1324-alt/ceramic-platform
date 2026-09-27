<script setup lang="ts">
import { onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useNotificationStore } from '@/stores/notification'
import type { Notification } from '@/types'

const router = useRouter()
const notificationStore = useNotificationStore()

const list = computed(() => notificationStore.list)
const hasUnread = computed(() => notificationStore.unreadCount > 0)

const typeMeta: Record<string, { label: string; icon: string }> = {
  ORDER: { label: '订单', icon: '📦' },
  CUSTOMIZATION: { label: '定制', icon: '🏺' },
  RETURN: { label: '售后', icon: '↩️' },
  SYSTEM: { label: '系统', icon: '🔔' },
}

const meta = (t: string) => typeMeta[t] ?? typeMeta.SYSTEM ?? { label: '系统', icon: '🔔' }

const formatDate = (d?: string) => {
  if (!d) return ''
  const date = new Date(d.replace(' ', 'T'))
  if (isNaN(date.getTime())) return d
  return date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

const onClickItem = async (n: Notification) => {
  if (!n.isRead) await notificationStore.markRead(n.id)
  // 跳转到关联业务页
  if (n.bizType === 'ORDER') router.push('/orders')
  else if (n.bizType === 'CUSTOMIZATION') router.push('/my-customizations')
  else if (n.bizType === 'RETURN') router.push('/aftersales')
}

const onMarkAll = () => notificationStore.markAllRead()

onMounted(async () => {
  await notificationStore.fetchList()
  await notificationStore.fetchUnread()
})
</script>

<template>
  <main class="page notifications-page">
    <header class="page-header">
      <div>
        <h1 class="page-title">消息通知</h1>
        <p class="page-subtitle">订单、定制与售后的最新动态</p>
      </div>
      <button v-if="hasUnread" class="mark-all-btn" @click="onMarkAll">全部已读</button>
    </header>

    <div v-if="list.length === 0" class="empty">
      <div class="empty-icon">🔕</div>
      <p>暂无通知</p>
    </div>

    <ul v-else class="notif-list">
      <li
        v-for="n in list"
        :key="n.id"
        class="notif-item"
        :class="{ unread: !n.isRead }"
        @click="onClickItem(n)"
      >
        <span class="notif-icon">{{ meta(n.type).icon }}</span>
        <div class="notif-body">
          <div class="notif-top">
            <span class="notif-title">{{ n.title }}</span>
            <span class="notif-tag">{{ meta(n.type).label }}</span>
            <span v-if="!n.isRead" class="dot"></span>
          </div>
          <p class="notif-content">{{ n.content }}</p>
          <span class="notif-time">{{ formatDate(n.createdAt) }}</span>
        </div>
      </li>
    </ul>
  </main>
</template>

<style scoped>
.notifications-page {
  max-width: 780px;
  margin: 0 auto;
  padding: 1.5rem 1rem;
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 1.25rem;
}

.page-title {
  margin: 0;
  font-size: 1.5rem;
  color: var(--c-text, #2b2b2b);
}

.page-subtitle {
  margin: 0.25rem 0 0;
  color: var(--c-text-muted, #7a7a7a);
  font-size: 0.9rem;
}

.mark-all-btn {
  background: none;
  border: 1px solid var(--c-primary, #2e8b6f);
  color: var(--c-primary, #2e8b6f);
  border-radius: 8px;
  padding: 0.4rem 0.9rem;
  cursor: pointer;
  font-size: 0.88rem;
}

.mark-all-btn:hover {
  background: var(--c-primary, #2e8b6f);
  color: #fff;
}

.empty {
  text-align: center;
  padding: 4rem 0;
  color: #999;
}

.empty-icon {
  font-size: 2.5rem;
  margin-bottom: 0.5rem;
}

.notif-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.notif-item {
  display: flex;
  gap: 0.85rem;
  padding: 1rem 1.1rem;
  background: var(--c-surface, #fff);
  border: 1px solid var(--c-border, #e7e3dc);
  border-radius: var(--radius, 12px);
  cursor: pointer;
  transition: box-shadow 0.15s, transform 0.15s;
}

.notif-item:hover {
  box-shadow: var(--shadow, 0 4px 16px rgba(0, 0, 0, 0.06));
  transform: translateY(-1px);
}

.notif-item.unread {
  background: #f6fbf8;
  border-color: #cfe9dd;
}

.notif-icon {
  font-size: 1.4rem;
  line-height: 1.6;
}

.notif-body {
  flex: 1;
  min-width: 0;
}

.notif-top {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.notif-title {
  font-weight: 600;
  color: var(--c-text, #2b2b2b);
}

.notif-tag {
  font-size: 0.72rem;
  color: var(--c-primary-dark, #256f59);
  background: #eaf5f0;
  border-radius: 6px;
  padding: 0.05rem 0.4rem;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #e74c3c;
}

.notif-content {
  margin: 0.35rem 0 0.4rem;
  color: #555;
  font-size: 0.9rem;
  line-height: 1.5;
}

.notif-time {
  font-size: 0.78rem;
  color: #aaa;
}
</style>
