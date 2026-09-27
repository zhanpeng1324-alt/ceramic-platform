<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import type { User } from '@/types'
import AdminNavbar from '@/components/admin/AdminNavbar.vue'
import { api } from '@/services/api'

const router = useRouter()
const userStore = useUserStore()

const users = ref<User[]>([])
const searchQuery = ref('')
const selectedRole = ref('all')

const filteredUsers = computed(() => {
  return users.value.filter(user => {
    const matchSearch = !searchQuery.value || 
      user.username.toLowerCase().includes(searchQuery.value.toLowerCase()) ||
      (user.nickname && user.nickname.toLowerCase().includes(searchQuery.value.toLowerCase()))
    
    const matchRole = selectedRole.value === 'all' || user.role === selectedRole.value
    
    return matchSearch && matchRole
  })
})

const roles = [
  { value: 'all', label: '全部角色' },
  { value: 'admin', label: '管理员' },
  { value: 'service', label: '客服' },
  { value: 'customer', label: '普通用户' },
]

const loadUsers = async () => {
  try {
    users.value = await api.adminUsers()
  } catch (error) {
    console.error('Failed to load users:', error)
  }
}

const deleteUser = async (userId: number) => {
  if (!confirm('确定要删除该用户吗？')) return
  
  try {
    await api.adminDeleteUser(userId)
    users.value = users.value.filter(u => u.id !== userId)
  } catch (error) {
    console.error('Failed to delete user:', error)
  }
}

const toggleRole = async (user: User) => {
  if (user.id === userStore.currentUser?.id) return
  const newRole = user.role === 'customer' ? 'service' : user.role === 'service' ? 'admin' : 'customer'
  try {
    const updated = await api.adminUpdateUserRole(user.id, newRole)
    Object.assign(user, updated)
  } catch (error) {
    console.error('Failed to update role:', error)
  }
}

onMounted(() => {
  if (userStore.currentUser?.role !== 'admin') {
    router.push('/')
    return
  }
  loadUsers()
})
</script>

<template>
  <div class="admin-page">
    <AdminNavbar />
    
    <div class="page-header">
      <h1>用户管理</h1>
    </div>

    <div class="filters">
      <input 
        v-model="searchQuery"
        type="text" 
        placeholder="搜索用户名或昵称..."
        class="search-input"
      />
      <select v-model="selectedRole" class="role-select">
        <option v-for="role in roles" :key="role.value" :value="role.value">
          {{ role.label }}
        </option>
      </select>
    </div>

    <table class="users-table">
      <thead>
        <tr>
          <th>ID</th>
          <th>用户名</th>
          <th>昵称</th>
          <th>邮箱</th>
          <th>手机号</th>
          <th>角色</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="user in filteredUsers" :key="user.id">
          <td>{{ user.id }}</td>
          <td>{{ user.username }}</td>
          <td>{{ user.nickname || '-' }}</td>
          <td>{{ user.email || '-' }}</td>
          <td>{{ user.phone || '-' }}</td>
          <td>
            <span 
              class="role-badge" 
              :class="user.role"
              @click="toggleRole(user)"
            >
              {{ user.role === 'admin' ? '管理员' : user.role === 'service' ? '客服' : '普通用户' }}
            </span>
          </td>
          <td>
            <button 
              v-if="user.role !== 'admin'"
              class="btn-delete"
              @click="deleteUser(user.id)"
            >
              删除
            </button>
            <span v-else class="no-action">-</span>
          </td>
        </tr>
      </tbody>
    </table>

    <div v-if="filteredUsers.length === 0" class="empty-state">
      暂无用户数据
    </div>
  </div>
</template>

<style scoped>
.user-management {
  padding: 2rem;
  max-width: 1200px;
  margin: 0 auto;
}

.page-header h1 {
  font-size: 1.8rem;
  color: #2c3e50;
  margin-bottom: 1.5rem;
}

.filters {
  display: flex;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.search-input {
  flex: 1;
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
}

.role-select {
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
  background: white;
}

.users-table {
  width: 100%;
  border-collapse: collapse;
  background: white;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.users-table th,
.users-table td {
  padding: 1rem;
  text-align: left;
  border-bottom: 1px solid #eee;
}

.users-table th {
  background: #f8f9fa;
  font-weight: 600;
  color: #333;
}

.users-table tr:hover {
  background: #f8f9fa;
}

.role-badge {
  padding: 0.25rem 0.75rem;
  border-radius: 4px;
  font-size: 0.8rem;
  cursor: pointer;
}

.role-badge.admin {
  background: #e74c3c;
  color: white;
}

.role-badge.customer {
  background: #42b883;
  color: white;
}

.btn-delete {
  padding: 0.5rem 1rem;
  background: #e74c3c;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
}

.btn-delete:hover {
  background: #c0392b;
}

.no-action {
  color: #999;
  font-size: 0.9rem;
}

.empty-state {
  text-align: center;
  padding: 3rem;
  color: #666;
  background: white;
  border-radius: 8px;
  margin-top: 1rem;
}

@media (max-width: 768px) {
  .user-management {
    padding: 1rem;
  }
  
  .filters {
    flex-direction: column;
  }
  
  .users-table {
    font-size: 0.8rem;
  }
  
  .users-table th,
  .users-table td {
    padding: 0.5rem;
  }
}
</style>
