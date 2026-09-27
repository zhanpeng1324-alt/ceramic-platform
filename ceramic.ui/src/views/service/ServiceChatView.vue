<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { api } from '@/services/api'
import { realtime } from '@/services/realtime'
import { useUserStore } from '@/stores/user'
import type { ChatConversation, ChatMessage, Order, Customization, User, LogisticsTrace } from '@/types'
import { QUICK_REPLIES, EMOJIS } from '@/constants/quickReplies'
import ServiceNavbar from '@/components/service/ServiceNavbar.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const conversations = ref<ChatConversation[]>([])
const messages = ref<ChatMessage[]>([])
const selectedConversationId = ref<number | null>(null)
const newMessage = ref('')
const loading = ref(true)
const showTransferredOnly = ref(false)

// —— 左栏：搜索 + 页签 ——
const searchQuery = ref('')
type ListTab = 'receiving' | 'pending' | 'replied' | 'all'
const activeTab = ref<ListTab>('all')

// —— 中栏：工具栏面板 ——
const showEmoji = ref(false)
const showQuickReplies = ref(false)

// —— 右栏：客户 / 订单上下文 ——
const customerProfile = ref<User | null>(null)
const customerOrders = ref<Order[]>([])
const customerCustomizations = ref<Customization[]>([])
const loadingContext = ref(false)
const expandedLogistics = ref<number | null>(null)
const logisticsCache = ref<Record<number, LogisticsTrace[]>>({})

const selectedConversation = computed(() =>
  conversations.value.find(c => c.id === selectedConversationId.value)
)

const formatDate = (dateStr: string | undefined): string => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

const formatDay = (dateStr: string | undefined): string => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleDateString('zh-CN')
}

const getStatusLabel = (status: string) => {
  const labels: Record<string, string> = {
    open: '进行中',
    active: '进行中',
    pending_human: '待人工处理',
    resolved: '已解决',
    closed: '已关闭',
    archived: '已归档'
  }
  return labels[status] || status
}

// —— 左栏分组：页签 + 搜索 + AI 转交过滤 ——
const isReceiving = (c: ChatConversation) =>
  ['active', 'open', 'pending_human'].includes(c.status)

const filteredConversations = computed(() => {
  const kw = searchQuery.value.trim().toLowerCase()
  return conversations.value.filter(c => {
    if (kw && !(c.customerName || '').toLowerCase().includes(kw)) return false
    if (activeTab.value === 'receiving' && !isReceiving(c)) return false
    if (activeTab.value === 'pending' && !(c.unreadCount > 0)) return false
    if (activeTab.value === 'replied' && (isReceiving(c) || c.unreadCount > 0)) return false
    return true
  })
})

const tabCount = (tab: ListTab) => {
  if (tab === 'all') return conversations.value.length
  if (tab === 'receiving') return conversations.value.filter(isReceiving).length
  if (tab === 'pending') return conversations.value.filter(c => c.unreadCount > 0).length
  return conversations.value.filter(c => !isReceiving(c) && !(c.unreadCount > 0)).length
}

const selectConversation = async (conversationId: number) => {
  selectedConversationId.value = conversationId
  showEmoji.value = false
  showQuickReplies.value = false
  api.markAsRead(conversationId).catch(err => console.error('标记已读失败:', err))
  try {
    messages.value = await api.chatMessages(conversationId)
  } catch (err) {
    console.error('加载消息失败:', err)
  }
  subscribeConversation(conversationId)
  const conv = conversations.value.find(c => c.id === conversationId)
  if (conv?.customerId) loadContext(conv.customerId)
}

// —— 右栏：加载客户资料 + 订单 + 定制 ——
const loadContext = async (customerId: number) => {
  loadingContext.value = true
  customerProfile.value = null
  customerOrders.value = []
  customerCustomizations.value = []
  expandedLogistics.value = null
  try {
    const [profile, orders, customs] = await Promise.all([
      api.user(customerId).catch(() => null),
      api.serviceUserOrders(customerId).catch(() => [] as Order[]),
      api.customizations(customerId).catch(() => [] as Customization[]),
    ])
    customerProfile.value = profile
    customerOrders.value = orders
    customerCustomizations.value = customs
  } finally {
    loadingContext.value = false
  }
}

// —— WebSocket 实时接收 ——
let chatUnsub: (() => void) | null = null
let inboxUnsub: (() => void) | null = null

const subscribeConversation = (conversationId: number) => {
  if (chatUnsub) {
    chatUnsub()
    chatUnsub = null
  }
  chatUnsub = realtime.subscribe(`/topic/conversation/${conversationId}`, (payload) => {
    const msg = payload as ChatMessage
    if (!msg || !msg.id) return
    // 忽略自己刚发的消息（发送后本地会重新拉取）
    const myId = userStore.currentUser?.id
    if ((msg.senderRole === 'service' || msg.senderRole === 'admin')
        && msg.senderId === myId) return
    if (msg.conversationId !== selectedConversationId.value) return
    if (messages.value.some(m => m.id === msg.id)) return
    messages.value.push(msg)
    api.markAsRead(msg.conversationId).catch(() => {})
  })
}

const sendMessage = async () => {
  if (!newMessage.value.trim() || !selectedConversationId.value) return

  try {
    await api.sendChatMessage({
      conversationId: selectedConversationId.value,
      message: newMessage.value.trim()
    })
    newMessage.value = ''
    showEmoji.value = false
    showQuickReplies.value = false
    await selectConversation(selectedConversationId.value)
    await loadConversations()
  } catch (err) {
    console.error('发送消息失败:', err)
  }
}

// —— 工具栏：表情 / 快捷短语 ——
const insertEmoji = (e: string) => {
  newMessage.value += e
  showEmoji.value = false
}

const applyQuickReply = (text: string) => {
  newMessage.value = text
  showQuickReplies.value = false
}

const handleMarkResolved = async () => {
  if (!selectedConversationId.value) return
  if (!confirm('确定要标记此对话为已解决吗？')) return

  try {
    await api.updateConversationStatus(selectedConversationId.value, 'resolved')
    await loadConversations()
    await selectConversation(selectedConversationId.value)
  } catch (err) {
    console.error('操作失败:', err)
  }
}

const handleMarkActive = async () => {
  if (!selectedConversationId.value) return

  try {
    await api.updateConversationStatus(selectedConversationId.value, 'active')
    await loadConversations()
    await selectConversation(selectedConversationId.value)
  } catch (err) {
    console.error('操作失败:', err)
  }
}

// —— 右栏订单操作：复制单号 / 查看物流 / 去处理 ——
const orderStatusLabel = (status: string) => {
  const labels: Record<string, string> = {
    PENDING_PAY: '待支付', paid: '已支付', PAID: '已支付',
    shipped: '已发货', SHIPPED: '已发货', delivered: '已送达',
    COMPLETED: '已完成', cancelled: '已取消', CANCELLED: '已取消',
  }
  return labels[status] || status
}

const copyText = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    // 忽略无剪贴板权限的环境
  }
}

const toggleLogistics = async (orderId: number) => {
  if (expandedLogistics.value === orderId) {
    expandedLogistics.value = null
    return
  }
  expandedLogistics.value = orderId
  if (!logisticsCache.value[orderId]) {
    try {
      logisticsCache.value[orderId] = await api.orderLogistics(orderId)
    } catch {
      logisticsCache.value[orderId] = []
    }
  }
}

const gotoOrders = () => router.push({ path: '/service/orders' })
const gotoReturns = () => router.push({ path: '/service/returns' })

const loadConversations = async () => {
  try {
    conversations.value = await api.serviceChatConversations(showTransferredOnly.value)
    if (route.params.id && !selectedConversationId.value) {
      selectConversation(parseInt(route.params.id as string))
    }
  } catch (err) {
    console.error('加载对话失败:', err)
  } finally {
    loading.value = false
  }
}

const toggleTransferredFilter = async () => {
  showTransferredOnly.value = !showTransferredOnly.value
  loading.value = true
  await loadConversations()
}

watch(() => route.params.id, (newId) => {
  if (newId) {
    selectConversation(parseInt(newId as string))
  }
})

onMounted(() => {
  realtime.connect()
  // 客服端广播频道：有新消息/新会话/转人工时刷新会话列表
  inboxUnsub = realtime.subscribe('/topic/service.inbox', () => {
    loadConversations()
  })
  loadConversations()
})

onUnmounted(() => {
  if (chatUnsub) { chatUnsub(); chatUnsub = null }
  if (inboxUnsub) { inboxUnsub(); inboxUnsub = null }
})
</script>

<template>
  <div class="service-chat">
    <ServiceNavbar />

    <div class="workstation">
      <!-- ========== 左栏：会话列表 ========== -->
      <aside class="col-list">
        <div class="list-search">
          <input v-model="searchQuery" type="text" placeholder="🔍 搜索客户昵称…" />
        </div>

        <div class="list-tabs">
          <button class="tab" :class="{ active: activeTab === 'all' }" @click="activeTab = 'all'">
            全部<span class="tab-num">{{ tabCount('all') }}</span>
          </button>
          <button class="tab" :class="{ active: activeTab === 'receiving' }" @click="activeTab = 'receiving'">
            正在接待<span class="tab-num">{{ tabCount('receiving') }}</span>
          </button>
          <button class="tab" :class="{ active: activeTab === 'pending' }" @click="activeTab = 'pending'">
            待回复<span class="tab-num alert" v-if="tabCount('pending')">{{ tabCount('pending') }}</span>
          </button>
          <button class="tab" :class="{ active: activeTab === 'replied' }" @click="activeTab = 'replied'">
            已回复<span class="tab-num">{{ tabCount('replied') }}</span>
          </button>
        </div>

        <div class="list-subfilter">
          <button class="chip" :class="{ active: !showTransferredOnly }"
            @click="showTransferredOnly && toggleTransferredFilter()">全部来源</button>
          <button class="chip" :class="{ active: showTransferredOnly }"
            @click="!showTransferredOnly && toggleTransferredFilter()">AI 已转交</button>
        </div>

        <div class="list-body">
          <div v-if="filteredConversations.length === 0" class="empty">暂无会话</div>
          <div
            v-for="conv in filteredConversations"
            :key="conv.id"
            class="conv-item"
            :class="{ active: selectedConversationId === conv.id }"
            @click="selectConversation(conv.id!)"
          >
            <div class="conv-avatar">
              👤
              <span v-if="conv.unreadCount > 0" class="unread-dot">{{ conv.unreadCount }}</span>
            </div>
            <div class="conv-main">
              <div class="conv-top">
                <span class="conv-name">{{ conv.customerName || '匿名用户' }}</span>
                <span class="conv-time">{{ formatDate(conv.lastMessageTime || conv.createdAt).slice(5, 16) }}</span>
              </div>
              <div class="conv-preview">
                <span v-if="conv.transferredToHuman" class="transfer-tag">AI转交</span>
                {{ conv.aiSummary || conv.lastMessage || '暂无摘要' }}
              </div>
            </div>
          </div>
        </div>
      </aside>

      <!-- ========== 中栏：聊天 ========== -->
      <section class="col-chat">
        <div v-if="!selectedConversationId" class="no-selection">
          <div class="ns-icon">💬</div>
          <p>请从左侧选择一个会话开始接待</p>
        </div>

        <template v-else>
          <div class="chat-header-bar">
            <div class="chat-title-area">
              <div class="chat-title">{{ selectedConversation?.customerName || '客户' }}</div>
              <div class="chat-meta">
                <span class="conv-status-dot" :class="selectedConversation?.status"></span>
                {{ getStatusLabel(selectedConversation?.status || '') }}
                <span v-if="selectedConversation?.transferredToHuman" class="human-badge">AI 已转交 · 待人工处理</span>
              </div>
            </div>
            <div class="chat-actions">
              <button v-if="selectedConversation?.status !== 'resolved'" class="btn btn-resolved" @click="handleMarkResolved">标记已解决</button>
              <button v-if="selectedConversation?.status === 'resolved'" class="btn btn-active" @click="handleMarkActive">重新打开</button>
            </div>
          </div>

          <div v-if="selectedConversation?.aiSummary" class="ai-summary-panel">
            <div class="summary-title">✨ AI 对话摘要</div>
            <div class="summary-content">{{ selectedConversation.aiSummary }}</div>
          </div>

          <div class="message-list">
            <div
              v-for="msg in messages"
              :key="msg.id"
              class="message-item"
              :class="{
                service: msg.senderRole === 'service' || msg.senderRole === 'admin',
                system: msg.senderRole === 'system',
                ai: msg.senderRole === 'ai'
              }"
            >
              <div v-if="msg.senderRole === 'system'" class="system-msg">{{ msg.message }}</div>
              <template v-else>
                <div class="message-avatar">
                  {{ (msg.senderRole === 'service' || msg.senderRole === 'admin') ? '🎧' : msg.senderRole === 'ai' ? '✨' : '👤' }}
                </div>
                <div class="message-content">
                  <div class="message-sender">
                    {{ (msg.senderRole === 'service' || msg.senderRole === 'admin') ? '人工客服' : msg.senderRole === 'ai' ? 'AI 助理' : '用户' }}
                  </div>
                  <div class="message-text">{{ msg.message }}</div>
                  <div class="message-time">{{ formatDate(msg.createdAt || msg.timestamp) }}</div>
                </div>
              </template>
            </div>
            <div v-if="messages.length === 0" class="empty-messages">暂无消息</div>
          </div>

          <!-- 工具栏 + 输入 -->
          <div class="composer">
            <div class="composer-toolbar">
              <button class="tool-btn" :class="{ on: showEmoji }" title="表情"
                @click="showEmoji = !showEmoji; showQuickReplies = false">😊</button>
              <button class="tool-btn" :class="{ on: showQuickReplies }" title="快捷短语"
                @click="showQuickReplies = !showQuickReplies; showEmoji = false">⚡ 快捷短语</button>
            </div>

            <div v-if="showEmoji" class="emoji-pop">
              <button v-for="e in EMOJIS" :key="e" class="emoji" @click="insertEmoji(e)">{{ e }}</button>
            </div>
            <div v-if="showQuickReplies" class="qr-pop">
              <button v-for="qr in QUICK_REPLIES" :key="qr.label" class="qr-item" @click="applyQuickReply(qr.text)">
                <span class="qr-label">{{ qr.label }}</span>
                <span class="qr-text">{{ qr.text }}</span>
              </button>
            </div>

            <div class="composer-input">
              <textarea
                v-model="newMessage"
                rows="2"
                placeholder="输入回复内容…（Enter 发送，Shift+Enter 换行）"
                @keydown.enter.exact.prevent="sendMessage"
              ></textarea>
              <button class="btn btn-send" @click="sendMessage">发送</button>
            </div>
          </div>
        </template>
      </section>

      <!-- ========== 右栏：客户 / 订单上下文 ========== -->
      <aside class="col-context">
        <div v-if="!selectedConversationId" class="ctx-empty">
          <p>选中会话后<br>这里显示客户资料与订单</p>
        </div>

        <template v-else>
          <div class="ctx-section customer-card">
            <div class="cust-avatar">{{ (customerProfile?.nickname || selectedConversation?.customerName || '客')[0] }}</div>
            <div class="cust-info">
              <div class="cust-name">{{ customerProfile?.nickname || selectedConversation?.customerName || '客户' }}</div>
              <div class="cust-sub">{{ customerProfile?.phone || '手机未登记' }}</div>
              <div class="cust-sub" v-if="customerProfile?.createdAt">注册于 {{ formatDay(customerProfile.createdAt) }}</div>
            </div>
          </div>

          <div class="ctx-section">
            <div class="ctx-head">
              <span>近期订单</span>
              <span class="ctx-count">{{ customerOrders.length }}</span>
            </div>
            <div v-if="loadingContext" class="ctx-loading">加载中…</div>
            <div v-else-if="customerOrders.length === 0" class="ctx-empty-sm">暂无订单</div>
            <div v-else class="order-card" v-for="o in customerOrders" :key="o.id">
              <div class="order-row1">
                <span class="order-no">{{ o.orderNo || ('#' + o.id) }}</span>
                <span class="order-status" :class="o.status">{{ orderStatusLabel(o.status) }}</span>
              </div>
              <div class="order-row2">
                <span>¥{{ o.totalAmount }}</span>
                <span class="order-date">{{ formatDay(o.createdAt) }}</span>
              </div>
              <div class="order-recv" v-if="o.receiverName">{{ o.receiverName }} · {{ o.receiverPhone }}</div>
              <div class="order-actions">
                <button class="mini-btn" @click="copyText(o.orderNo || String(o.id))">复制单号</button>
                <button class="mini-btn" v-if="o.trackingNo" @click="toggleLogistics(o.id)">查看物流</button>
                <button class="mini-btn" @click="gotoOrders">去处理</button>
              </div>
              <div v-if="expandedLogistics === o.id" class="logistics-box">
                <div v-if="(logisticsCache[o.id] || []).length === 0" class="logi-empty">暂无物流轨迹</div>
                <div v-for="(t, i) in logisticsCache[o.id]" :key="i" class="logi-item">
                  <span class="logi-time">{{ t.time }}</span>
                  <span class="logi-desc">{{ t.description || t.status }}</span>
                </div>
              </div>
            </div>
          </div>

          <div class="ctx-section" v-if="customerCustomizations.length">
            <div class="ctx-head">
              <span>定制单</span>
              <span class="ctx-count">{{ customerCustomizations.length }}</span>
            </div>
            <div class="custom-card" v-for="c in customerCustomizations" :key="c.id">
              <div class="order-row1">
                <span class="order-no">{{ c.productName || c.shape || '定制' }} ×{{ c.quantity }}</span>
                <span class="order-status">{{ c.status }}</span>
              </div>
              <div class="order-row2">
                <span>{{ c.glazeColor }} / {{ c.size }}</span>
                <span v-if="c.quotedPrice">报价 ¥{{ c.quotedPrice }}</span>
              </div>
            </div>
          </div>

          <div class="ctx-section ctx-links">
            <button class="link-btn" @click="gotoOrders">📋 订单查询</button>
            <button class="link-btn" @click="gotoReturns">📦 售后协助</button>
          </div>
        </template>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.service-chat {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--c-bg, #f7f5f1);
}

/* 三栏工作台 */
.workstation {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 300px 1fr 320px;
  gap: 10px;
  padding: 10px;
}

.col-list,
.col-chat,
.col-context {
  background: var(--c-surface, #fff);
  border: 1px solid var(--c-border, #e7e3dc);
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

/* ---------- 左栏 ---------- */
.list-search {
  padding: 10px;
  border-bottom: 1px solid var(--c-border, #e7e3dc);
}
.list-search input {
  width: 100%;
  box-sizing: border-box;
  padding: 8px 10px;
  border: 1px solid var(--c-border, #e7e3dc);
  border-radius: 6px;
  font-size: 0.85rem;
  background: var(--c-bg, #f7f5f1);
}
.list-tabs {
  display: flex;
  border-bottom: 1px solid var(--c-border, #e7e3dc);
}
.tab {
  flex: 1;
  padding: 9px 4px;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 0.78rem;
  color: var(--c-text-muted, #7a7a7a);
  border-bottom: 2px solid transparent;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.tab.active {
  color: var(--c-primary, #2e8b6f);
  border-bottom-color: var(--c-primary, #2e8b6f);
  font-weight: 600;
}
.tab-num {
  font-size: 0.68rem;
  background: #eee;
  color: #888;
  border-radius: 8px;
  padding: 0 5px;
  min-width: 14px;
}
.tab.active .tab-num {
  background: var(--c-primary-soft, #eaf5f0);
  color: var(--c-primary, #2e8b6f);
}
.tab-num.alert {
  background: var(--c-accent, #c1502e);
  color: #fff;
}
.list-subfilter {
  display: flex;
  gap: 6px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--c-border, #e7e3dc);
}
.chip {
  padding: 3px 10px;
  border: 1px solid var(--c-border, #e7e3dc);
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
  font-size: 0.72rem;
  color: var(--c-text-muted, #7a7a7a);
}
.chip.active {
  background: var(--c-primary, #2e8b6f);
  border-color: var(--c-primary, #2e8b6f);
  color: #fff;
}
.list-body {
  flex: 1;
  overflow-y: auto;
}
.empty {
  text-align: center;
  padding: 2rem 1rem;
  color: #aaa;
  font-size: 0.85rem;
}
.conv-item {
  display: flex;
  gap: 10px;
  padding: 11px 12px;
  cursor: pointer;
  border-bottom: 1px solid #f2efe9;
  transition: background 0.15s;
}
.conv-item:hover {
  background: var(--c-bg, #f7f5f1);
}
.conv-item.active {
  background: var(--c-primary-soft, #eaf5f0);
}
.conv-avatar {
  position: relative;
  font-size: 1.6rem;
  flex-shrink: 0;
}
.unread-dot {
  position: absolute;
  top: -4px;
  right: -6px;
  background: var(--c-accent, #c1502e);
  color: #fff;
  font-size: 0.62rem;
  min-width: 15px;
  height: 15px;
  line-height: 15px;
  text-align: center;
  border-radius: 8px;
  padding: 0 3px;
}
.conv-main {
  flex: 1;
  min-width: 0;
}
.conv-top {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.conv-name {
  font-weight: 600;
  color: var(--c-text, #2b2b2b);
  font-size: 0.88rem;
}
.conv-time {
  color: #b0aaa0;
  font-size: 0.68rem;
  flex-shrink: 0;
}
.conv-preview {
  font-size: 0.76rem;
  color: var(--c-text-muted, #7a7a7a);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 3px;
}
.transfer-tag {
  background: var(--c-accent, #c1502e);
  color: #fff;
  padding: 0 5px;
  border-radius: 3px;
  font-size: 0.62rem;
  margin-right: 3px;
}

/* ---------- 中栏 ---------- */
.no-selection {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #b0aaa0;
  gap: 10px;
}
.ns-icon {
  font-size: 2.5rem;
}
.chat-header-bar {
  padding: 12px 16px;
  border-bottom: 1px solid var(--c-border, #e7e3dc);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.chat-title {
  font-weight: 700;
  color: var(--c-text, #2b2b2b);
  font-size: 1rem;
}
.chat-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.75rem;
  color: var(--c-text-muted, #7a7a7a);
  margin-top: 3px;
}
.conv-status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #bbb;
  display: inline-block;
}
.conv-status-dot.active,
.conv-status-dot.open,
.conv-status-dot.pending_human {
  background: #f0a020;
}
.conv-status-dot.resolved {
  background: var(--c-primary, #2e8b6f);
}
.human-badge {
  background: var(--c-accent, #c1502e);
  color: #fff;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 0.68rem;
}
.btn {
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.82rem;
  padding: 6px 14px;
}
.btn-resolved {
  background: var(--c-primary, #2e8b6f);
  color: #fff;
}
.btn-active {
  background: #3a7bd5;
  color: #fff;
}
.ai-summary-panel {
  margin: 10px 16px 0;
  padding: 10px 12px;
  background: #fff8e6;
  border: 1px solid #ffe082;
  border-radius: 8px;
}
.summary-title {
  font-size: 0.78rem;
  font-weight: 700;
  color: #e65100;
  margin-bottom: 4px;
}
.summary-content {
  font-size: 0.82rem;
  color: #5d4037;
  line-height: 1.5;
}
.message-list {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.empty-messages {
  text-align: center;
  padding: 2rem;
  color: #b0aaa0;
}
.message-item {
  display: flex;
  gap: 10px;
}
.message-item.service {
  flex-direction: row-reverse;
}
.message-avatar {
  font-size: 1.5rem;
  flex-shrink: 0;
}
.message-content {
  max-width: 72%;
}
.message-item.service .message-content {
  text-align: right;
}
.message-sender {
  font-size: 0.72rem;
  color: #a89f92;
}
.message-text {
  background: #f0eee9;
  padding: 9px 12px;
  border-radius: 10px;
  margin-top: 3px;
  font-size: 0.88rem;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  display: inline-block;
  text-align: left;
}
.message-item.service .message-text {
  background: var(--c-primary, #2e8b6f);
  color: #fff;
}
.message-item.ai .message-text {
  background: var(--c-primary-soft, #eaf5f0);
  color: var(--c-primary-dark, #256f59);
  border: 1px solid #cfe8de;
}
.message-item.system {
  justify-content: center;
}
.system-msg {
  background: #ece9e3;
  color: #8a8378;
  padding: 5px 14px;
  border-radius: 16px;
  font-size: 0.78rem;
  max-width: 90%;
  text-align: center;
}
.message-time {
  font-size: 0.68rem;
  color: #b7afa2;
  margin-top: 3px;
}

/* 工具栏 + 输入 */
.composer {
  border-top: 1px solid var(--c-border, #e7e3dc);
  position: relative;
}
.composer-toolbar {
  display: flex;
  gap: 6px;
  padding: 6px 12px;
}
.tool-btn {
  border: none;
  background: none;
  cursor: pointer;
  font-size: 0.82rem;
  padding: 4px 8px;
  border-radius: 6px;
  color: var(--c-text-muted, #7a7a7a);
}
.tool-btn:hover,
.tool-btn.on {
  background: var(--c-primary-soft, #eaf5f0);
  color: var(--c-primary, #2e8b6f);
}
.emoji-pop {
  position: absolute;
  bottom: 100%;
  left: 12px;
  background: #fff;
  border: 1px solid var(--c-border, #e7e3dc);
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  padding: 8px;
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 2px;
  z-index: 5;
}
.emoji {
  border: none;
  background: none;
  cursor: pointer;
  font-size: 1.1rem;
  padding: 4px;
  border-radius: 5px;
}
.emoji:hover {
  background: var(--c-bg, #f7f5f1);
}
.qr-pop {
  position: absolute;
  bottom: 100%;
  left: 12px;
  right: 12px;
  max-height: 240px;
  overflow-y: auto;
  background: #fff;
  border: 1px solid var(--c-border, #e7e3dc);
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  padding: 6px;
  z-index: 5;
}
.qr-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  text-align: left;
  border: none;
  background: none;
  cursor: pointer;
  padding: 7px 9px;
  border-radius: 6px;
}
.qr-item:hover {
  background: var(--c-primary-soft, #eaf5f0);
}
.qr-label {
  font-size: 0.72rem;
  color: var(--c-primary, #2e8b6f);
  font-weight: 600;
}
.qr-text {
  font-size: 0.78rem;
  color: var(--c-text-muted, #7a7a7a);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.composer-input {
  display: flex;
  gap: 8px;
  padding: 0 12px 12px;
  align-items: flex-end;
}
.composer-input textarea {
  flex: 1;
  resize: none;
  padding: 8px 10px;
  border: 1px solid var(--c-border, #e7e3dc);
  border-radius: 8px;
  font-size: 0.88rem;
  font-family: inherit;
  line-height: 1.5;
}
.composer-input textarea:focus {
  outline: none;
  border-color: var(--c-primary, #2e8b6f);
}
.btn-send {
  background: var(--c-primary, #2e8b6f);
  color: #fff;
  padding: 8px 20px;
  height: fit-content;
}

/* ---------- 右栏 ---------- */
.col-context {
  overflow-y: auto;
}
.ctx-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: #b0aaa0;
  font-size: 0.82rem;
  padding: 1rem;
}
.ctx-section {
  padding: 12px;
  border-bottom: 8px solid var(--c-bg, #f7f5f1);
}
.customer-card {
  display: flex;
  gap: 10px;
  align-items: center;
}
.cust-avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: var(--c-primary, #2e8b6f);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.1rem;
  font-weight: 700;
  flex-shrink: 0;
}
.cust-name {
  font-weight: 700;
  color: var(--c-text, #2b2b2b);
}
.cust-sub {
  font-size: 0.72rem;
  color: var(--c-text-muted, #7a7a7a);
  margin-top: 2px;
}
.ctx-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--c-text, #2b2b2b);
  margin-bottom: 8px;
}
.ctx-count {
  font-size: 0.68rem;
  background: var(--c-primary-soft, #eaf5f0);
  color: var(--c-primary, #2e8b6f);
  border-radius: 8px;
  padding: 0 6px;
}
.ctx-loading,
.ctx-empty-sm {
  font-size: 0.78rem;
  color: #b0aaa0;
  padding: 6px 0;
}
.order-card,
.custom-card {
  border: 1px solid var(--c-border, #e7e3dc);
  border-radius: 8px;
  padding: 9px 10px;
  margin-bottom: 8px;
  background: var(--c-surface, #fff);
}
.order-row1 {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.order-no {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--c-text, #2b2b2b);
  word-break: break-all;
}
.order-status {
  font-size: 0.68rem;
  padding: 1px 7px;
  border-radius: 4px;
  background: #eee;
  color: #777;
  flex-shrink: 0;
  margin-left: 6px;
}
.order-status.paid,
.order-status.PAID,
.order-status.shipped,
.order-status.SHIPPED {
  background: var(--c-primary-soft, #eaf5f0);
  color: var(--c-primary, #2e8b6f);
}
.order-status.PENDING_PAY {
  background: #fdf0e6;
  color: var(--c-accent, #c1502e);
}
.order-row2 {
  display: flex;
  justify-content: space-between;
  font-size: 0.76rem;
  color: var(--c-text-muted, #7a7a7a);
  margin-top: 4px;
}
.order-recv {
  font-size: 0.72rem;
  color: #a89f92;
  margin-top: 3px;
}
.order-actions {
  display: flex;
  gap: 6px;
  margin-top: 8px;
  flex-wrap: wrap;
}
.mini-btn {
  border: 1px solid var(--c-border, #e7e3dc);
  background: #fff;
  border-radius: 5px;
  padding: 3px 9px;
  font-size: 0.7rem;
  cursor: pointer;
  color: var(--c-primary, #2e8b6f);
}
.mini-btn:hover {
  background: var(--c-primary-soft, #eaf5f0);
}
.logistics-box {
  margin-top: 8px;
  padding: 8px;
  background: var(--c-bg, #f7f5f1);
  border-radius: 6px;
}
.logi-empty {
  font-size: 0.72rem;
  color: #b0aaa0;
}
.logi-item {
  display: flex;
  gap: 8px;
  font-size: 0.7rem;
  color: var(--c-text-muted, #7a7a7a);
  margin-bottom: 4px;
}
.logi-time {
  flex-shrink: 0;
  color: #a89f92;
}
.ctx-links {
  display: flex;
  gap: 8px;
  border-bottom: none;
}
.link-btn {
  flex: 1;
  border: 1px solid var(--c-border, #e7e3dc);
  background: #fff;
  border-radius: 6px;
  padding: 8px;
  font-size: 0.78rem;
  cursor: pointer;
  color: var(--c-text, #2b2b2b);
}
.link-btn:hover {
  background: var(--c-primary-soft, #eaf5f0);
  border-color: var(--c-primary, #2e8b6f);
}

/* 窄屏：隐藏右栏，列表变窄 */
@media (max-width: 1024px) {
  .workstation {
    grid-template-columns: 260px 1fr;
  }
  .col-context {
    display: none;
  }
}
@media (max-width: 768px) {
  .workstation {
    grid-template-columns: 1fr;
    grid-auto-rows: minmax(0, 1fr);
  }
  .col-list {
    max-height: 38vh;
  }
}
</style>
