<script setup lang="ts">import { ref, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '@/services/api';
import { useUserStore } from '@/stores/user';
const router = useRouter();
const userStore = useUserStore();
const phone = ref('');
const smsCode = ref('');
const demoCode = ref('');
const countdown = ref(0);
const error = ref('');
const loading = ref(false);
let timer: ReturnType<typeof setInterval> | null = null;
onUnmounted(() => { if (timer) clearInterval(timer); });
const handleSendCode = async () => {
 error.value = '';
 demoCode.value = '';
 if (!phone.value.match(/^\d{11}$/)) {
 error.value = '请输入正确的11位手机号';
 return;
 }
 try {
 const res = await api.sendSmsCode(phone.value);
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
 }
 catch (e: any) {
 error.value = e.message || '验证码发送失败';
 }
};
const handleRegister = async () => {
 error.value = '';
 if (!phone.value.match(/^\d{11}$/)) {
 error.value = '请输入正确的11位手机号';
 return;
 }
 if (!smsCode.value.match(/^\d{6}$/)) {
 error.value = '请输入6位短信验证码';
 return;
 }
 loading.value = true;
 try {
 // 注册只需手机号+验证码；登录同样可用验证码通道
 const result = await api.register({
 phone: phone.value,
 password: '',
 code: smsCode.value,
 });
 userStore.login(result.user, result.token);
 router.push('/');
 }
 catch (e: any) {
 error.value = e.message || '注册失败';
 }
 finally {
 loading.value = false;
 }
};
</script>

<template>
  <main class="auth-container">
    <div class="auth-card">
      <div class="auth-header">
        <div class="brand-mark">瓷</div>
        <h1>用户注册</h1>
        <p>创建您的陶瓷平台账号</p>
      </div>

      <div v-if="error" class="error">{{ error }}</div>

      <form @submit.prevent="handleRegister" class="auth-form">
        <div class="form-group">
          <label>手机号 <span class="required">*</span></label>
          <input
            v-model="phone"
            type="tel"
            placeholder="请输入11位手机号"
            autocomplete="tel"
            maxlength="11"
          />
        </div>

        <div class="form-group">
          <label>短信验证码 <span class="required">*</span></label>
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

        <div class="form-hint">注册后使用手机号 + 验证码登录，无需记忆密码</div>

        <button type="submit" class="auth-btn" :disabled="loading">
          <span v-if="loading">注册中...</span>
          <span v-else>注册并登录</span>
        </button>
      </form>

      <div class="auth-footer">
        <span>已有账号？</span>
        <button @click="router.push('/login')" class="link-btn">立即登录</button>
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

.auth-header {
  text-align: center;
  margin-bottom: var(--sp-6);
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

@keyframes cardIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.error {
  background: var(--c-primary-soft);
  color: var(--c-accent-dark);
  border: 1px solid var(--c-accent);
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

.required {
  color: var(--c-accent);
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
  margin-bottom: var(--sp-2);
}

.demo-code strong {
  font-size: 1.05rem;
  letter-spacing: 2px;
}

.form-hint {
  color: var(--c-text-muted);
  font-size: 0.82rem;
  text-align: center;
  margin-top: calc(-1 * var(--sp-2));
}

.form-group input::placeholder {
  color: var(--c-text-muted);
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
}

.link-btn:hover {
  color: var(--c-primary-dark);
}
</style>
