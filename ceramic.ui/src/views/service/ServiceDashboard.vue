<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/services/api'
import { useUserStore } from '@/stores/user'
import type { ChatConversation, SupportTicket, ReturnRequest, Order } from '@/types'

const router = useRouter()
const userStore = useUserStore()

const conversations = ref<ChatConversation[]>([])
const tickets = ref<SupportTicket[]>([])
const returnRequests = ref<ReturnRequest[]>([])
const recentOrders = ref<Order[]>([])
const loading = ref(true)

const formatDate = (dateStr: string | undefined): string => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

const goToChat = (conversationId?: number | Event) => {
  if (typeof conversationId === 'number') {
    router.push(`/service/chat/${conversationId}`)
  } else {
    router.push('/service/chat')
  }
}

const goToTickets = () => {
  router.push('/service/tickets')
}

const goToReturns = () => {
  router.push('/service/returns')
}

const goToOrders = () => {
  router.push('/service/orders')
}

const logout = () => {
  userStore.logout()
  router.push('/service/login')
}

const loadData = async () => {
  try {
    conversations.value = await api.serviceChatConversations()
    tickets.value = await api.supportTickets()
    returnRequests.value = await api.returnRequests()
    recentOrders.value = await api.orders()
  } catch (err) {
    console.error('加载数据失败:', err)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (!userStore.isLoggedIn || (!userStore.isService && !userStore.isAdmin)) {
    router.push('/service/login')
    return
  }
  loadData()
})
</script>

<template>
  <div class="service-dashboard">
    <header class="dashboard-header">
      <div class="header-left">
        <h1>客服工作台</h1>
        <p>欢迎, {{ userStore.currentUser?.nickname }}</p>
      </div>
      <button @click="logout" class="btn btn-logout">退出登录</button>
    </header>

    <nav class="dashboard-nav">
      <div class="nav-item" @click="router.push('/service/dashboard')">
        <span class="nav-icon">📊</span>
        <span>工作台</span>
      </div>
      <div class="nav-item" @click="router.push('/service/chat')">
        <span class="nav-icon">💬</span>
        <span>对话管理</span>
        <span v-if="conversations.length" class="badge">{{ conversations.length }}</span>
      </div>
      <div class="nav-item" @click="router.push('/service/tickets')">
        <span class="nav-icon">🎫</span>
        <span>工单处理</span>
        <span v-if="tickets.length" class="badge">{{ tickets.length }}</span>
      </div>
      <div class="nav-item" @click="router.push('/service/returns')">
        <span class="nav-icon">📦</span>
        <span>售后协助</span>
      </div>
      <div class="nav-item" @click="router.push('/service/orders')">
        <span class="nav-icon">📋</span>
        <span>订单查询</span>
      </div>
      <div class="nav-item" @click="router.push('/service/users')">
        <span class="nav-icon">👥</span>
        <span>用户列表</span>
      </div>
      <div v-if="userStore.isAdmin" class="nav-item" @click="router.push('/admin')">
        <span class="nav-icon">⚙️</span>
        <span>系统管理</span>
      </div>
    </nav>

    <main class="dashboard-content">
      <div v-if="loading" class="loading">加载中...</div>
      <div v-else>
        <div class="stats-row">
          <div class="stat-card">
            <div class="stat-icon">💬</div>
            <div class="stat-info">
              <div class="stat-value">{{ conversations.length }}</div>
              <div class="stat-label">待处理对话</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon">🎫</div>
            <div class="stat-info">
              <div class="stat-value">{{ tickets.length }}</div>
              <div class="stat-label">待处理工单</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon">📦</div>
            <div class="stat-info">
              <div class="stat-value">{{ returnRequests.length }}</div>
              <div class="stat-label">退换货申请</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon">📋</div>
            <div class="stat-info">
              <div class="stat-value">{{ recentOrders.length }}</div>
              <div class="stat-label">今日订单</div>
            </div>
          </div>
        </div>

        <div class="section-row">
          <div class="section">
            <div class="section-header" @click="goToChat">
              <h2>最新对话</h2>
              <span class="view-all">查看全部 →</span>
            </div>
            <div v-if="conversations.length === 0" class="empty">暂无对话</div>
            <div v-else class="conversation-list">
              <div 
                v-for="conv in conversations.slice(0, 5)" 
                :key="conv.id"
                class="conversation-item"
                @click="goToChat(conv.id!)"
              >
                <div class="conv-info">
                  <div class="conv-customer">{{ conv.customerName || '匿名用户' }}</div>
                  <div class="conv-time">{{ formatDate(conv.createdAt) }}</div>
                </div>
                <div class="conv-status" :class="conv.status">
                  {{ conv.status === 'active' ? '进行中' : '已解决' }}
                </div>
              </div>
            </div>
          </div>

          <div class="section">
            <div class="section-header" @click="goToTickets">
              <h2>待处理工单</h2>
              <span class="view-all">查看全部 →</span>
            </div>
            <div v-if="tickets.length === 0" class="empty">暂无工单</div>
            <div v-else class="ticket-list">
              <div 
                v-for="ticket in tickets.slice(0, 5)" 
                :key="ticket.id"
                class="ticket-item"
              >
                <div class="ticket-info">
                  <div class="ticket-title">{{ ticket.title }}</div>
                  <div class="ticket-meta">
                    <span class="priority" :class="ticket.priority">
                      {{ ticket.priority === 'high' ? '高' : ticket.priority === 'medium' ? '中' : '低' }}
                    </span>
                    <span>{{ formatDate(ticket.createdAt) }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="section-row">
          <div class="section">
            <div class="section-header" @click="goToReturns">
              <h2>退换货申请</h2>
              <span class="view-all">查看全部 →</span>
            </div>
            <div v-if="returnRequests.length === 0" class="empty">暂无申请</div>
            <div v-else class="return-list">
              <div 
                v-for="req in returnRequests.slice(0, 5)" 
                :key="req.id"
                class="return-item"
              >
                <div class="return-info">
                  <div class="return-order">订单 #{{ req.orderId }}</div>
                  <div class="return-type">{{ req.type === 'refund' ? '退款' : '退货' }}</div>
                </div>
                <div class="return-status" :class="req.status">
                  {{ req.status === 'pending' ? '待审核' : req.status === 'approved' ? '已通过' : '已驳回' }}
                </div>
              </div>
            </div>
          </div>

          <div class="section">
            <div class="section-header" @click="goToOrders">
              <h2>最近订单</h2>
              <span class="view-all">查看全部 →</span>
            </div>
            <div v-if="recentOrders.length === 0" class="empty">暂无订单</div>
            <div v-else class="order-list">
              <div 
                v-for="order in recentOrders.slice(0, 5)" 
                :key="order.id"
                class="order-item"
              >
                <div class="order-info">
                  <div class="order-number">#{{ order.id }}</div>
                  <div class="order-status">{{ order.status }}</div>
                </div>
                <div class="order-amount">{{ order.totalAmount }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.service-dashboard {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 2rem;
  background: white;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.header-left h1 {
  margin: 0;
  color: #2c3e50;
}

.header-left p {
  margin: 0.25rem 0 0 0;
  color: #666;
}

.btn-logout {
  background: #e74c3c;
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  cursor: pointer;
}

.dashboard-nav {
  width: 200px;
  background: #2c3e50;
  color: white;
  padding: 1rem 0;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1.5rem;
  cursor: pointer;
  transition: background 0.2s;
  position: relative;
}

.nav-item:hover {
  background: #34495e;
}

.nav-icon {
  font-size: 1.2rem;
}

.badge {
  margin-left: auto;
  background: #e74c3c;
  color: white;
  font-size: 0.7rem;
  padding: 0.2rem 0.5rem;
  border-radius: 10px;
}

.dashboard-content {
  flex: 1;
  padding: 1.5rem;
  overflow-y: auto;
}

.loading {
  text-align: center;
  padding: 2rem;
  color: #666;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.stat-card {
  background: white;
  border-radius: 8px;
  padding: 1.25rem;
  display: flex;
  align-items: center;
  gap: 1rem;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}

.stat-icon {
  font-size: 2.5rem;
}

.stat-value {
  font-size: 1.5rem;
  font-weight: bold;
  color: #2c3e50;
}

.stat-label {
  color: #666;
  font-size: 0.9rem;
}

.section-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
  margin-bottom: 1rem;
}

.section {
  background: white;
  border-radius: 8px;
  padding: 1.25rem;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  cursor: pointer;
}

.section-header h2 {
  margin: 0;
  font-size: 1.1rem;
  color: #2c3e50;
}

.view-all {
  color: #42b883;
  font-size: 0.9rem;
}

.empty {
  text-align: center;
  padding: 2rem;
  color: #999;
}

.conversation-list, .ticket-list, .return-list, .order-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.conversation-item, .ticket-item, .return-item, .order-item {
  padding: 0.75rem;
  background: #f9f9f9;
  border-radius: 4px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.conversation-item:hover {
  background: #f0f0f0;
}

.conv-customer, .ticket-title, .return-order, .order-number {
  font-weight: bold;
  color: #2c3e50;
}

.conv-time, .ticket-meta, .return-type, .order-status {
  color: #666;
  font-size: 0.9rem;
}

.conv-status, .return-status {
  padding: 0.25rem 0.75rem;
  border-radius: 4px;
  font-size: 0.8rem;
}

.conv-status.active, .return-status.pending {
  background: #f39c12;
  color: white;
}

.conv-status.resolved, .return-status.approved {
  background: #27ae60;
  color: white;
}

.return-status.rejected {
  background: #e74c3c;
  color: white;
}

.priority {
  padding: 0.2rem 0.5rem;
  border-radius: 3px;
  font-size: 0.75rem;
  color: white;
}

.priority.high {
  background: #e74c3c;
}

.priority.medium {
  background: #f39c12;
}

.priority.low {
  background: #95a5a6;
}

.order-amount {
  font-weight: bold;
  color: #e74c3c;
}

@media (max-width: 768px) {
  .dashboard-nav {
    width: 100%;
    flex-direction: row;
    overflow-x: auto;
  }
  
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .section-row {
    grid-template-columns: 1fr;
  }
}
</style>