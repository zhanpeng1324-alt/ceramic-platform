<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const navItems = [
  { path: '/service/dashboard', label: '工作台', icon: '📊' },
  { path: '/service/chat', label: '对话管理', icon: '💬' },
  { path: '/service/tickets', label: '工单处理', icon: '🎫' },
  { path: '/service/returns', label: '售后协助', icon: '📦' },
  { path: '/service/orders', label: '订单查询', icon: '📋' },
  { path: '/service/users', label: '用户列表', icon: '👥' },
]

const isActive = (path: string) => route.path === path || route.path.startsWith(path + '/')

const logout = () => {
  userStore.logout()
  router.push('/service/login')
}
</script>

<template>
  <nav class="service-navbar">
    <div class="navbar-brand" @click="router.push('/service/dashboard')">
      <span class="brand-icon">🏺</span>
      <span class="brand-name">客服工作台</span>
    </div>
    
    <div class="navbar-nav">
      <button 
        v-for="item in navItems" 
        :key="item.path"
        class="nav-item"
        :class="{ active: isActive(item.path) }"
        @click="router.push(item.path)"
      >
        <span class="nav-icon">{{ item.icon }}</span>
        <span>{{ item.label }}</span>
      </button>
    </div>

    <div class="navbar-right">
      <span class="user-info">欢迎, {{ userStore.currentUser?.nickname }}</span>
      <button class="nav-item logout-btn" @click="logout">退出登录</button>
    </div>
  </nav>
</template>

<style scoped>
.service-navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1.5rem;
  background: white;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.navbar-brand {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  cursor: pointer;
}

.brand-icon {
  font-size: 1.3rem;
}

.brand-name {
  font-size: 1.1rem;
  font-weight: bold;
  color: #2c3e50;
}

.navbar-nav {
  display: flex;
  gap: 0.25rem;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.5rem 0.75rem;
  border: none;
  background: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
  color: #666;
  transition: background 0.2s;
}

.nav-item:hover {
  background: #f8f9fa;
  color: #333;
}

.nav-item.active {
  color: #667eea;
  font-weight: bold;
  background: #f0f0ff;
}

.nav-icon {
  font-size: 1rem;
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.user-info {
  color: #666;
  font-size: 0.9rem;
}

.logout-btn {
  background: #e74c3c;
  color: white;
  padding: 0.5rem 1rem;
}

.logout-btn:hover {
  background: #c0392b;
  color: white;
}

@media (max-width: 768px) {
  .navbar-nav {
    gap: 0.1rem;
  }
  
  .nav-item {
    padding: 0.4rem 0.5rem;
    font-size: 0.8rem;
  }
  
  .brand-name {
    font-size: 0.9rem;
  }
  
  .user-info {
    display: none;
  }
}
</style>
