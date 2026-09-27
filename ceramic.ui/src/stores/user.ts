import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User } from '@/types'
import { api } from '@/services/api'
import { realtime } from '@/services/realtime'

export const useUserStore = defineStore('user', () => {
  const currentUser = ref<User | null>(null)

  const isLoggedIn = computed(() => !!currentUser.value && !!localStorage.getItem('token'))

  const isCustomer = computed(() => {
    return isLoggedIn.value && currentUser.value?.role === 'customer'
  })

  const isService = computed(() => {
    return isLoggedIn.value && currentUser.value?.role === 'service'
  })

  const isAdmin = computed(() => {
    return isLoggedIn.value && currentUser.value?.role === 'admin'
  })

  const login = (user: User, token: string) => {
    currentUser.value = user
    localStorage.setItem('user', JSON.stringify(user))
    api.setToken(token)
  }

  const logout = () => {
    currentUser.value = null
    localStorage.removeItem('user')
    api.clearToken()
    // 断开实时连接并清空全部订阅（通知 / 聊天共用同一连接）
    realtime.disconnect()
  }

  const setUser = (user: User | null) => {
    currentUser.value = user
    if (user) localStorage.setItem('user', JSON.stringify(user))
  }

  const initUser = () => {
    const token = localStorage.getItem('token')
    const saved = localStorage.getItem('user')
    if (token && saved) {
      try {
        currentUser.value = JSON.parse(saved)
      } catch {
        localStorage.removeItem('user')
        api.clearToken()
      }
    }
  }

  return {
    currentUser,
    isLoggedIn,
    isCustomer,
    isService,
    isAdmin,
    login,
    logout,
    setUser,
    initUser,
  }
})
