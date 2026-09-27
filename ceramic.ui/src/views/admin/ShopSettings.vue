<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { api } from '@/services/api'
import type { ShopSettings } from '@/types'
import AdminNavbar from '@/components/admin/AdminNavbar.vue'

const loading = ref(true)
const saving = ref(false)
const message = ref('')
const messageType = ref<'ok' | 'err'>('ok')
const updatedAt = ref<string | undefined>(undefined)

const form = reactive<ShopSettings>({
  shopName: '',
  contactName: '',
  contactPhone: '',
  address: '',
})

const load = async () => {
  loading.value = true
  try {
    const s = await api.shopSettings()
    form.shopName = s.shopName || ''
    form.contactName = s.contactName || ''
    form.contactPhone = s.contactPhone || ''
    form.address = s.address || ''
    updatedAt.value = s.updatedAt
  } catch (e) {
    showMsg((e as Error).message || '加载失败', 'err')
  } finally {
    loading.value = false
  }
}

const showMsg = (text: string, type: 'ok' | 'err') => {
  message.value = text
  messageType.value = type
  if (type === 'ok') {
    setTimeout(() => { if (message.value === text) message.value = '' }, 3000)
  }
}

// 中国大陆 11 位手机号
const PHONE_RE = /^1[3-9]\d{9}$/

// 输入时只保留数字并限制 11 位
const onPhoneInput = (e: Event) => {
  const v = (e.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 11)
  form.contactPhone = v
}

const save = async () => {
  const phone = form.contactPhone?.trim() || ''
  if (!PHONE_RE.test(phone)) {
    showMsg('请填写正确的 11 位手机号', 'err')
    return
  }
  saving.value = true
  message.value = ''
  try {
    const saved = await api.updateShopSettings({
      shopName: form.shopName?.trim(),
      contactName: form.contactName?.trim(),
      contactPhone: phone,
      address: form.address?.trim(),
    })
    updatedAt.value = saved.updatedAt
    showMsg('店铺设置已保存', 'ok')
  } catch (e) {
    showMsg((e as Error).message || '保存失败', 'err')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="settings-page">
    <AdminNavbar />
    <main class="content">
      <header class="page-header">
        <div class="header-left">
          <h1>店铺设置</h1>
          <p>寄件人信息将作为订单的发货地址，并作为售后退货的默认收货地址</p>
        </div>
        <button class="btn btn-refresh" @click="load" :disabled="loading">刷新</button>
      </header>

      <div v-if="loading" class="state-box">加载中...</div>

      <div v-else class="card">
        <div class="field">
          <label>店铺名称</label>
          <input v-model="form.shopName" type="text" placeholder="如：青瓷坊" maxlength="100" />
        </div>
        <div class="field">
          <label>寄件人 / 联系人 <span class="req">*</span></label>
          <input v-model="form.contactName" type="text" placeholder="发货人姓名" maxlength="50" />
        </div>
        <div class="field">
          <label>联系电话 <span class="req">*</span></label>
          <input
            v-model="form.contactPhone"
            type="text"
            inputmode="numeric"
            placeholder="11 位手机号，如 13800138000"
            maxlength="11"
            @input="onPhoneInput"
          />
          <span class="field-hint">寄件方电话，需为真实 11 位手机号</span>
        </div>
        <div class="field">
          <label>店铺地址（发货地 / 默认退货地址） <span class="req">*</span></label>
          <textarea v-model="form.address" rows="3" placeholder="省 市 区 详细地址" maxlength="255"></textarea>
        </div>

        <div class="footer">
          <span v-if="message" class="msg" :class="messageType === 'ok' ? 'msg-ok' : 'msg-err'">{{ message }}</span>
          <span v-else-if="updatedAt" class="updated">上次更新：{{ updatedAt }}</span>
          <button class="btn btn-save" @click="save" :disabled="saving">
            {{ saving ? '保存中...' : '保存设置' }}
          </button>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.settings-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.content {
  flex: 1;
  max-width: 760px;
  width: 100%;
  margin: 0 auto;
  padding: 24px 28px;
  box-sizing: border-box;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-left h1 {
  margin: 0;
  color: #243b53;
  font-size: 1.5rem;
}

.header-left p {
  margin: 4px 0 0;
  color: #718096;
  font-size: 0.9rem;
}

.btn {
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.9rem;
  padding: 0.5rem 1rem;
  transition: opacity 0.2s;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-refresh {
  background: #e2e8f0;
  color: #2c3e50;
}

.btn-save {
  background: #42b883;
  color: #fff;
  padding: 0.6rem 1.6rem;
}

.state-box {
  background: #fff;
  border-radius: 8px;
  padding: 40px;
  text-align: center;
  color: #718096;
}

.card {
  background: #fff;
  border-radius: 10px;
  padding: 28px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.field {
  margin-bottom: 20px;
}

.field label {
  display: block;
  margin-bottom: 8px;
  color: #2c3e50;
  font-size: 0.9rem;
  font-weight: 600;
}

.req {
  color: #e74c3c;
}

.field-hint {
  display: block;
  margin-top: 6px;
  color: #a0aec0;
  font-size: 0.8rem;
}

.field input,
.field textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #d9e2ec;
  border-radius: 6px;
  padding: 0.6rem 0.75rem;
  font-size: 0.95rem;
  font-family: inherit;
  color: #2c3e50;
  resize: vertical;
}

.field input:focus,
.field textarea:focus {
  outline: none;
  border-color: #42b883;
}

.footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  margin-top: 8px;
}

.msg {
  font-size: 0.9rem;
}

.msg-ok {
  color: #42b883;
}

.msg-err {
  color: #e74c3c;
}

.updated {
  color: #a0aec0;
  font-size: 0.85rem;
}
</style>
