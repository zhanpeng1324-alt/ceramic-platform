<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/services/api'
import type { SupportTicket } from '@/types'
import ServiceNavbar from '@/components/service/ServiceNavbar.vue'

const router = useRouter()
const tickets = ref<SupportTicket[]>([])
const loading = ref(true)
const selectedTicket = ref<SupportTicket | null>(null)
const showModal = ref(false)
const replyContent = ref('')
const newPriority = ref('medium')

const formatDate = (dateStr: string | undefined): string => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

const getPriorityClass = (priority: string) => {
  return `priority-${priority.toLowerCase()}`
}

const getPriorityLabel = (priority: string) => {
  const labels: Record<string, string> = {
    high: '高',
    medium: '中',
    low: '低'
  }
  return labels[priority.toLowerCase()] || priority
}

const getStatusLabel = (status: string) => {
  const labels: Record<string, string> = {
    open: '待处理',
    replied: '已回复',
    processing: '处理中',
    resolved: '已解决',
    closed: '已关闭'
  }
  return labels[status.toLowerCase()] || status
}

const getStatusClass = (status: string) => {
  return `status-${status.toLowerCase()}`
}

const handleViewDetails = (ticket: SupportTicket) => {
  selectedTicket.value = ticket
  newPriority.value = ticket.priority?.toLowerCase() || 'medium'
  replyContent.value = ''
  showModal.value = true
}

const handleUpdatePriority = async () => {
  if (!selectedTicket.value) return
  
  try {
    await api.updateTicketPriority(selectedTicket.value.id!, newPriority.value)
    loadTickets()
    showModal.value = false
  } catch (err) {
    console.error('更新优先级失败:', err)
  }
}

const handleUpdateStatus = async (status: string) => {
  if (!selectedTicket.value) return
  
  try {
    await api.updateTicketStatus(selectedTicket.value.id!, status)
    loadTickets()
    showModal.value = false
  } catch (err) {
    console.error('更新状态失败:', err)
  }
}

const handleReply = async () => {
  if (!selectedTicket.value || !replyContent.value.trim()) return
  
  try {
    await api.replyTicket(selectedTicket.value.id!, {
      reply: replyContent.value.trim(),
      status: 'replied'
    })
    replyContent.value = ''
    loadTickets()
    showModal.value = false
  } catch (err) {
    console.error('回复失败:', err)
  }
}

const loadTickets = async () => {
  try {
    tickets.value = await api.supportTickets()
  } catch (err) {
    console.error('加载工单失败:', err)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadTickets()
})
</script>

<template>
  <div class="service-tickets">
    <ServiceNavbar />
    
    <main class="tickets-content">
      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="tickets.length === 0" class="empty">暂无工单</div>
      <div v-else class="tickets-table">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>标题</th>
              <th>用户</th>
              <th>优先级</th>
              <th>状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="ticket in tickets" :key="ticket.id">
              <td>#{{ ticket.id }}</td>
              <td>{{ ticket.title }}</td>
              <td>{{ ticket.customerName || ticket.userId }}</td>
              <td>
                <span class="priority" :class="getPriorityClass(ticket.priority || 'medium')">
                  {{ getPriorityLabel(ticket.priority || 'medium') }}
                </span>
              </td>
              <td>
                <span class="status" :class="getStatusClass(ticket.status || 'OPEN')">
                  {{ getStatusLabel(ticket.status || 'OPEN') }}
                </span>
              </td>
              <td>{{ formatDate(ticket.createdAt) }}</td>
              <td>
                <button class="btn btn-view" @click="handleViewDetails(ticket)">查看详情</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </main>

    <div v-if="showModal" class="modal-overlay" @click.self="showModal = false">
      <div class="modal">
        <div class="modal-header">
          <h2>工单详情 #{{ selectedTicket?.id }}</h2>
          <button @click="showModal = false" class="btn-close">×</button>
        </div>
        <div class="modal-body">
          <div class="detail-section">
            <div class="detail-row">
              <span class="label">标题:</span>
              <span>{{ selectedTicket?.title }}</span>
            </div>
            <div class="detail-row">
              <span class="label">用户:</span>
              <span>{{ selectedTicket?.customerName || selectedTicket?.userId }}</span>
            </div>
            <div class="detail-row">
              <span class="label">类型:</span>
              <span>{{ selectedTicket?.type }}</span>
            </div>
            <div class="detail-row">
              <span class="label">优先级:</span>
              <select v-model="newPriority" class="priority-select">
                <option value="high">高</option>
                <option value="medium">中</option>
                <option value="low">低</option>
              </select>
            </div>
            <div class="detail-row">
              <span class="label">当前状态:</span>
              <span class="status" :class="getStatusClass(selectedTicket?.status || 'OPEN')">
                {{ getStatusLabel(selectedTicket?.status || 'OPEN') }}
              </span>
            </div>
            <div class="detail-row">
              <span class="label">创建时间:</span>
              <span>{{ formatDate(selectedTicket?.createdAt) }}</span>
            </div>
            <div class="detail-row full">
              <span class="label">内容:</span>
              <p>{{ selectedTicket?.content }}</p>
            </div>
            <div v-if="selectedTicket?.reply" class="detail-row full">
              <span class="label">回复:</span>
              <p class="reply-content">{{ selectedTicket?.reply }}</p>
            </div>
          </div>

          <div class="reply-section">
            <h3>回复工单</h3>
            <textarea 
              v-model="replyContent" 
              placeholder="输入回复内容..."
              class="reply-textarea"
              rows="4"
            ></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-cancel" @click="showModal = false">关闭</button>
          <button class="btn btn-priority" @click="handleUpdatePriority">更新优先级</button>
          <button v-if="(selectedTicket?.status || 'OPEN').toLowerCase() !== 'resolved'" class="btn btn-resolve" @click="handleUpdateStatus('resolved')">标记已解决</button>
          <button v-if="(selectedTicket?.status || 'OPEN').toLowerCase() === 'resolved'" class="btn btn-reopen" @click="handleUpdateStatus('open')">重新打开</button>
          <button class="btn btn-reply" @click="handleReply">发送回复</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.service-tickets {
  min-height: 100vh;
  background: #f5f7fa;
}

.tickets-content {
  padding: 1.5rem;
}

.loading, .empty {
  text-align: center;
  padding: 2rem;
  color: #666;
}

.tickets-table {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  overflow: hidden;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th, td {
  padding: 0.75rem 1rem;
  text-align: left;
  border-bottom: 1px solid #eee;
}

th {
  background: #f8f9fa;
  font-weight: bold;
  color: #2c3e50;
}

.priority {
  padding: 0.2rem 0.5rem;
  border-radius: 3px;
  font-size: 0.75rem;
  color: white;
}

.priority-high {
  background: #e74c3c;
}

.priority-medium {
  background: #f39c12;
}

.priority-low {
  background: #95a5a6;
}

.status {
  padding: 0.2rem 0.5rem;
  border-radius: 3px;
  font-size: 0.75rem;
}

.status-open {
  background: #f39c12;
  color: white;
}

.status-replied {
  background: #3498db;
  color: white;
}

.status-processing {
  background: #9b59b6;
  color: white;
}

.status-resolved {
  background: #27ae60;
  color: white;
}

.status-closed {
  background: #95a5a6;
  color: white;
}

.btn-view {
  background: #42b883;
  color: white;
  border: none;
  padding: 0.4rem 0.8rem;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal {
  background: white;
  border-radius: 8px;
  width: 90%;
  max-width: 600px;
  max-height: 80vh;
  overflow-y: auto;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem;
  border-bottom: 1px solid #eee;
}

.modal-header h2 {
  margin: 0;
  color: #2c3e50;
}

.btn-close {
  background: none;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  color: #666;
}

.modal-body {
  padding: 1rem;
}

.detail-section {
  margin-bottom: 1rem;
}

.detail-row {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.detail-row.full {
  flex-direction: column;
}

.label {
  font-weight: bold;
  color: #666;
  width: 80px;
}

.detail-row.full .label {
  margin-bottom: 0.25rem;
}

.detail-row p {
  margin: 0;
  color: #333;
}

.reply-content {
  background: #e8f5e9;
  padding: 0.75rem;
  border-radius: 4px;
}

.priority-select {
  padding: 0.3rem 0.5rem;
  border: 1px solid #ddd;
  border-radius: 4px;
}

.reply-section {
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid #eee;
}

.reply-section h3 {
  margin: 0 0 0.5rem 0;
  color: #2c3e50;
}

.reply-textarea {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  resize: vertical;
  font-family: inherit;
}

.modal-footer {
  display: flex;
  gap: 0.5rem;
  padding: 1rem;
  border-top: 1px solid #eee;
  justify-content: flex-end;
}

.btn-cancel {
  background: #95a5a6;
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  cursor: pointer;
}

.btn-priority {
  background: #3498db;
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  cursor: pointer;
}

.btn-resolve {
  background: #27ae60;
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  cursor: pointer;
}

.btn-reopen {
  background: #f39c12;
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  cursor: pointer;
}

.btn-reply {
  background: #42b883;
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  cursor: pointer;
}

@media (max-width: 768px) {
  .tickets-table {
    overflow-x: auto;
  }
  
  .modal-footer {
    flex-wrap: wrap;
  }
}
</style>