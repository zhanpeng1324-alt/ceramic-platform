<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/services/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

const handleLogin = async () => {
  if (!username.value || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  error.value = ''

  try {
    const result = await api.loginService(username.value, password.value)
    userStore.login(result.user, result.token)
    if (result.user.role === 'admin') {
      router.push('/admin')
    } else {
      router.push('/service/dashboard')
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '登录失败，请重试'
  } finally {
    loading.value = false
  }
}

const goToCustomerLogin = () => {
  router.push('/login')
}
</script>

<template>
  <div class="service-login-page">
    <div class="login-container">
      <div class="login-header">
        <h1>后台管理系统</h1>
        <p>客服 / 管理员登录</p>
      </div>

      <form @submit.prevent="handleLogin" class="login-form">
        <div class="form-group">
          <label for="username">用户名</label>
          <input
            id="username"
            v-model="username"
            type="text"
            placeholder="请输入用户名"
            class="form-input"
          />
        </div>

        <div class="form-group">
          <label for="password">密码</label>
          <input
            id="password"
            v-model="password"
            type="password"
            placeholder="请输入密码"
            class="form-input"
          />
        </div>

        <div v-if="error" class="error-message">{{ error }}</div>

        <button type="submit" class="btn btn-primary" :disabled="loading">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>

      <div class="demo-info">
        <p><strong>管理员：</strong></p>
        <p>用户名：admin</p>
        <p>密码：admin</p>
        <hr style="margin: 0.75rem 0; border: none; border-top: 1px solid #ddd;" />
        <p><strong>客服：</strong></p>
        <p>用户名：service</p>
        <p>密码：admin</p>
      </div>

      <div class="login-footer">
        <button @click="goToCustomerLogin" class="link-button">
          返回消费者登录
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.service-login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-container {
  background: white;
  border-radius: 12px;
  padding: 2.5rem;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
  width: 100%;
  max-width: 400px;
}

.login-header {
  text-align: center;
  margin-bottom: 2rem;
}

.login-header h1 {
  margin: 0 0 0.5rem 0;
  color: #2c3e50;
  font-size: 1.8rem;
}

.login-header p {
  margin: 0;
  color: #666;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.form-group label {
  font-weight: bold;
  color: #333;
}

.form-input {
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 1rem;
  transition: border-color 0.2s;
}

.form-input:focus {
  outline: none;
  border-color: #667eea;
}

.error-message {
  color: #e74c3c;
  font-size: 0.9rem;
  text-align: center;
}

.btn {
  padding: 0.75rem;
  border: none;
  border-radius: 6px;
  font-size: 1rem;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.demo-info {
  background: #f9fafb;
  border-radius: 8px;
  padding: 1rem;
  margin-top: 1.5rem;
  text-align: center;
}

.demo-info p {
  margin: 0.25rem 0;
  font-size: 0.85rem;
  color: #666;
}

.demo-info strong {
  color: #333;
}

.login-footer {
  margin-top: 1.5rem;
  text-align: center;
}

.link-button {
  background: none;
  border: none;
  color: #667eea;
  cursor: pointer;
  font-size: 0.9rem;
  text-decoration: underline;
}

.link-button:hover {
  color: #764ba2;
}
</style>