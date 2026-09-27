<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/services/api'
import { useUserStore } from '@/stores/user'
import type { Order, OrderItem, LogisticsTrace, ShopSettings } from '@/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const orderId = Number(route.params.id)
const order = ref<Order | null>(null)
const items = ref<OrderItem[]>([])
const traces = ref<LogisticsTrace[]>([])
const shopInfo = ref<ShopSettings | null>(null)
const loading = ref(true)
const error = ref('')

// 修改地址弹窗
const showAddressModal = ref(false)
const savingAddress = ref(false)
const addressForm = ref({ receiverName: '', receiverPhone: '', receiverAddress: '' })

const formatCurrency = (amount: number): string =>
  new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(amount || 0)

const formatDate = (dateString?: string): string => {
  if (!dateString) return '—'
  return new Date(dateString).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  })
}

const getOrderStatusName = (status: string): string => {
  switch (status) {
    case 'PENDING_PAY': return '待付款'
    case 'PAID': return '已付款'
    case 'SHIPPED': return '已发货'
    case 'COMPLETED': return '已完成'
    case 'CANCELLED': return '已取消'
    default: return status
  }
}

const getOrderStatusColor = (status: string): string => {
  switch (status) {
    case 'PENDING_PAY': return '#ffeaa7'
    case 'PAID': return '#74b9ff'
    case 'SHIPPED': return '#fdcb6e'
    case 'COMPLETED': return '#00b894'
    case 'CANCELLED': return '#fab1a0'
    default: return '#ddd'
  }
}

// 仅未发货订单（待付款/已付款）允许修改收货信息，与后端校验保持一致
const canEditAddress = computed(() =>
  order.value?.status === 'PENDING_PAY' || order.value?.status === 'PAID')

// 已取消订单不展示物流/发货信息（与淘宝一致：取消后无物流轨迹）
const isCancelled = computed(() => order.value?.status === 'CANCELLED')

const totalQuantity = computed(() => items.value.reduce((sum, it) => sum + it.quantity, 0))

const loadDetail = async () => {
  loading.value = true
  error.value = ''
  try {
    const res: any = await api.orderDetail(orderId)
    order.value = res.order
    items.value = res.items || []
    await loadLogistics()
  } catch (e: any) {
    error.value = e?.message || '获取订单详情失败'
  } finally {
    loading.value = false
  }
}

// 物流轨迹（未发货也会返回「待发货·所在仓库」节点）+ 寄件方信息，
// 均为旁路展示，失败不影响主流程
const loadLogistics = async () => {
  traces.value = []
  // 已取消订单无物流，直接跳过拉取
  if (order.value?.status === 'CANCELLED') return
  try {
    traces.value = await api.orderLogistics(orderId)
  } catch {
    traces.value = []
  }
  if (!shopInfo.value) {
    try {
      shopInfo.value = await api.shopSettings()
    } catch {
      shopInfo.value = null
    }
  }
}

const openAddressModal = () => {
  if (!order.value) return
  addressForm.value = {
    receiverName: order.value.receiverName || '',
    receiverPhone: order.value.receiverPhone || '',
    receiverAddress: order.value.receiverAddress || ''
  }
  showAddressModal.value = true
}

const closeAddressModal = () => {
  showAddressModal.value = false
}

const saveAddress = async () => {
  if (!addressForm.value.receiverName.trim()) { alert('请填写收货人姓名'); return }
  if (!addressForm.value.receiverPhone.trim()) { alert('请填写收货人电话'); return }
  if (!/^1[3-9]\d{9}$/.test(addressForm.value.receiverPhone.trim())) { alert('请填写正确的 11 位手机号'); return }
  if (!addressForm.value.receiverAddress.trim()) { alert('请填写收货地址'); return }
  savingAddress.value = true
  try {
    await api.updateOrderAddress(orderId, {
      receiverName: addressForm.value.receiverName.trim(),
      receiverPhone: addressForm.value.receiverPhone.trim(),
      receiverAddress: addressForm.value.receiverAddress.trim()
    })
    closeAddressModal()
    await loadDetail()
    alert('收货信息已更新')
  } catch (e: any) {
    alert(e?.message || '更新失败，请重试')
  } finally {
    savingAddress.value = false
  }
}

onMounted(async () => {
  if (!userStore.isLoggedIn) {
    alert('请先登录')
    router.push('/login')
    return
  }
  await loadDetail()
})
</script>

<template>
  <div class="order-detail-page">
    <div class="container">
      <button class="back-link" @click="router.push('/orders')">← 返回订单列表</button>

      <div v-if="loading" class="state-box">加载中...</div>
      <div v-else-if="error" class="state-box error">{{ error }}</div>

      <template v-else-if="order">
        <!-- 订单头部 -->
        <div class="detail-header">
          <div>
            <div class="order-no">订单号：{{ order.orderNo || '#' + order.id }}</div>
            <div class="order-date">下单时间：{{ formatDate(order.createdAt) }}</div>
          </div>
          <div class="order-status" :style="{ backgroundColor: getOrderStatusColor(order.status) }">
            {{ getOrderStatusName(order.status) }}
          </div>
        </div>

        <div class="detail-grid" :class="{ 'single-col': isCancelled }">
          <!-- 收货信息 -->
          <section class="panel">
            <div class="panel-head">
              <h2 class="ui-section-title">收货信息</h2>
              <button v-if="canEditAddress" class="link-btn" @click="openAddressModal">修改地址</button>
            </div>
            <div class="info-row"><span class="info-label">收货人</span><span class="info-value">{{ order.receiverName || '—' }}</span></div>
            <div class="info-row"><span class="info-label">联系电话</span><span class="info-value">{{ order.receiverPhone || '—' }}</span></div>
            <div class="info-row"><span class="info-label">收货地址</span><span class="info-value">{{ order.receiverAddress || '—' }}</span></div>
            <p v-if="!canEditAddress" class="lock-hint">订单已发货或已结束，收货信息不可修改。</p>
          </section>

          <!-- 物流信息（已取消订单不展示） -->
          <section v-if="!isCancelled" class="panel">
            <h2 class="ui-section-title">物流信息</h2>
            <template v-if="order.trackingNo">
              <div class="info-row"><span class="info-label">快递公司</span><span class="info-value">{{ order.shippingCompany || '—' }}</span></div>
              <div class="info-row"><span class="info-label">运单号</span><span class="info-value">{{ order.trackingNo }}</span></div>
              <div class="info-row"><span class="info-label">发货时间</span><span class="info-value">{{ formatDate(order.shipTime) }}</span></div>
              <div v-if="shopInfo && shopInfo.address" class="info-row">
                <span class="info-label">寄件方</span>
                <span class="info-value">{{ shopInfo.contactName || shopInfo.shopName || '商家' }}<template v-if="shopInfo.contactPhone"> · {{ shopInfo.contactPhone }}</template><br>{{ shopInfo.address }}</span>
              </div>
            </template>
            <div v-else class="info-row">
              <span class="info-label">当前位置</span>
              <span class="info-value">江西景德镇（商家发货仓）</span>
            </div>

            <div v-if="traces.length" class="trace-timeline">
              <div v-for="(t, idx) in traces" :key="idx" class="trace-node" :class="{ latest: idx === 0 }">
                <span class="trace-dot"></span>
                <div class="trace-body">
                  <div class="trace-desc">{{ t.description }}</div>
                  <div class="trace-meta"><span class="trace-status">{{ t.status }}</span><span class="trace-time">{{ t.time }}</span></div>
                </div>
              </div>
            </div>
            <div v-else class="logistics-empty">🚚 物流信息正在同步中…</div>
          </section>
        </div>

        <!-- 商品清单 -->
        <section class="panel">
          <h2 class="ui-section-title">商品清单</h2>
          <div class="items">
            <div v-for="item in items" :key="item.id" class="order-item"
                 @click="router.push(`/products/${item.productId}`)">
              <img :src="item.imageUrl || `/images/products/${item.productId}.jpg`" :alt="item.productName" class="item-image" />
              <div class="item-info">
                <div class="item-name">{{ item.productName }}</div>
                <div class="item-price">¥{{ item.unitPrice }}</div>
              </div>
              <div class="item-quantity">×{{ item.quantity }}</div>
              <div class="item-total">¥{{ item.subtotal }}</div>
            </div>
          </div>
          <div class="order-total-bar">
            共 {{ totalQuantity }} 件商品，合计：
            <span class="total-amount">{{ formatCurrency(order.totalAmount) }}</span>
          </div>
        </section>
      </template>

      <!-- 修改地址弹窗 -->
      <div v-if="showAddressModal" class="modal-overlay" @click="closeAddressModal">
        <div class="modal-content" @click.stop>
          <div class="modal-header">
            <h2>修改收货信息</h2>
            <button class="close-btn" @click="closeAddressModal">×</button>
          </div>
          <div class="modal-body">
            <div class="form-group">
              <label>收货人姓名</label>
              <input v-model="addressForm.receiverName" class="form-input" placeholder="请输入收货人姓名" />
            </div>
            <div class="form-group">
              <label>联系电话</label>
              <input
                v-model="addressForm.receiverPhone"
                class="form-input"
                type="text"
                inputmode="numeric"
                maxlength="11"
                placeholder="请输入 11 位手机号"
                @input="addressForm.receiverPhone = addressForm.receiverPhone.replace(/\D/g, '').slice(0, 11)"
              />
            </div>
            <div class="form-group">
              <label>收货地址</label>
              <textarea v-model="addressForm.receiverAddress" class="form-textarea" rows="3" placeholder="请输入详细收货地址"></textarea>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn btn-secondary" @click="closeAddressModal">取消</button>
            <button class="btn btn-primary" :disabled="savingAddress" @click="saveAddress">
              {{ savingAddress ? '保存中...' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.order-detail-page {
  padding: var(--sp-6) 0;
  min-height: calc(100vh - 120px);
  background: var(--c-bg);
}

.container {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 var(--sp-4);
}

.back-link {
  background: none;
  border: none;
  color: var(--c-primary-dark);
  cursor: pointer;
  font-size: 0.95rem;
  padding: 0;
  margin-bottom: var(--sp-4);
}

.back-link:hover {
  text-decoration: underline;
}

.state-box {
  text-align: center;
  padding: var(--sp-6);
  color: var(--c-text-muted);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

.state-box.error {
  color: var(--c-accent-dark);
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--sp-4);
  padding: var(--sp-5);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow);
  margin-bottom: var(--sp-4);
}

.order-no {
  font-weight: 700;
  color: var(--c-text);
  margin-bottom: var(--sp-1);
}

.order-date {
  color: var(--c-text-muted);
  font-size: 0.9rem;
}

.order-status {
  padding: var(--sp-1) var(--sp-3);
  border-radius: 999px;
  color: var(--c-text);
  font-weight: 700;
  font-size: 0.9rem;
  white-space: nowrap;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-4);
  margin-bottom: var(--sp-4);
}

/* 已取消订单只剩收货信息，占满整行 */
.detail-grid.single-col {
  grid-template-columns: 1fr;
}

.panel {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow);
  padding: var(--sp-5);
}

.panel + .panel,
.detail-grid + .panel {
  margin-bottom: var(--sp-4);
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-head .ui-section-title {
  margin: 0;
}

.link-btn {
  background: var(--c-primary-soft);
  color: var(--c-primary-dark);
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  padding: var(--sp-1) var(--sp-3);
  font-size: 0.85rem;
  cursor: pointer;
  transition: background-color 0.15s;
}

.link-btn:hover {
  background: #dcefe6;
}

.info-row {
  display: flex;
  gap: var(--sp-3);
  padding: var(--sp-2) 0;
  border-bottom: 1px dashed var(--c-border);
  font-size: 0.95rem;
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  flex-shrink: 0;
  width: 72px;
  color: var(--c-text-muted);
}

.info-value {
  color: var(--c-text);
  word-break: break-all;
}

.lock-hint {
  margin: var(--sp-3) 0 0 0;
  font-size: 0.85rem;
  color: var(--c-text-muted);
}

.logistics-empty {
  padding: var(--sp-4) 0;
  color: var(--c-text-muted);
  font-size: 0.95rem;
}

.trace-timeline {
  margin-top: var(--sp-4);
  padding-top: var(--sp-4);
  border-top: 1px dashed var(--c-border);
}

.trace-node {
  position: relative;
  padding: 0 0 var(--sp-4) var(--sp-5);
  border-left: 2px solid var(--c-border);
}

.trace-node:last-child {
  padding-bottom: 0;
  border-left-color: transparent;
}

.trace-dot {
  position: absolute;
  left: -6px;
  top: 2px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--c-border);
}

.trace-node.latest .trace-dot {
  background: var(--c-primary);
  box-shadow: 0 0 0 3px var(--c-primary-soft);
}

.trace-node.latest .trace-desc {
  color: var(--c-primary-dark);
  font-weight: 600;
}

.trace-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.trace-desc {
  color: var(--c-text);
  font-size: 0.92rem;
  line-height: 1.4;
}

.trace-meta {
  display: flex;
  gap: var(--sp-3);
  font-size: 0.8rem;
  color: var(--c-text-muted);
}

.trace-status {
  color: var(--c-primary-dark);
}

.items {
  margin-top: var(--sp-2);
}

.order-item {
  display: flex;
  align-items: center;
  padding: var(--sp-4) 0;
  border-bottom: 1px solid var(--c-border);
  cursor: pointer;
  transition: background-color 0.15s;
}

.order-item:hover {
  background: var(--c-primary-soft);
}

.order-item:last-child {
  border-bottom: none;
}

.item-image {
  width: 72px;
  height: 72px;
  object-fit: cover;
  border-radius: var(--radius-sm);
  margin-right: var(--sp-4);
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-name {
  color: var(--c-text);
  margin-bottom: var(--sp-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-price {
  font-size: 0.9rem;
  color: var(--c-accent);
}

.item-quantity {
  color: var(--c-text-muted);
  margin: 0 var(--sp-3);
}

.item-total {
  font-weight: 700;
  color: var(--c-text);
  min-width: 80px;
  text-align: right;
}

.order-total-bar {
  text-align: right;
  padding-top: var(--sp-4);
  margin-top: var(--sp-2);
  border-top: 1px solid var(--c-border);
  color: var(--c-text);
}

.total-amount {
  font-size: 1.2rem;
  font-weight: 700;
  color: var(--c-accent);
}

/* 弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: var(--c-surface);
  border-radius: var(--radius-lg);
  padding: var(--sp-5);
  max-width: 480px;
  width: 90%;
  box-shadow: var(--shadow-lg);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--sp-4);
  padding-bottom: var(--sp-3);
  border-bottom: 1px solid var(--c-border);
}

.modal-header h2 {
  margin: 0;
  font-size: 1.2rem;
  color: var(--c-text);
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  color: var(--c-text-muted);
}

.close-btn:hover {
  color: var(--c-text);
}

.modal-body {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.form-group label {
  font-weight: 600;
  color: var(--c-text);
  font-size: 0.9rem;
}

.form-input,
.form-textarea {
  padding: var(--sp-3);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-sm);
  font-size: 1rem;
  color: var(--c-text);
  background: var(--c-surface);
}

.form-input:focus,
.form-textarea:focus {
  outline: none;
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px var(--c-primary-soft);
}

.form-textarea {
  resize: vertical;
}

.modal-footer {
  display: flex;
  gap: var(--sp-3);
  justify-content: flex-end;
  margin-top: var(--sp-5);
  padding-top: var(--sp-4);
  border-top: 1px solid var(--c-border);
}

.btn {
  padding: var(--sp-2) var(--sp-5);
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 0.95rem;
  transition: background-color 0.15s;
}

.btn-primary {
  background: var(--c-primary);
  color: #fff;
}

.btn-primary:hover {
  background: var(--c-primary-dark);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary {
  background: transparent;
  border: 1px solid var(--c-border);
  color: var(--c-text-muted);
}

.btn-secondary:hover {
  background: var(--c-primary-soft);
  border-color: var(--c-primary);
  color: var(--c-primary-dark);
}

@media (max-width: 768px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .detail-header {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--sp-2);
  }
}
</style>
