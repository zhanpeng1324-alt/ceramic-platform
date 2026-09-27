<script setup lang="ts">
import { ref, watch } from 'vue'
import { api } from '@/services/api'
import type { Payment } from '@/types'

/**
 * 模拟支付网关收银台弹窗。
 * 复用于订单 / 定制定金 / 定制尾款 / 秒杀订单：选支付方式 → 确认支付 → 展示成功流水。
 * 实付金额由后端按业务单真实值计算，本组件的 amount 仅用于展示。
 */
const props = defineProps<{
  visible: boolean
  bizType: 'ORDER' | 'CUSTOM_DEPOSIT' | 'CUSTOM_BALANCE' | 'SECKILL'
  bizId: number | null
  amount: number | string
  title?: string
  initialChannel?: 'wechat' | 'alipay' | 'bank'
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'paid', payment: Payment): void
}>()

const payTypes = [
  { value: 'wechat', label: '微信支付', icon: '💳' },
  { value: 'alipay', label: '支付宝', icon: '📱' },
  { value: 'bank', label: '银行转账', icon: '🏦' },
] as const

const channel = ref<'wechat' | 'alipay' | 'bank'>('wechat')
const paying = ref(false)
const paid = ref(false)
const result = ref<Payment | null>(null)
const error = ref('')

// 每次打开重置状态
watch(
  () => props.visible,
  (v) => {
    if (v) {
      channel.value = props.initialChannel || 'wechat'
      paying.value = false
      paid.value = false
      result.value = null
      error.value = ''
    }
  },
)

const confirmPay = async () => {
  if (!props.bizId) return
  paying.value = true
  error.value = ''
  try {
    // 模拟支付网关回调延时，营造「正在网关支付」的过程感（非整页跳转）
    await new Promise((resolve) => setTimeout(resolve, 1200))
    // 发起支付流水 → 收银台确认入账
    result.value = await api.payViaGateway(props.bizType, props.bizId, channel.value)
    paid.value = true
    emit('paid', result.value)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '支付失败，请重试'
  } finally {
    paying.value = false
  }
}

const close = () => {
  if (paying.value) return
  emit('close')
}

const channelLabel = (v?: string) => payTypes.find((p) => p.value === v)?.label || v
</script>

<template>
  <div v-if="visible" class="pay-mask" @click.self="close">
    <div class="pay-modal">
      <div class="pay-header">
        <h3>{{ title || '模拟收银台' }}</h3>
        <button class="pay-close" :disabled="paying" @click="close">×</button>
      </div>

      <div class="pay-body">
        <div class="pay-amount-box">
          <span class="pay-amount-label">应付金额</span>
          <span class="pay-amount">¥{{ amount }}</span>
        </div>

        <p v-if="error" class="pay-error">{{ error }}</p>

        <!-- 支付成功 -->
        <template v-if="paid">
          <div class="pay-success">
            <div class="pay-success-icon">✓</div>
            <div class="pay-success-text">支付成功</div>
          </div>
          <div class="pay-detail">
            <div class="pay-detail-row"><span>支付渠道</span><span>{{ channelLabel(result?.channel) }}</span></div>
            <div class="pay-detail-row"><span>交易流水号</span><span>{{ result?.transactionId }}</span></div>
            <div class="pay-detail-row"><span>支付金额</span><span class="hl">¥{{ result?.amount }}</span></div>
          </div>
          <div class="pay-actions">
            <button class="pay-btn primary" @click="close">完成</button>
          </div>
        </template>

        <!-- 网关处理中 -->
        <template v-else-if="paying">
          <div class="pay-processing">
            <div class="pay-spinner"></div>
            <div class="pay-processing-text">正在通过{{ channelLabel(channel) }}完成付款…</div>
            <div class="pay-processing-sub">模拟支付网关处理中，请稍候</div>
          </div>
        </template>

        <!-- 选择支付方式 -->
        <template v-else>
          <div class="pay-section-title">选择支付方式</div>
          <div class="pay-options">
            <label
              v-for="p in payTypes"
              :key="p.value"
              class="pay-option"
              :class="{ selected: channel === p.value }"
            >
              <input type="radio" v-model="channel" :value="p.value" />
              <span class="pay-icon">{{ p.icon }}</span>
              <span class="pay-name">{{ p.label }}</span>
            </label>
          </div>
          <p class="pay-hint">
            这是用于毕设演示的模拟支付网关：点击「确认支付」发起流水并由服务端确认入账完成付款；网关回调仅限服务端重推，客户端无法伪造支付成功。
          </p>
          <div class="pay-actions">
            <button class="pay-btn ghost" @click="close">取消</button>
            <button class="pay-btn primary" @click="confirmPay">确认支付</button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pay-mask {
  position: fixed;
  inset: 0;
  background: rgba(36, 59, 83, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1100;
  padding: 1rem;
}

.pay-modal {
  background: #fff;
  border-radius: 12px;
  width: 100%;
  max-width: 380px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.25);
  overflow: hidden;
}

.pay-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.2rem;
  border-bottom: 1px solid #eef2ef;
}

.pay-header h3 {
  margin: 0;
  font-size: 1.05rem;
  color: #243b53;
}

.pay-close {
  border: 0;
  background: transparent;
  font-size: 1.5rem;
  cursor: pointer;
  color: #718096;
  line-height: 1;
}
.pay-close:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.pay-body {
  padding: 1.2rem;
}

.pay-amount-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.3rem;
  padding: 0.8rem 0 1.1rem;
}

.pay-amount-label {
  font-size: 0.82rem;
  color: #95a5a6;
}

.pay-amount {
  font-size: 2rem;
  font-weight: 700;
  color: #c1502e;
}

.pay-error {
  margin: 0 0 0.9rem;
  padding: 0.5rem 0.7rem;
  background: #fdecea;
  border: 1px solid #f5c6cb;
  border-radius: 6px;
  color: #922b21;
  font-size: 0.85rem;
}

.pay-section-title {
  font-size: 0.85rem;
  color: #718096;
  margin-bottom: 0.6rem;
}

.pay-options {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}

.pay-option {
  display: flex;
  align-items: center;
  padding: 0.7rem 0.9rem;
  border: 2px solid #e7e3dc;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
}
.pay-option:hover {
  border-color: #2e8b6f;
}
.pay-option.selected {
  border-color: #2e8b6f;
  background: #eaf5f0;
}
.pay-option input {
  margin-right: 0.7rem;
}
.pay-icon {
  font-size: 1.2rem;
  margin-right: 0.6rem;
}
.pay-name {
  font-size: 0.92rem;
  color: #2b2b2b;
}

.pay-hint {
  margin: 0.9rem 0 0;
  font-size: 0.76rem;
  color: #95a5a6;
  line-height: 1.5;
}

.pay-actions {
  display: flex;
  gap: 0.7rem;
  margin-top: 1.1rem;
}

.pay-btn {
  flex: 1;
  border: 0;
  border-radius: 8px;
  padding: 0.6rem 1rem;
  font-size: 0.92rem;
  cursor: pointer;
  transition: background 0.15s;
}
.pay-btn.primary {
  background: #2e8b6f;
  color: #fff;
}
.pay-btn.primary:hover {
  background: #256f59;
}
.pay-btn.primary:disabled {
  background: #a0c4b5;
  cursor: not-allowed;
}
.pay-btn.ghost {
  background: #fff;
  border: 1px solid #e7e3dc;
  color: #2b2b2b;
}
.pay-btn.ghost:hover {
  background: #f7f5f1;
}
.pay-btn.ghost:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.pay-processing {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.7rem;
  padding: 1.6rem 0 1.2rem;
}
.pay-spinner {
  width: 44px;
  height: 44px;
  border: 3px solid #eaf5f0;
  border-top-color: #2e8b6f;
  border-radius: 50%;
  animation: pay-spin 0.8s linear infinite;
}
@keyframes pay-spin {
  to {
    transform: rotate(360deg);
  }
}
.pay-processing-text {
  font-size: 0.98rem;
  font-weight: 600;
  color: #243b53;
}
.pay-processing-sub {
  font-size: 0.8rem;
  color: #95a5a6;
}

.pay-success {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.6rem;
  padding: 0.5rem 0 1rem;
}
.pay-success-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #2e8b6f;
  color: #fff;
  font-size: 1.8rem;
  display: flex;
  align-items: center;
  justify-content: center;
}
.pay-success-text {
  font-size: 1.05rem;
  font-weight: 600;
  color: #1e8449;
}

.pay-detail {
  background: #f9fbfa;
  border-radius: 8px;
  padding: 0.7rem 0.9rem;
}
.pay-detail-row {
  display: flex;
  justify-content: space-between;
  padding: 0.4rem 0;
  font-size: 0.86rem;
  color: #4a5568;
  border-bottom: 1px solid #eef2ef;
}
.pay-detail-row:last-child {
  border-bottom: none;
}
.pay-detail-row .hl {
  color: #c1502e;
  font-weight: 700;
}
</style>
