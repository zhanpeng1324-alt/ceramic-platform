<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const navItems = [
  { path: '/admin', label: '管理后台', icon: '🏠' },
  { path: '/admin/users', label: '用户管理', icon: '👥' },
  { path: '/admin/products', label: '商品管理', icon: '🏺' },
  { path: '/admin/seckill', label: '秒杀管理', icon: '⚡' },
  { path: '/admin/restock', label: '备货建议', icon: '📦' },
  { path: '/admin/orders', label: '订单管理', icon: '📋' },
  { path: '/admin/reviews', label: '评价管理', icon: '⭐' },
  { path: '/admin/returns', label: '售后管理', icon: '🔧' },
  { path: '/admin/customizations', label: '定制管理', icon: '🎨' },
  { path: '/admin/settings', label: '店铺设置', icon: '🏪' },
]

const isActive = (path: string) => route.path === path || route.path.startsWith(path + '/')

const logout = () => {
  userStore.logout()
  router.push('/service/login')
}
</script>

<template>
  <nav class="admin-navbar">
    <div class="navbar-brand" @click="router.push('/admin')">
      <span class="brand-icon">👑</span>
      <span class="brand-name">管理后台</span>
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
      <span class="user-info">管理员: {{ userStore.currentUser?.nickname }}</span>
      <button class="nav-item logout-btn" @click="logout">退出登录</button>
    </div>
  </nav>
</template>

<style scoped>
.admin-navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1.5rem;
  background: #2c3e50;
  color: white;
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
  color: #bdc3c7;
  transition: background 0.2s;
}

.nav-item:hover {
  background: rgba(255,255,255,0.1);
  color: white;
}

.nav-item.active {
  color: #3498db;
  font-weight: bold;
  background: rgba(52, 152, 219, 0.2);
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
  color: #bdc3c7;
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
