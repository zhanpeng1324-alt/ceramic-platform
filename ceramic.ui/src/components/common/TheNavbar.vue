<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notification'

const router = useRouter()
const route = useRoute()
const cartStore = useCartStore()
const userStore = useUserStore()
const notificationStore = useNotificationStore()

const navItems = computed(() => {
  if (userStore.currentUser?.role === 'admin') {
    return [
      { path: '/admin', label: '管理后台' },
      { path: '/admin/users', label: '用户管理' },
      { path: '/admin/products', label: '商品管理' },
      { path: '/admin/seckill', label: '秒杀管理' },
      { path: '/admin/orders', label: '订单管理' },
      { path: '/admin/reviews', label: '评价管理' },
      { path: '/admin/support', label: '售后管理' },
    ]
  }
  return [
    { path: '/', label: '首页' },
    { path: '/products', label: '商品' },
    { path: '/customize', label: '定制' },
    { path: '/seckill', label: '秒杀' },
    { path: '/my-customizations', label: '我的定制' },
    { path: '/cart', label: '购物车' },
    { path: '/orders', label: '订单' },
    { path: '/chat', label: '客服' },
    { path: '/aftersales', label: '售后' },
  ]
})

const isActive = (path: string) => route.path === path

const cartCount = computed(() => cartStore.totalCount)

// 仅对已登录的顾客展示通知铃铛
const showBell = computed(() => userStore.isLoggedIn && userStore.currentUser?.role === 'customer')
const unreadCount = computed(() => notificationStore.unreadCount)

watch(
  () => userStore.isLoggedIn && userStore.currentUser?.role === 'customer',
  (on) => {
    if (on) notificationStore.start()
    else notificationStore.stop()
  }
)

onMounted(() => {
  if (showBell.value) notificationStore.start()
})

const handleLogout = () => {
  notificationStore.stop()
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <nav class="navbar">
    <div class="navbar-content">
      <div class="navbar-brand" @click="router.push(userStore.currentUser?.role === 'admin' ? '/admin' : '/')">
        <span class="ui-seal brand-seal">陶</span>
        <span class="brand-name">陶瓷定制平台</span>
      </div>
      
      <div class="navbar-nav">
        <button 
          v-for="item in navItems" 
          :key="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
          @click="router.push(item.path)"
        >
          {{ item.label }}
          <span v-if="item.path === '/cart' && cartCount > 0" class="cart-badge">{{ cartCount }}</span>
        </button>
      </div>

      <div class="navbar-right">
        <template v-if="userStore.isLoggedIn">
          <button
            v-if="showBell"
            class="nav-item bell-btn"
            :class="{ active: isActive('/notifications') }"
            title="消息通知"
            @click="router.push('/notifications')"
          >
            🔔
            <span v-if="unreadCount > 0" class="cart-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
          </button>
          <button v-if="userStore.currentUser?.role === 'admin'" class="nav-item admin-badge">
            👑 管理员
          </button>
          <button class="nav-item" @click="router.push('/profile')">
            {{ userStore.currentUser?.nickname || userStore.currentUser?.username }}
          </button>
          <button class="nav-item" @click="handleLogout">退出</button>
        </template>
        <template v-else>
          <button class="nav-item" @click="router.push('/login')">登录</button>
          <button class="nav-item primary" @click="router.push('/register')">注册</button>
        </template>
      </div>
    </div>
  </nav>
</template>

<style scoped>
.navbar {
  background: var(--c-surface);
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  /* 底边描金细线（青瓷底 + 金线） */
  border-bottom: 2px solid var(--c-gold-soft);
  position: sticky;
  top: 0;
  z-index: 100;
}

.navbar-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 1rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 60px;
}

.navbar-right {
  display: flex;
  gap: 0.5rem;
}

.navbar-brand {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  cursor: pointer;
}

.brand-seal {
  width: 32px;
  height: 32px;
  font-size: 1.05rem;
}

.brand-name {
  font-size: 1.2rem;
  font-weight: bold;
  font-family: var(--font-serif);
  letter-spacing: 0.04em;
  color: var(--c-primary-deep);
}

.navbar-nav {
  display: flex;
  gap: 0.5rem;
}

.nav-item {
  padding: 0.5rem 1rem;
  border: none;
  background: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 1rem;
  color: var(--c-text-muted);
  position: relative;
  transition: background 0.15s, color 0.15s;
}

.nav-item:hover {
  background: var(--c-primary-soft);
  color: var(--c-text);
}

.nav-item.active {
  color: var(--c-primary-deep);
  font-weight: bold;
}

/* 青瓷下划线 + 居中描金点，替代纯色高亮 */
.nav-item.active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 2px;
  transform: translateX(-50%);
  width: 60%;
  height: 2px;
  border-radius: 2px;
  background: var(--c-primary);
}

.nav-item.active::before {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  transform: translateX(-50%);
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--c-gold);
}

.nav-item.primary {
  background: var(--c-primary);
  color: white;
}

.nav-item.primary:hover {
  background: var(--c-primary-dark);
  color: white;
}

.nav-item.admin-badge {
  background: #f39c12;
  color: white;
  font-weight: bold;
}

.nav-item.admin-badge:hover {
  background: #e67e22;
  color: white;
}

.cart-badge {
  position: absolute;
  top: 2px;
  right: 2px;
  background: var(--c-accent);
  color: white;
  border-radius: 50%;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  font-size: 0.7rem;
  display: flex;
  align-items: center;
  justify-content: center;
}

@media (max-width: 768px) {
  .navbar-nav {
    gap: 0.25rem;
  }
  
  .nav-item {
    padding: 0.5rem;
    font-size: 0.9rem;
  }
  
  .brand-name {
    font-size: 1rem;
  }
}
</style>