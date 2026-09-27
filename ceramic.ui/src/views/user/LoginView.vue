<script setup lang="ts">import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '@/services/api';
import { useUserStore } from '@/stores/user';
const router = useRouter();
const userStore = useUserStore();
const mode = ref<'password' | 'sms'>('password');
const username = ref('');
const password = ref('');
const error = ref('');
const loading = ref(false);
const handleLogin = async () => {
 error.value = '';
 if (!username.value || !password.value) {
 error.value = '请输入用户名和密码';
 return;
 }
 loading.value = true;
 try {
 const result = await api.login(username.value, password.value);
 userStore.login(result.user, result.token);
 if (result.user.role === 'admin') {
 router.push('/admin');
 } else if (result.user.role === 'service') {
 router.push('/service/dashboard');
 }
 else {
 router.push('/');
 }
 }
 catch (e: any) {
 error.value = e.message || '登录失败';
 }
 finally {
 loading.value = false;
 }
};

// 短信验证码登录
const smsPhone = ref('');
const smsCode = ref('');
const smsLoading = ref(false);
const countdown = ref(0);
const demoCode = ref('');
let timer: ReturnType<typeof setInterval> | null = null;
const handleSendCode = async () => {
 error.value = '';
 demoCode.value = '';
 if (!/^1\d{10}$/.test(smsPhone.value)) {
 error.value = '请输入正确的11位手机号';
 return;
 }
 try {
 const res = await api.sendSmsCode(smsPhone.value);
 // 演示模式：验证码直接回显（生产环境由真实短信通道下发）
 if (res.code) demoCode.value = res.code;
 countdown.value = 60;
 timer = setInterval(() => {
 countdown.value--;
 if (countdown.value <= 0 && timer) {
 clearInterval(timer);
 timer = null;
 }
 }, 1000);
 } catch (e: any) {
 error.value = e.message || '验证码发送失败';
 }
};
const handleSmsLogin = async () => {
 error.value = '';
 if (!/^1\d{10}$/.test(smsPhone.value)) {
 error.value = '请输入正确的11位手机号';
 return;
 }
 if (!/^\d{6}$/.test(smsCode.value)) {
 error.value = '请输入6位验证码';
 return;
 }
 smsLoading.value = true;
 try {
 const result = await api.smsLogin(smsPhone.value, smsCode.value);
 userStore.login(result.user, result.token);
 if (result.user.role === 'admin') {
 router.push('/admin');
 } else if (result.user.role === 'service') {
 router.push('/service/dashboard');
 } else {
 router.push('/');
 }
 }
 catch (e: any) {
 error.value = e.message || '登录失败';
 }
 finally {
 smsLoading.value = false;
 }
};
</script>

<template>
  <main class="auth-container">
    <div class="auth-card">
      <div class="auth-header">
        <div class="brand-mark">瓷</div>
        <h1>欢迎登录</h1>
        <p>陶瓷定制购物平台</p>
      </div>

      <div class="mode-tabs">
        <button type="button" :class="{ active: mode === 'password' }" @click="mode = 'password'; error = ''">密码登录</button>
        <button type="button" :class="{ active: mode === 'sms' }" @click="mode = 'sms'; error = ''">验证码登录</button>
      </div>

      <div v-if="error" class="error">{{ error }}</div>

      <form v-if="mode === 'password'" @submit.prevent="handleLogin" class="auth-form">
        <div class="form-group">
          <label>用户名</label>
          <input
            v-model="username"
            type="text"
            placeholder="请输入用户名"
            autocomplete="username"
          />
        </div>

        <div class="form-group">
          <label>密码</label>
          <input
            v-model="password"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </div>

        <button type="submit" class="auth-btn" :disabled="loading">
          <span v-if="loading">登录中...</span>
          <span v-else>登录</span>
        </button>
      </form>

      <form v-else @submit.prevent="handleSmsLogin" class="auth-form">
        <div class="form-group">
          <label>手机号</label>
          <input
            v-model="smsPhone"
            type="tel"
            maxlength="11"
            placeholder="请输入11位手机号"
            autocomplete="tel"
          />
        </div>

        <div class="form-group">
          <label>验证码</label>
          <div class="code-row">
            <input
              v-model="smsCode"
              type="text"
              maxlength="6"
              placeholder="6位验证码"
              autocomplete="one-time-code"
            />
            <button type="button" class="code-btn" :disabled="countdown > 0" @click="handleSendCode">
              {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
            </button>
          </div>
        </div>

        <div v-if="demoCode" class="demo-code">
          演示环境：验证码 <strong>{{ demoCode }}</strong>（生产环境由短信下发）
        </div>

        <button type="submit" class="auth-btn" :disabled="smsLoading">
          <span v-if="smsLoading">登录中...</span>
          <span v-else>登录</span>
        </button>
      </form>

      <div class="auth-footer">
        <span>还没有账号？</span>
        <button @click="router.push('/register')" class="link-btn">立即注册</button>
      </div>
    </div>
  </main>
</template>

<style scoped>
.auth-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background:
    radial-gradient(1000px 500px at 85% -10%, var(--c-primary-soft), transparent 60%),
    radial-gradient(800px 400px at -10% 100%, var(--c-primary-soft), transparent 55%),
    var(--c-bg);
  padding: var(--sp-5);
}

.auth-card {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 16px;
  padding: var(--sp-7, 2.5rem) var(--sp-6);
  width: 100%;
  max-width: 400px;
  box-shadow: 0 12px 32px rgba(31, 45, 40, 0.10), 0 2px 8px rgba(31, 45, 40, 0.05);
  animation: cardIn 0.35s ease;
}

@keyframes cardIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.auth-header {
  text-align: center;
  margin-bottom: var(--sp-5);
}

.brand-mark {
  width: 56px;
  height: 56px;
  margin: 0 auto var(--sp-3);
  border-radius: 50%;
  background: linear-gradient(135deg, var(--c-primary), var(--c-primary-dark));
  color: #fff;
  font-size: 1.5rem;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 16px rgba(31, 45, 40, 0.16);
  user-select: none;
}

.auth-header h1 {
  font-size: 1.7rem;
  letter-spacing: 2px;
  color: var(--c-text);
  margin: 0 0 var(--sp-1);
}

.auth-header p {
  color: var(--c-text-muted);
  font-size: 0.9rem;
  letter-spacing: 3px;
  margin: 0;
}

.error {
  background: var(--c-primary-soft);
  color: var(--c-accent-dark);
  padding: var(--sp-3);
  border-radius: var(--radius-sm);
  margin-bottom: var(--sp-4);
  text-align: center;
  animation: cardIn 0.25s ease;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
  animation: cardIn 0.25s ease;
}

.mode-tabs {
  display: flex;
  gap: var(--sp-2);
  margin-bottom: var(--sp-4);
  border-bottom: 1px solid var(--c-border);
}

.mode-tabs button {
  flex: 1;
  background: none;
  border: none;
  padding: var(--sp-3);
  font-size: 0.95rem;
  color: var(--c-text-muted);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: color 0.2s, border-color 0.2s;
}

.mode-tabs button:hover {
  color: var(--c-text);
}

.mode-tabs button.active {
  color: var(--c-primary);
  font-weight: 600;
  border-bottom-color: var(--c-primary);
}

.code-row {
  display: flex;
  gap: var(--sp-2);
}

.code-row input {
  flex: 1;
  min-width: 0;
}

.code-btn {
  white-space: nowrap;
  padding: 0 var(--sp-3);
  border: 1px solid var(--c-primary);
  background: var(--c-primary-soft);
  color: var(--c-primary-dark);
  border-radius: var(--radius-sm);
  font-size: 0.85rem;
  cursor: pointer;
  transition: opacity 0.2s;
}

.code-btn:hover:not(:disabled) {
  opacity: 0.85;
}

.code-btn:disabled {
  border-color: var(--c-border);
  background: var(--c-bg);
  color: var(--c-text-muted);
  cursor: not-allowed;
}

.demo-code {
  background: var(--c-primary-soft);
  color: var(--c-accent-dark);
  padding: var(--sp-3);
  border-radius: var(--radius-sm);
  font-size: 0.88rem;
  text-align: center;
}

.demo-code strong {
  font-size: 1.05rem;
  letter-spacing: 2px;
}

.form-group {
  display: flex;
  flex-direction: column;
}

.form-group label {
  font-size: 0.9rem;
  color: var(--c-text);
  margin-bottom: var(--sp-2);
  font-weight: 500;
}

.form-group input {
  padding: var(--sp-3);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-sm);
  font-size: 1rem;
  color: var(--c-text);
  background: var(--c-surface);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.form-group input:hover {
  border-color: var(--c-text-muted);
}

.form-group input:focus {
  outline: none;
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px var(--c-primary-soft);
}

.auth-btn {
  background: linear-gradient(135deg, var(--c-primary), var(--c-primary-dark));
  color: #fff;
  border: none;
  border-radius: var(--radius-sm);
  padding: var(--sp-4);
  font-size: 1rem;
  font-weight: bold;
  letter-spacing: 4px;
  cursor: pointer;
  margin-top: var(--sp-1);
  box-shadow: 0 4px 12px rgba(31, 45, 40, 0.14);
  transition: transform 0.15s ease, box-shadow 0.2s ease;
}

.auth-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 18px rgba(31, 45, 40, 0.18);
}

.auth-btn:active:not(:disabled) {
  transform: translateY(0);
}

.auth-btn:disabled {
  background: var(--c-border);
  box-shadow: none;
  color: var(--c-text-muted);
  cursor: not-allowed;
}

.auth-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-2);
  margin-top: var(--sp-5);
  color: var(--c-text-muted);
}

.link-btn {
  background: none;
  border: none;
  color: var(--c-primary);
  font-size: 0.95rem;
  cursor: pointer;
  text-decoration: underline;
  transition: color 0.2s;
}

.link-btn:hover {
  color: var(--c-primary-dark);
}
</style>