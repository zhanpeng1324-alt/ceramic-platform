<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/services/api'
import type { User } from '@/types'
import ServiceNavbar from '@/components/service/ServiceNavbar.vue'

const router = useRouter()
const customers = ref<User[]>([])
const serviceUsers = ref<User[]>([])
const loading = ref(true)

const formatDate = (dateStr: string | undefined): string => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

const getStatusLabel = (status: string | undefined) => {
  return status === 'active' ? '正常' : '禁用'
}

const loadUsers = async () => {
  try {
    customers.value = await api.customerList()
    serviceUsers.value = await api.serviceList()
  } catch (err) {
    console.error('加载用户列表失败:', err)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadUsers()
})
</script>

<template>
  <div class="service-users">
    <ServiceNavbar />
    
    <main class="users-content">
      <div v-if="loading" class="loading">加载中...</div>
      
      <div v-else>
        <section>
          <h2>消费者用户</h2>
          <div v-if="customers.length === 0" class="empty">暂无消费者用户</div>
          <div v-else class="users-table">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>用户名</th>
                  <th>昵称</th>
                  <th>邮箱</th>
                  <th>手机号</th>
                  <th>状态</th>
                  <th>注册时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="user in customers" :key="user.id">
                  <td>#{{ user.id }}</td>
                  <td>{{ user.username }}</td>
                  <td>{{ user.nickname }}</td>
                  <td>{{ user.email || '-' }}</td>
                  <td>{{ user.phone || '-' }}</td>
                  <td>
                    <span class="status" :class="user.status">
                      {{ getStatusLabel(user.status) }}
                    </span>
                  </td>
                  <td>{{ formatDate(user.createdAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section>
          <h2>客服用户</h2>
          <div v-if="serviceUsers.length === 0" class="empty">暂无客服用户</div>
          <div v-else class="users-table">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>用户名</th>
                  <th>昵称</th>
                  <th>邮箱</th>
                  <th>状态</th>
                  <th>注册时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="user in serviceUsers" :key="user.id">
                  <td>#{{ user.id }}</td>
                  <td>{{ user.username }}</td>
                  <td>{{ user.nickname }}</td>
                  <td>{{ user.email || '-' }}</td>
                  <td>
                    <span class="status" :class="user.status">
                      {{ getStatusLabel(user.status) }}
                    </span>
                  </td>
                  <td>{{ formatDate(user.createdAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>

<style scoped>
.service-users {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.users-header {
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

.btn-back {
  background: #95a5a6;
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  cursor: pointer;
  margin-left: 1rem;
}

.users-content {
  flex: 1;
  padding: 1.5rem;
}

.loading, .empty {
  text-align: center;
  padding: 2rem;
  color: #666;
}

section {
  background: white;
  border-radius: 8px;
  padding: 1.5rem;
  margin-bottom: 1.5rem;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}

section h2 {
  margin: 0 0 1rem 0;
  color: #2c3e50;
  font-size: 1.1rem;
}

.users-table {
  overflow-x: auto;
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

.status {
  padding: 0.2rem 0.5rem;
  border-radius: 3px;
  font-size: 0.75rem;
}

.status.active {
  background: #27ae60;
  color: white;
}

.status.disabled {
  background: #95a5a6;
  color: white;
}

@media (max-width: 768px) {
  .users-table {
    overflow-x: auto;
  }
}
</style>