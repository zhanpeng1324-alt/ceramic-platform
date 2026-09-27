<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/services/api'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'
import type { Order } from '@/types'
import type { Payment } from '@/types'
import PaymentGatewayModal from '@/components/common/PaymentGatewayModal.vue'

const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()

interface CartItem {
  id: number
  userId?: number
  productId: number
  quantity: number
  productName?: string
  price?: number
  imageUrl?: string
  selectedOptions?: any
  createdAt: string
  updatedAt: string
}

const cartItems = ref<CartItem[]>([])
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const success = ref(false)
const paid = ref(false)
const showPayModal = ref(false)
const order = ref<Order | null>(null)
const payment = ref<Payment | null>(null)

const form = ref({
  receiverName: '',
  receiverPhone: '',
  receiverAddress: '',
  remark: '',
  payType: 'wechat' as 'wechat' | 'alipay' | 'bank',
})

const fillUserInfo = async () => {
  const userId = getUserId()
  
  try {
    const defaultAddress = await api.getDefaultAddress()
    if (defaultAddress) {
      form.value.receiverName = defaultAddress.receiverName
      form.value.receiverPhone = defaultAddress.receiverPhone
      form.value.receiverAddress = defaultAddress.address
      return
    }
  } catch {
    console.log('未找到默认地址，使用用户基本信息')
  }
  
  const user = userStore.currentUser
  if (user) {
    if (!form.value.receiverName) {
      form.value.receiverName = user.nickname || user.username || ''
    }
    if (!form.value.receiverPhone) {
      form.value.receiverPhone = user.phone || ''
    }
    if (!form.value.receiverAddress) {
      form.value.receiverAddress = user.address || ''
    }
  }
}

const payTypes = [
  { value: 'wechat', label: '微信支付', icon: '💳' },
  { value: 'alipay', label: '支付宝', icon: '📱' },
  { value: 'bank', label: '银行转账', icon: '🏦' },
]

const totalAmount = computed(() => {
  return cartItems.value.reduce((sum, item) => sum + (item.price || 0) * (item.quantity || 0), 0)
})

const totalCount = computed(() => {
  return cartItems.value.reduce((sum, item) => sum + (item.quantity || 0), 0)
})

const getUserId = () => {
  return userStore.currentUser?.id || 0
}

const loadCart = async () => {
  loading.value = true
  try {
    const items = await api.cart()
    // 勾选结算：只结算购物车里勾选的项；无勾选记录（直接进入本页）时按全量兜底
    const ids = cartStore.selectedIds
    cartItems.value = ids.size > 0 ? items.filter(i => i.id != null && ids.has(i.id)) : items
    if (cartItems.value.length === 0) {
      error.value = '没有已勾选的商品，请回购物车勾选后再结算'
    }
  } catch {
    error.value = '加载购物车失败'
  } finally {
    loading.value = false
  }
}

const submitOrder = async () => {
  if (!form.value.receiverName || !form.value.receiverPhone || !form.value.receiverAddress) {
    error.value = '请填写完整的收货信息'
    return
  }
  if (!/^1[3-9]\d{9}$/.test(form.value.receiverPhone.trim())) {
    error.value = '请填写正确的 11 位手机号'
    return
  }
  if (cartItems.value.length === 0) {
    error.value = '购物车为空'
    return
  }
  submitting.value = true
  error.value = ''
  try {
    // 只把本次结算的购物车项传给后端；下单成功后这些项从购物车移除，未勾选的保留
    const settledIds = cartItems.value.map(i => i.id).filter((x): x is number => x != null)
    const result = await api.createOrder({
      receiverName: form.value.receiverName,
      receiverPhone: form.value.receiverPhone,
      receiverAddress: form.value.receiverAddress,
      remark: form.value.remark,
      payType: form.value.payType,
      cartItemIds: settledIds.length > 0 ? settledIds : undefined,
    })
    order.value = result
    success.value = true
    await cartStore.loadCart()
  } catch (e: any) {
    error.value = e.message || '提交订单失败'
  } finally {
    submitting.value = false
  }
}

const goToOrders = () => {
  router.push('/orders')
}

const openPay = () => {
  if (!order.value) return
  error.value = ''
  showPayModal.value = true
}

// 收银台确认支付成功：记录流水、切换到已支付态
const onPaid = (p: Payment) => {
  payment.value = p
  paid.value = true
  showPayModal.value = false
}

const cancelPayment = () => {
  if (!order.value) return
  if (!confirm('取消本次支付？订单将保留为「待支付」，可稍后在「我的订单」中继续支付。')) return
  router.push('/orders')
}

const goShopping = () => {
  router.push('/products')
}

onMounted(async () => {
  await fillUserInfo()
  loadCart()
})
</script>

<template>
  <main class="page">
    <header class="page-header">
      <div>
        <h1 class="page-title">确认订单</h1>
        <p class="page-subtitle">请确认订单信息，完成支付</p>
      </div>
    </header>

    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="success && order" class="success-panel">
      <div class="success-icon">✓</div>
      <h2>{{ paid ? '支付成功！' : '订单已提交，等待支付' }}</h2>
      <p>订单号：{{ order.orderNo }}</p>
      <p>订单金额：¥{{ order.totalAmount }}</p>
      <div v-if="!paid" class="gateway-box">
        <p class="gateway-hint">订单已生成并保留为「待支付」，点击下方「去支付」进入模拟收银台完成付款；若暂不支付，可稍后在「我的订单」中继续。</p>
      </div>
      <div v-if="paid && payment" class="gateway-box paid">
        <div class="detail-row"><span>支付渠道</span><span>{{ payTypes.find(p => p.value === payment?.channel)?.label || payment.channel }}</span></div>
        <div class="detail-row"><span>交易流水号</span><span>{{ payment.transactionId }}</span></div>
        <div class="detail-row"><span>支付金额</span><span class="pay-amount">¥{{ payment.amount }}</span></div>
      </div>
      <div class="success-actions">
        <template v-if="!paid">
          <button @click="openPay" class="primary-btn">去支付</button>
          <button @click="cancelPayment" class="secondary-btn">稍后支付</button>
        </template>
        <template v-else>
          <button @click="goToOrders" class="primary-btn">查看订单</button>
          <button @click="goShopping" class="secondary-btn">继续购物</button>
        </template>
      </div>

      <PaymentGatewayModal
        :visible="showPayModal"
        biz-type="ORDER"
        :biz-id="order?.id ?? null"
        :amount="order?.totalAmount ?? 0"
        :initial-channel="form.payType"
        title="订单支付"
        @paid="onPaid"
        @close="showPayModal = false"
      />
    </div>

    <div v-else class="checkout-container">
      <div class="checkout-left">
        <section class="checkout-section">
          <h2>收货信息</h2>
          <div class="form-group">
            <label>收货人姓名</label>
            <input v-model="form.receiverName" placeholder="请输入收货人姓名" />
          </div>
          <div class="form-group">
            <label>联系电话</label>
            <input
              v-model="form.receiverPhone"
              type="text"
              inputmode="numeric"
              maxlength="11"
              placeholder="请输入 11 位手机号"
              @input="form.receiverPhone = form.receiverPhone.replace(/\D/g, '').slice(0, 11)"
            />
          </div>
          <div class="form-group">
            <label>收货地址</label>
            <textarea v-model="form.receiverAddress" placeholder="请输入详细收货地址" rows="3"></textarea>
          </div>
          <div class="form-group">
            <label>备注（选填）</label>
            <textarea v-model="form.remark" placeholder="请输入订单备注" rows="2"></textarea>
          </div>
        </section>

        <section class="checkout-section">
          <h2>支付方式</h2>
          <div class="pay-options">
            <label 
              v-for="payType in payTypes" 
              :key="payType.value"
              class="pay-option"
              :class="{ selected: form.payType === payType.value }"
            >
              <input type="radio" v-model="form.payType" :value="payType.value" />
              <span class="pay-icon">{{ payType.icon }}</span>
              <span class="pay-label">{{ payType.label }}</span>
            </label>
          </div>
        </section>
      </div>

      <div class="checkout-right">
        <section class="checkout-section order-summary">
          <h2>订单摘要</h2>
          <div class="cart-items-list" v-if="!loading">
            <div v-for="item in cartItems" :key="item.id" class="cart-item-row">
              <img :src="item.imageUrl || `/images/products/${item.productId}.jpg`" alt="" class="item-image" />
              <div class="item-info">
                <div class="item-name">{{ item.productName }}</div>
                <div class="item-price">¥{{ item.price }}</div>
              </div>
              <div class="item-quantity">×{{ item.quantity }}</div>
              <div class="item-total">¥{{ (item.price || 0) * (item.quantity || 0) }}</div>
            </div>
          </div>
          <div v-else class="loading">加载中...</div>

          <div class="summary-divider"></div>

          <div class="summary-row">
            <span>商品总数</span>
            <span>{{ totalCount }} 件</span>
          </div>
          <div class="summary-row">
            <span>订单金额</span>
            <span class="total-amount">¥{{ totalAmount }}</span>
          </div>

          <button 
            @click="submitOrder" 
            class="submit-btn"
            :disabled="submitting"
          >
            <span v-if="submitting">提交中...</span>
            <span v-else>提交订单</span>
          </button>
        </section>
      </div>
    </div>
  </main>
</template>

<style scoped>
.page {
  background: var(--c-bg);
}

.page-header {
  max-width: 1200px;
  margin: 0 auto var(--sp-5);
}

.page-title {
  margin: 0 0 var(--sp-1);
  color: var(--c-text);
}

.page-subtitle {
  margin: 0;
  color: var(--c-text-muted);
}

.error {
  max-width: 1200px;
  margin: 0 auto var(--sp-4);
  padding: var(--sp-3) var(--sp-4);
  background: var(--c-primary-soft);
  border: 1px solid var(--c-accent);
  border-radius: var(--radius-sm);
  color: var(--c-accent-dark);
}

.checkout-container {
  display: grid;
  grid-template-columns: 1fr 400px;
  gap: var(--sp-5);
  max-width: 1200px;
  margin: 0 auto;
}

.checkout-section {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  padding: var(--sp-5);
  margin-bottom: var(--sp-5);
  box-shadow: var(--shadow);
}

.checkout-section h2 {
  margin: 0 0 var(--sp-4);
  font-size: 1.1rem;
  color: var(--c-text);
}

.form-group {
  margin-bottom: var(--sp-4);
}

.form-group label {
  display: block;
  margin-bottom: var(--sp-2);
  font-size: 0.9rem;
  color: var(--c-text-muted);
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: var(--sp-3);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-sm);
  font-size: 0.95rem;
  color: var(--c-text);
  box-sizing: border-box;
}

.form-group input:focus,
.form-group textarea:focus {
  outline: none;
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px var(--c-primary-soft);
}

.form-group textarea {
  resize: vertical;
  min-height: 60px;
}

.pay-options {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.pay-option {
  display: flex;
  align-items: center;
  padding: var(--sp-4);
  border: 2px solid var(--c-border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.2s;
}

.pay-option:hover {
  border-color: var(--c-primary);
}

.pay-option.selected {
  border-color: var(--c-primary);
  background: var(--c-primary-soft);
}

.pay-option input {
  margin-right: var(--sp-3);
}

.pay-icon {
  font-size: 1.2rem;
  margin-right: var(--sp-3);
}

.pay-label {
  font-size: 0.95rem;
  color: var(--c-text);
}

.order-summary {
  position: sticky;
  top: 80px;
}

.cart-items-list {
  max-height: 300px;
  overflow-y: auto;
}

.cart-item-row {
  display: flex;
  align-items: center;
  padding: var(--sp-3) 0;
  border-bottom: 1px solid var(--c-border);
}

.cart-item-row:last-child {
  border-bottom: none;
}

.item-image {
  width: 60px;
  height: 60px;
  object-fit: cover;
  border-radius: var(--radius-sm);
  margin-right: var(--sp-3);
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-size: 0.9rem;
  color: var(--c-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: var(--sp-1);
}

.item-price {
  font-size: 0.85rem;
  color: var(--c-text-muted);
}

.item-quantity {
  font-size: 0.85rem;
  color: var(--c-text-muted);
  margin: 0 var(--sp-2);
}

.item-total {
  font-size: 0.9rem;
  font-weight: bold;
  color: var(--c-text);
  min-width: 60px;
  text-align: right;
}

.summary-divider {
  height: 1px;
  background: var(--c-border);
  margin: var(--sp-4) 0;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--sp-2);
  font-size: 0.95rem;
}

.summary-row span:first-child {
  color: var(--c-text-muted);
}

.total-amount {
  font-size: 1.3rem;
  font-weight: bold;
  color: var(--c-accent);
}

.submit-btn {
  width: 100%;
  background: var(--c-primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-sm);
  padding: var(--sp-4);
  font-size: 1rem;
  font-weight: bold;
  cursor: pointer;
  margin-top: var(--sp-4);
  transition: background 0.2s;
}

.submit-btn:hover:not(:disabled) {
  background: var(--c-primary-dark);
}

.submit-btn:disabled {
  background: var(--c-border);
  color: var(--c-text-muted);
  cursor: not-allowed;
}

.success-panel {
  text-align: center;
  padding: var(--sp-6);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  max-width: 500px;
  margin: 0 auto;
}

.success-icon {
  width: 80px;
  height: 80px;
  background: var(--c-primary);
  color: #fff;
  border-radius: 50%;
  font-size: 2.5rem;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto var(--sp-4);
  box-shadow: var(--shadow);
}

.success-panel h2 {
  color: var(--c-text);
  margin-bottom: var(--sp-2);
}

.success-panel p {
  color: var(--c-text-muted);
  margin: var(--sp-1) 0;
}

.success-actions {
  display: flex;
  gap: var(--sp-4);
  justify-content: center;
  margin-top: var(--sp-6);
}

.gateway-box {
  margin: var(--sp-5) auto 0;
  max-width: 340px;
  padding: var(--sp-4) var(--sp-5);
  background: var(--c-primary-soft);
  border: 1px dashed var(--c-primary);
  border-radius: var(--radius);
  text-align: left;
}

.gateway-box.paid {
  border-style: solid;
  border-color: var(--c-border);
  background: var(--c-bg);
}

.gateway-title {
  font-weight: 700;
  color: var(--c-primary-dark);
  margin-bottom: var(--sp-2);
}

.gateway-hint {
  font-size: 0.82rem;
  color: var(--c-text-muted);
  margin: 0;
}

.gateway-box .detail-row {
  display: flex;
  justify-content: space-between;
  padding: var(--sp-2) 0;
  font-size: 0.9rem;
  border-bottom: 1px solid var(--c-border);
}

.gateway-box .detail-row:last-child {
  border-bottom: none;
}

.gateway-box .detail-row span:first-child {
  color: var(--c-text-muted);
}

.pay-amount {
  color: var(--c-accent);
  font-weight: 700;
}

.primary-btn {
  background: var(--c-primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-sm);
  padding: var(--sp-3) var(--sp-6);
  font-size: 1rem;
  cursor: pointer;
  transition: background 0.2s;
}

.primary-btn:hover {
  background: var(--c-primary-dark);
}

.primary-btn:disabled {
  background: var(--c-border);
  color: var(--c-text-muted);
  cursor: not-allowed;
}

.secondary-btn {
  background: var(--c-surface);
  color: var(--c-primary);
  border: 1px solid var(--c-primary);
  border-radius: var(--radius-sm);
  padding: var(--sp-3) var(--sp-6);
  font-size: 1rem;
  cursor: pointer;
  transition: background 0.2s;
}

.secondary-btn:hover {
  background: var(--c-primary-soft);
}

.loading {
  text-align: center;
  padding: var(--sp-5);
  color: var(--c-text-muted);
}

@media (max-width: 768px) {
  .checkout-container {
    grid-template-columns: 1fr;
  }

  .order-summary {
    position: static;
  }
}
</style>
