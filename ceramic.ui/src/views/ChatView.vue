<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { api } from '@/services/api'
import { realtime } from '@/services/realtime'
import { useUserStore } from '@/stores/user'
import type { ChatConversation, ChatMessage, User } from '@/types'

const userStore = useUserStore()

const currentUser = ref<User>({
  id: userStore.currentUser?.id || 0,
  username: userStore.currentUser?.username || '陶瓷爱好者',
  email: userStore.currentUser?.email || 'customer@example.com',
  role: 'customer',
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
})

const supportAgents = ref<User[]>([
  { id: 999, username: '陶瓷客服小王', email: 'support1@example.com', role: 'service', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
  { id: 998, username: '陶瓷客服小李', email: 'support2@example.com', role: 'service', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
])

const conversations = ref<ChatConversation[]>([])
const messages = ref<ChatMessage[]>([])
const currentConversation = ref<ChatConversation | null>(null)
const newMessage = ref('')
const error = ref('')
const humanServiceMode = ref(false)
const sending = ref(false)

const QUICK_QUESTIONS = [
  '如何保养陶瓷？',
  '定制周期多久？',
  '青釉和白釉有什么区别？',
  '如何申请售后？',
] as const

const sendQuickQuestion = (question: string) => {
  if (sending.value || isHumanService.value) return
  newMessage.value = question
  sendMessage()
}

const getCurrentUser = (): User => {
  try {
    const saved = localStorage.getItem('user')
    if (saved) return JSON.parse(saved) as User
  } catch { /* use the demo account below */ }
  return currentUser.value
}

const isHumanService = computed(() => {
  if (humanServiceMode.value) return true
  if (currentConversation.value?.transferredToHuman) return true
  return conversations.value.find(c => c.id === currentConversation.value?.id)?.transferredToHuman ?? false
})

const selectConversation = async (conversation: ChatConversation) => {
  currentConversation.value = conversation
  conversation.unreadCount = 0
  api.markAsRead(conversation.id!).catch(err => console.error('标记已读失败:', err))
  humanServiceMode.value = !!conversation.transferredToHuman
  try {
    messages.value = await api.chatMessages(conversation.id!)
  } catch {
    messages.value = []
  }
  subscribeConversation(conversation.id!)
}

// —— WebSocket 实时接收 ——
let chatUnsub: (() => void) | null = null

const subscribeConversation = (conversationId: number) => {
  if (chatUnsub) {
    chatUnsub()
    chatUnsub = null
  }
  chatUnsub = realtime.subscribe(`/topic/conversation/${conversationId}`, (payload) => {
    const msg = payload as ChatMessage
    if (!msg || !msg.id) return
    // 忽略自己刚发的消息（已乐观插入）
    if (msg.senderRole === 'customer' && msg.senderId === currentUser.value.id) return
    // 按 id 去重（AI 回复既走同步返回也走推送）
    if (messages.value.some(m => m.id === msg.id)) return
    messages.value.push(msg)
    if (currentConversation.value) {
      currentConversation.value.lastMessage = msg.message
      currentConversation.value.lastMessageTime = msg.timestamp
    }
    if (msg.conversationId === currentConversation.value?.id) {
      api.markAsRead(msg.conversationId).catch(() => {})
    }
    scrollToBottom()
  })
}

const sendMessage = async () => {
  if (!newMessage.value.trim() || !currentConversation.value || sending.value) return
  const content = newMessage.value.trim()

  sending.value = true
  newMessage.value = ''
  error.value = ''

  const tempMsg: ChatMessage = {
    id: Date.now(),
    conversationId: currentConversation.value.id!,
    senderId: currentUser.value.id,
    senderRole: 'customer',
    message: content,
    type: 'text',
    timestamp: localNow(),
    readBySupport: false,
    readByCustomer: true,
  }
  messages.value.push(tempMsg)

  if (currentConversation.value) {
    currentConversation.value.lastMessage = content
    currentConversation.value.lastMessageTime = new Date().toISOString()
  }

  await scrollToBottom()

  try {
    if (isHumanService.value) {
      await api.sendChatMessage({
        conversationId: currentConversation.value.id!,
        message: content,
      })
    } else {
      const response = await api.sendAiChatMessage({
        conversationId: currentConversation.value.id!,
        message: content,
      })
      if (response.assistantMessage) {
        // AI 回复可能同时经「同步返回」和「WebSocket 推送」两条通道到达，按 id 去重，避免出现两条
        const ai = response.assistantMessage
        if (!messages.value.some(m => m.id === ai.id)) {
          messages.value.push(ai)
        }
        if (currentConversation.value) {
          currentConversation.value.lastMessage = ai.message
          currentConversation.value.lastMessageTime = new Date().toISOString()
        }
        await scrollToBottom()
      }
      if (response.transferredToHuman) {
        humanServiceMode.value = true
        if (currentConversation.value) {
          currentConversation.value.transferredToHuman = true
        }
        await loadConversations()
        error.value = '已为您转接人工客服。'
      } else if (response.suggestTransfer) {
        error.value = '该问题需人工核实，建议点击「转人工客服」。'
      }
    }
  } catch {
    error.value = '发送失败，请重试。'
  } finally {
    sending.value = false
  }
}

const requestTransferToHuman = async () => {
  if (!currentConversation.value) return
  if (!confirm('确定要转接人工客服吗？')) return

  try {
    await api.transferToHuman(currentConversation.value.id!)
    humanServiceMode.value = true
    if (currentConversation.value) {
      currentConversation.value.transferredToHuman = true
    }
    const systemMsg: ChatMessage = {
      id: Date.now() + 1,
      conversationId: currentConversation.value.id!,
      senderId: 0,
      senderRole: 'system',
      message: '已为您转接人工客服，会话已标记为待人工处理，请稍候…',
      type: 'system',
      timestamp: new Date().toISOString(),
      readBySupport: true,
      readByCustomer: true,
    }
    messages.value.push(systemMsg)
    await loadConversations()
  } catch {
    error.value = '转接失败，请重试。'
  }
}

const createNewConversation = async () => {
  try {
    const newConv = await api.createChatConversation()
    conversations.value.unshift(newConv)
    humanServiceMode.value = false
    selectConversation(newConv)
  } catch {
    const newConv: ChatConversation = {
      id: conversations.value.length + 1,
      customerId: currentUser.value.id,
      status: 'open',
      subject: '新的客服咨询',
      priority: 'medium',
      unreadCount: 0,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      transferredToHuman: false,
    }
    conversations.value.unshift(newConv)
    humanServiceMode.value = false
    selectConversation(newConv)
  }
}

const formatTime = (timestamp: string): string => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// 生成与后端一致的本地秒级时间戳 yyyy-MM-dd HH:mm:ss，避免前端毫秒精度导致排序错乱
const localNow = (): string => {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const formatDate = (timestamp: string): string => {
  const date = new Date(timestamp)
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

const getSupportAgentName = (agentId?: number): string => {
  if (!agentId) return '未分配'
  const agent = supportAgents.value.find((a: User) => a.id === agentId)
  return agent?.username || '客服'
}

const getSenderDisplayName = (msg: ChatMessage): string => {
  if (msg.senderRole === 'system') return '系统通知'
  if (msg.senderId === currentUser.value.id) return '我'
  if (msg.senderRole === 'ai') return 'AI 陶瓷顾问'
  return getSupportAgentName(msg.senderId)
}

const isSystemMessage = (msg: ChatMessage): boolean => {
  return msg.senderRole === 'system' || msg.type === 'system'
}

const currentMessages = computed(() => {
  if (!currentConversation.value) return []
  return messages.value
    .filter(msg => msg.conversationId === currentConversation.value?.id)
    .sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime())
})

const scrollToBottom = async () => {
  await nextTick()
  const container = document.querySelector('.messages-container')
  if (container) {
    container.scrollTop = container.scrollHeight
  }
}

watch(currentMessages, () => {
  scrollToBottom()
}, { deep: true })

const loadConversations = async () => {
  try {
    conversations.value = await api.chatConversations()
    if (conversations.value.length === 0) {
      createNewConversation()
    } else {
      const target = currentConversation.value
        ? conversations.value.find(c => c.id === currentConversation.value?.id)
        : conversations.value[0]
      if (target) {
        selectConversation(target)
      } else {
        selectConversation(conversations.value[0]!)
      }
    }
  } catch {
    conversations.value = [
      {
        id: 1,
        customerId: currentUser.value.id,
        supportAgentId: 999,
        status: 'open',
        subject: '关于定制陶瓷的问题',
        priority: 'medium',
        unreadCount: 0,
        lastMessage: '好的，我们会在24小时内联系您',
        lastMessageTime: new Date().toISOString(),
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        transferredToHuman: false,
      },
    ]
    messages.value = [
      { id: 1, conversationId: 1, senderId: currentUser.value.id, senderRole: 'customer', message: '您好，我想咨询一下定制陶瓷的相关问题', type: 'text', timestamp: new Date().toISOString(), readBySupport: true, readByCustomer: true },
      { id: 2, conversationId: 1, senderId: 0, senderRole: 'ai', message: '您好！很高兴为您服务。请问您想了解哪方面的定制呢？', type: 'text', timestamp: new Date().toISOString(), readBySupport: true, readByCustomer: true },
    ]
    selectConversation(conversations.value[0]!)
  }
}

onMounted(() => {
  currentUser.value = getCurrentUser()
  realtime.connect()
  loadConversations()
})

onUnmounted(() => {
  if (chatUnsub) {
    chatUnsub()
    chatUnsub = null
  }
})
</script>

<template>
  <main class="page">
    <header class="page-header">
      <div>
        <h1 class="page-title">在线客服</h1>
        <p class="page-subtitle">
          <span v-if="isHumanService" class="human-mode-badge">人工客服模式</span>
          <span v-else>AI 智能客服</span>
        </p>
      </div>
    </header>

    <p v-if="error" class="error">{{ error }}</p>

    <section class="chat-layout">
      <div class="conversations-panel">
        <div class="panel-header">
          <h2>对话列表</h2>
        </div>
        
        <div class="conversations-list">
          <div 
            v-for="conv in conversations" 
            :key="conv.id"
            class="conversation-item"
            :class="{ active: currentConversation?.id === conv.id }"
            @click="selectConversation(conv)"
          >
            <div class="conv-info">
              <div class="conv-subject">
                {{ conv.subject }}
                <span v-if="conv.transferredToHuman" class="human-tag">人工</span>
              </div>
              <div class="conv-preview">{{ conv.lastMessage || '暂无消息' }}</div>
            </div>
            <div class="conv-meta">
              <span class="priority" :class="conv.priority">{{ conv.priority }}</span>
              <span class="time">{{ formatDate(conv.lastMessageTime || conv.createdAt || new Date().toISOString()) }}</span>
              <span v-if="conv.unreadCount > 0" class="unread-count">{{ conv.unreadCount }}</span>
            </div>
          </div>
        </div>
      </div>
      
      <div class="messages-panel">
        <div v-if="currentConversation" class="messages-header">
          <div class="header-left">
            <h3>{{ currentConversation.subject }}</h3>
            <span class="status" :class="currentConversation.status">
              {{ currentConversation.status === 'open' ? '进行中' : '已关闭' }}
            </span>
            <span v-if="isHumanService" class="mode-indicator">人工客服服务中</span>
          </div>
          <button 
            v-if="!isHumanService" 
            class="transfer-btn"
            @click="requestTransferToHuman"
          >
            转人工客服
          </button>
        </div>
        
        <div v-if="currentConversation" class="messages-container">
          <div class="messages-list">
            <div 
              v-for="msg in currentMessages" 
              :key="msg.id"
              class="message-bubble"
              :class="{ 
                'sent-by-me': msg.senderId === currentUser.id && !isSystemMessage(msg),
                'sent-by-other': msg.senderId !== currentUser.id && !isSystemMessage(msg),
                'system-message': isSystemMessage(msg),
                'ai-message': msg.senderRole === 'ai',
                'service-message': msg.senderRole === 'service'
              }"
            >
              <div v-if="isSystemMessage(msg)" class="system-notice">
                {{ msg.message }}
              </div>
              <template v-else>
                <div class="message-header">
                  <span class="sender-name" :class="msg.senderRole">
                    {{ getSenderDisplayName(msg) }}
                  </span>
                  <span class="message-time">{{ formatTime(msg.timestamp) }}</span>
                </div>
                <div class="message-content">{{ msg.message }}</div>
              </template>
            </div>
          </div>
        </div>
        
        <div v-else class="no-conversation">
          <p>请选择一个对话或新建对话</p>
        </div>
        
        <div class="message-input" v-if="currentConversation">
          <div v-if="!isHumanService" class="quick-questions">
            <button
              v-for="q in QUICK_QUESTIONS"
              :key="q"
              class="quick-btn"
              :disabled="sending"
              @click="sendQuickQuestion(q)"
            >
              {{ q }}
            </button>
          </div>
          <div v-if="isHumanService" class="human-notice">
            当前由 <strong>{{ getSupportAgentName(currentConversation.supportAgentId) }}</strong> 为您服务
          </div>
          <textarea 
            v-model="newMessage" 
            :placeholder="isHumanService ? '向人工客服描述您的问题…' : '向 AI 陶瓷顾问提问，如商品、订单、售后等问题…'" 
            @keydown.enter.exact.prevent="sendMessage"
            :disabled="sending"
            maxlength="2000"
            rows="3"
          ></textarea>
          <button @click="sendMessage" class="send-btn" :disabled="sending">
            {{ sending ? '发送中…' : '发送' }}
          </button>
        </div>
      </div>
    </section>
  </main>
</template>

<style scoped>
.human-mode-badge {
  background: var(--c-accent);
  color: #fff;
  padding: var(--sp-1) var(--sp-3);
  border-radius: var(--radius-sm);
  font-size: 0.8rem;
  margin-left: var(--sp-2);
}

.human-tag {
  background: var(--c-accent);
  color: #fff;
  padding: 2px var(--sp-2);
  border-radius: var(--radius-sm);
  font-size: 0.7rem;
  margin-left: var(--sp-1);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.mode-indicator {
  background: var(--c-accent);
  color: #fff;
  padding: var(--sp-1) var(--sp-3);
  border-radius: var(--radius-sm);
  font-size: 0.8rem;
}

.transfer-btn {
  background: var(--c-accent);
  color: #fff;
  border: none;
  border-radius: var(--radius-sm);
  padding: var(--sp-2) var(--sp-3);
  font-size: 0.85rem;
  cursor: pointer;
  transition: background 0.2s;
}

.transfer-btn:hover {
  background: var(--c-accent-dark);
}

.quick-questions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-bottom: var(--sp-1);
}

.quick-btn {
  background: var(--c-primary-soft);
  color: var(--c-primary-dark);
  border: 1px solid var(--c-border);
  border-radius: 999px;
  padding: var(--sp-1) var(--sp-3);
  font-size: 0.82rem;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;
}

.quick-btn:hover:not(:disabled) {
  background: #dceee6;
  border-color: var(--c-primary);
}

.quick-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.human-notice {
  background: var(--c-primary-soft);
  color: var(--c-primary-dark);
  padding: var(--sp-2) var(--sp-3);
  border-radius: var(--radius-sm);
  font-size: 0.85rem;
  margin-bottom: var(--sp-2);
  border: 1px solid var(--c-border);
}

.system-message {
  max-width: 90% !important;
  margin: var(--sp-2) auto;
  padding: var(--sp-2) var(--sp-3);
  background: transparent !important;
  text-align: center;
}

.system-notice {
  background: var(--c-bg);
  color: var(--c-text-muted);
  padding: var(--sp-2) var(--sp-4);
  border-radius: 999px;
  font-size: 0.85rem;
  display: inline-block;
  border: 1px solid var(--c-border);
}

.message-bubble.ai-message .sender-name {
  color: var(--c-primary-dark);
}

.message-bubble.service-message .sender-name {
  color: var(--c-accent);
}

.chat-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: var(--sp-4);
  height: calc(100vh - 180px);
}

.conversations-panel {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  padding: var(--sp-4);
  border-bottom: 1px solid var(--c-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-header h2 {
  margin: 0;
  font-size: 1.1rem;
  color: var(--c-text);
}

.new-conversation-btn {
  background: var(--c-primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-sm);
  padding: var(--sp-2) var(--sp-4);
  font-size: 0.9rem;
  cursor: pointer;
  transition: background 0.2s;
}

.new-conversation-btn:hover {
  background: var(--c-primary-dark);
}

.conversations-list {
  overflow-y: auto;
  flex: 1;
}

.conversation-item {
  padding: var(--sp-4);
  border-bottom: 1px solid var(--c-border);
  cursor: pointer;
  transition: background-color 0.2s;
}

.conversation-item:hover {
  background: var(--c-bg);
}

.conversation-item.active {
  background: var(--c-primary-soft);
}

.conv-info {
  margin-bottom: var(--sp-2);
}

.conv-subject {
  font-weight: bold;
  color: var(--c-text);
  font-size: 0.95rem;
}

.conv-preview {
  font-size: 0.85rem;
  color: var(--c-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conv-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.8rem;
  color: var(--c-text-muted);
}

.priority {
  padding: 2px var(--sp-2);
  border-radius: var(--radius-sm);
  font-size: 0.7rem;
}

.priority.low { background: var(--c-primary-soft); color: var(--c-primary-dark); }
.priority.medium { background: #fdf0e9; color: var(--c-accent-dark); }
.priority.high { background: #f7dfd6; color: var(--c-accent-dark); }
.priority.urgent { background: #f2cdc0; color: var(--c-accent-dark); }

.time {
  margin: 0 var(--sp-2);
}

.unread-count {
  background: var(--c-accent);
  color: #fff;
  border-radius: 50%;
  width: 20px;
  height: 20px;
  font-size: 0.75rem;
  display: flex;
  align-items: center;
  justify-content: center;
}

.messages-panel {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.messages-header {
  padding: var(--sp-4);
  border-bottom: 1px solid var(--c-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--c-bg);
}

.messages-header h3 {
  margin: 0;
  color: var(--c-text);
}

.status {
  padding: var(--sp-1) var(--sp-3);
  border-radius: var(--radius-sm);
  font-size: 0.8rem;
}

.status.open { background: var(--c-primary-soft); color: var(--c-primary-dark); }
.status.closed { background: #f7dfd6; color: var(--c-accent-dark); }

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: var(--sp-4);
  display: flex;
  flex-direction: column;
  background: var(--c-bg);
}

.messages-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
  width: 100%;
}

.message-bubble {
  max-width: 70%;
  padding: var(--sp-3);
  border-radius: var(--radius);
  position: relative;
}

.message-bubble.sent-by-me {
  align-self: flex-end;
  background: var(--c-primary);
  color: #fff;
}

.message-bubble.sent-by-other {
  align-self: flex-start;
  background: var(--c-primary-soft);
  color: var(--c-text);
  border: 1px solid var(--c-border);
}

.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--sp-1);
  font-size: 0.8rem;
}

.sender-name {
  font-weight: bold;
}

.sender-name.ai {
  color: var(--c-primary-dark);
}

.sender-name.service {
  color: var(--c-accent);
}

.message-time {
  color: rgba(255, 255, 255, 0.85);
}

.message-bubble.sent-by-other .message-time {
  color: var(--c-text-muted);
}

.message-content {
  word-wrap: break-word;
  line-height: 1.4;
}

.no-conversation {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-text-muted);
}

.message-input {
  padding: var(--sp-4);
  border-top: 1px solid var(--c-border);
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  background: var(--c-surface);
}

.message-input textarea {
  flex: 1;
  border: 1px solid var(--c-border);
  border-radius: var(--radius-sm);
  padding: var(--sp-3);
  resize: none;
  font-size: 0.95rem;
  color: var(--c-text);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.message-input textarea:focus {
  outline: none;
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px var(--c-primary-soft);
}

.send-btn {
  background: var(--c-primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-sm);
  padding: var(--sp-3) var(--sp-5);
  font-size: 0.95rem;
  cursor: pointer;
  white-space: nowrap;
  align-self: flex-end;
  transition: background 0.2s;
}

.send-btn:hover:not(:disabled) {
  background: var(--c-primary-dark);
}

.send-btn:disabled {
  background: var(--c-text-muted);
  cursor: not-allowed;
}

@media (max-width: 768px) {
  .chat-layout {
    grid-template-columns: 1fr;
    height: auto;
  }

  .conversations-panel {
    height: 200px;
  }

  .messages-panel {
    height: calc(100vh - 400px);
  }

  .message-bubble {
    max-width: 85%;
  }
}
</style>
