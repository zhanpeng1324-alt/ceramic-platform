<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/services/api'
import { useUserStore } from '@/stores/user'
import type { Order, OrderItem, ReturnRequest } from '@/types'

const router = useRouter()
const userStore = useUserStore()

const orders = ref<Order[]>([])
const orderItemsMap = ref<Map<number, OrderItem[]>>(new Map())
const returnRequestsMap = ref<Map<number, ReturnRequest[]>>(new Map())
const reviewedProductIds = ref<Set<number>>(new Set())
const loading = ref(true)
const filterStatus = ref<string>('all')
const searchKeyword = ref('')
const selectedOrder = ref<Order | null>(null)
const showReturnModal = ref(false)
const selectedItems = ref<OrderItem[]>([])

const newReturnRequest = ref({
  orderId: 0,
  returnType: 'refund_and_return' as 'refund_only' | 'refund_and_return' | 'exchange',
  reason: '',
  description: ''
})

const returnTypeOptions = [
  { value: 'refund_only', label: '仅退款' },
  { value: 'refund_and_return', label: '退货退款' },
  { value: 'exchange', label: '换货' }
]

const returnReasonOptions = [
  { value: '运输破损', label: '运输破损' },
  { value: 'quality', label: '商品质量问题' },
  { value: 'wrong_item', label: '发错商品' },
  { value: 'damaged', label: '商品破损' },
  { value: 'size', label: '尺寸不符' },
  { value: 'changed_mind', label: '不想要了/拍错了' },
  { value: 'other', label: '其他原因' }
]

const formatCurrency = (amount: number): string => {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY'
  }).format(amount)
}

const formatDate = (dateString: string): string => {
  return new Date(dateString).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 待支付倒计时：下单后 30 分钟内需完成支付，超时后端定时任务会自动取消
const PAY_WINDOW_MS = 30 * 60 * 1000
const nowTs = ref(Date.now())
let clockTimer: ReturnType<typeof setInterval> | null = null
let lastExpiredRefresh = 0

const payDeadline = (order: Order): number => {
  const t = new Date(order.createdAt).getTime()
  return isNaN(t) ? 0 : t + PAY_WINDOW_MS
}

const payRemainingMs = (order: Order): number => {
  const dl = payDeadline(order)
  if (!dl) return 0
  return Math.max(0, dl - nowTs.value)
}

const isPayExpired = (order: Order): boolean => {
  const dl = payDeadline(order)
  return dl > 0 && dl - nowTs.value <= 0
}

const payCountdownText = (order: Order): string => {
  const total = Math.floor(payRemainingMs(order) / 1000)
  const m = Math.floor(total / 60)
  const s = total % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
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

const getReturnStatusName = (status: string): string => {
  switch (status) {
    case 'PENDING': return '待审核'
    case 'APPROVED': return '审核通过'
    case 'REJECTED': return '已拒绝'
    case 'RETURNING': return '退货中'
    case 'RECEIVED': return '已收货'
    case 'REFUNDING': return '退款中'
    case 'REFUNDED': return '已退款'
    case 'CLOSED': return '已关闭'
    default: return status
  }
}

const getReturnStatusColor = (status: string): string => {
  switch (status) {
    case 'PENDING': return '#ffeaa7'
    case 'APPROVED': return '#74b9ff'
    case 'REJECTED': return '#e74c3c'
    case 'RETURNING': return '#fdcb6e'
    case 'RECEIVED': return '#a29bfe'
    case 'REFUNDING': return '#fd79a8'
    case 'REFUNDED': return '#00b894'
    case 'CLOSED': return '#b2bec3'
    default: return '#ddd'
  }
}

// 待评价：已完成且订单内商品尚未全部评价（Review 按商品+用户存，故按商品维度近似判断）
const isOrderReviewed = (order: Order): boolean => {
  const its = orderItemsMap.value.get(order.id) || []
  if (its.length === 0) return false
  return its.every(it => reviewedProductIds.value.has(it.productId))
}

const isPendingReview = (order: Order): boolean =>
  order.status === 'COMPLETED' && !isOrderReviewed(order)

// 待评价订单中第一个尚未评价的商品（评价按商品维度提交）
const firstUnreviewedItem = (order: Order): OrderItem | undefined => {
  const its = orderItemsMap.value.get(order.id) || []
  return its.find(it => !reviewedProductIds.value.has(it.productId))
}

// 待评价订单剩余未评价商品数量（用于按钮文案，多件时提示还剩几件）
const unreviewedCount = (order: Order): number => {
  const its = orderItemsMap.value.get(order.id) || []
  return its.filter(it => !reviewedProductIds.value.has(it.productId)).length
}

// 去评价：跳到该商品详情页并自动展开评价表单（?review=1）
const goReview = (order: Order) => {
  const item = firstUnreviewedItem(order)
  if (!item) return
  router.push({ path: `/products/${item.productId}`, query: { review: '1' } })
}

const filteredOrders = computed(() => {
  let result = orders.value
  const tab = filterStatus.value
  if (tab === 'AFTERSALE') {
    // 退款/售后：有任意售后申请的订单
    result = result.filter(order => (returnRequestsMap.value.get(order.id) || []).length > 0)
  } else if (tab === 'COMPLETED') {
    // 待评价：已完成且未评价，评价后自动从此标签消失
    result = result.filter(isPendingReview)
  } else if (tab !== 'all') {
    result = result.filter(order => order.status === tab)
  }
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(order =>
      (order.orderNo || '').toLowerCase().includes(keyword) ||
      order.id.toString().includes(searchKeyword.value)
    )
  }
  return result
})

// 淘宝式订单分组标签（待评价 = 已完成且未评价）
const orderTabs = [
  { key: 'all', label: '全部' },
  { key: 'PENDING_PAY', label: '待付款' },
  { key: 'PAID', label: '待发货' },
  { key: 'SHIPPED', label: '待收货' },
  { key: 'COMPLETED', label: '待评价' },
  { key: 'AFTERSALE', label: '退款/售后' }
]

const tabCount = (key: string): number => {
  if (key === 'all') return orders.value.length
  if (key === 'AFTERSALE') {
    return orders.value.filter(o => (returnRequestsMap.value.get(o.id) || []).length > 0).length
  }
  if (key === 'COMPLETED') {
    return orders.value.filter(isPendingReview).length
  }
  return orders.value.filter(o => o.status === key).length
}

const canApplyRefund = (order: Order): boolean => {
  return order.status === 'PAID' || order.status === 'SHIPPED' || order.status === 'COMPLETED'
}

const canApplyReturn = (order: Order): boolean => {
  return order.status === 'SHIPPED' || order.status === 'COMPLETED'
}

const hasPendingReturn = (orderId: number): boolean => {
  const requests = returnRequestsMap.value.get(orderId) || []
  return requests.some(r => r.status === 'PENDING' || r.status === 'APPROVED' || r.status === 'RETURNING')
}

// 已完成退款的订单：钱已原路退回，订单终结，不再提供退款/退货/取消/物流入口
const hasRefunded = (orderId: number): boolean => {
  const requests = returnRequestsMap.value.get(orderId) || []
  return requests.some(r => r.status === 'REFUNDED')
}

const getOrderItems = (orderId: number): OrderItem[] => {
  return orderItemsMap.value.get(orderId) || []
}

const getOrderReturnRequests = (orderId: number): ReturnRequest[] => {
  return returnRequestsMap.value.get(orderId) || []
}

const getOrders = async () => {
  loading.value = true
  try {
    orders.value = await api.orders()

    // 当前用户评价过的商品，用于「待评价」判断（失败不阻断主流程）
    try {
      reviewedProductIds.value = new Set(await api.myReviewedProductIds())
    } catch {
      reviewedProductIds.value = new Set()
    }

    for (const order of orders.value) {
      const items = await api.orderItems(order.id)
      orderItemsMap.value.set(order.id, items)
      
      const returnRequests = await api.returnRequestsByOrder(order.id)
      returnRequestsMap.value.set(order.id, returnRequests)
    }
  } catch (error) {
    console.error('获取订单失败:', error)
    alert('获取订单失败')
  } finally {
    loading.value = false
  }
}

const updateOrderStatus = async (orderId: number, status: string) => {
  try {
    await api.updateOrderStatus(orderId, status)
    await getOrders()
    alert('订单状态已更新')
  } catch (error: any) {
    console.error('更新订单状态失败:', error)
    alert(error.message || '更新失败，请重试')
  }
}

const openReturnModal = (order: Order, returnType: 'refund_only' | 'refund_and_return' = 'refund_and_return') => {
  selectedOrder.value = order
  selectedItems.value = [...getOrderItems(order.id)]
  newReturnRequest.value = {
    orderId: order.id,
    returnType,
    reason: '',
    description: ''
  }
  showReturnModal.value = true
}

const closeReturnModal = () => {
  showReturnModal.value = false
  selectedOrder.value = null
  selectedItems.value = []
}

const toggleItemSelect = (item: OrderItem) => {
  const index = selectedItems.value.findIndex(i => i.id === item.id)
  if (index === -1) {
    selectedItems.value.push(item)
  } else {
    selectedItems.value.splice(index, 1)
  }
}

const selectAllItems = () => {
  if (selectedOrder.value) {
    selectedItems.value = [...getOrderItems(selectedOrder.value.id)]
  }
}

const deselectAllItems = () => {
  selectedItems.value = []
}

const submitReturnRequest = async () => {
  if (selectedItems.value.length === 0) {
    alert('请选择要退货的商品')
    return
  }
  if (!newReturnRequest.value.reason) {
    alert('请选择退货原因')
    return
  }

  try {
    for (const item of selectedItems.value) {
      await api.createReturnRequest({
        orderId: newReturnRequest.value.orderId,
        orderItemId: item.id,
        productId: item.productId,
        productName: item.productName,
        imageUrl: item.imageUrl,
        unitPrice: item.unitPrice,
        quantity: item.quantity,
        returnType: newReturnRequest.value.returnType,
        reason: newReturnRequest.value.reason,
        description: newReturnRequest.value.description,
        refundAmount: item.subtotal
      })
    }
    
    alert('退货申请已提交，请等待审核')
    closeReturnModal()
    await getOrders()
  } catch (error) {
    console.error('提交退货申请失败:', error)
    alert('提交失败，请重试')
  }
}

const handleOrderAction = async (order: Order, action: string) => {
  switch (action) {
    case '立即支付':
      if (isPayExpired(order)) {
        alert('该订单支付已超时，系统将自动取消，请重新下单')
        break
      }
      if (confirm('确认支付该订单？')) {
        try {
          await api.payOrder(order.id)
          await getOrders()
          alert('支付成功')
        } catch (e: any) {
          alert(e.message || '支付失败')
        }
      }
      break
    case '确认收货':
      if (confirm('确认已收到商品？')) {
        await api.confirmOrder(order.id)
        await getOrders()
      }
      break
    case '取消订单':
      if (confirm('确定要取消此订单吗？')) {
        await api.cancelOrder(order.id)
        await getOrders()
      }
      break
    case '查看物流':
    case '物流跟踪':
      router.push(`/orders/${order.id}`)
      break
    case '申请退款':
      openReturnModal(order, 'refund_only')
      break
    case '申请退货':
      openReturnModal(order, 'refund_and_return')
      break
  }
}

const selectOrder = (order: Order) => {
  selectedOrder.value = order
}

const closeOrderDetail = () => {
  selectedOrder.value = null
}

onMounted(async () => {
  if (!userStore.isLoggedIn) {
    alert('请先登录')
    router.push('/login')
    return
  }
  await getOrders()
  clockTimer = setInterval(() => {
    nowTs.value = Date.now()
    // 待支付订单一旦超时，后端定时任务会将其取消；此处定时刷新以同步最新状态
    const hasExpiredPending = orders.value.some(o => o.status === 'PENDING_PAY' && isPayExpired(o))
    if (hasExpiredPending && Date.now() - lastExpiredRefresh > 30000) {
      lastExpiredRefresh = Date.now()
      getOrders()
    }
  }, 1000)
})

onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer)
})
</script>

<template>
  <div class="order-management-page">
    <div class="container">
      <h1>我的订单</h1>
      
      <div v-if="loading" class="loading">加载中...</div>
      
      <div v-else>
        <div class="order-tabs">
          <button
            v-for="tab in orderTabs"
            :key="tab.key"
            class="order-tab"
            :class="{ active: filterStatus === tab.key }"
            @click="filterStatus = tab.key"
          >
            {{ tab.label }}
            <span v-if="tabCount(tab.key) > 0" class="tab-badge">{{ tabCount(tab.key) }}</span>
          </button>
        </div>

        <div class="order-filters">
          <div class="search-box">
            <input v-model="searchKeyword" type="text" placeholder="搜索订单号..." />
          </div>
        </div>
        
        <div class="orders-summary">
          <div class="summary-item">
            <span class="summary-label">总订单数</span>
            <span class="summary-value">{{ orders.length }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">待付款</span>
            <span class="summary-value">{{ orders.filter(o => o.status === 'PENDING_PAY').length }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">已完成</span>
            <span class="summary-value">{{ orders.filter(o => o.status === 'COMPLETED').length }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">总金额</span>
            <span class="summary-value">{{ formatCurrency(orders.reduce((sum, order) => sum + order.totalAmount, 0)) }}</span>
          </div>
        </div>
        
        <div class="orders-list" v-if="filteredOrders.length > 0">
          <div 
            v-for="order in filteredOrders" 
            :key="order.id" 
            class="order-card"
          >
            <div class="order-header">
              <div class="order-info">
                <div class="order-no">订单号: {{ order.orderNo || '#' + order.id }}</div>
                <div class="order-date">{{ formatDate(order.createdAt) }}</div>
              </div>
              <div class="order-status" :style="{ backgroundColor: getOrderStatusColor(order.status) }">
                {{ getOrderStatusName(order.status) }}
              </div>
            </div>

            <div v-if="order.status === 'PENDING_PAY'" class="pay-countdown-bar" :class="{ expired: isPayExpired(order) }">
              <template v-if="!isPayExpired(order)">
                ⏱ 请在 <strong>{{ payCountdownText(order) }}</strong> 内完成支付，超时订单将被自动取消
              </template>
              <template v-else>
                ⏱ 支付已超时，订单将被系统自动取消
              </template>
            </div>
            
            <div class="order-items">
              <div
                v-for="item in getOrderItems(order.id)"
                :key="item.id"
                class="order-item clickable"
                @click="router.push(`/orders/${order.id}`)"
                title="查看订单详情"
              >
                <img :src="item.imageUrl || `/images/products/${item.productId}.jpg`" :alt="item.productName" class="item-image" />
                <div class="item-info">
                  <div class="item-name">{{ item.productName }}</div>
                  <div class="item-price">¥{{ item.unitPrice }}</div>
                </div>
                <div class="item-quantity">×{{ item.quantity }}</div>
                <div class="item-total">¥{{ item.subtotal }}</div>
                <span class="item-arrow">›</span>
              </div>
            </div>
            
            <div v-if="order.trackingNo" class="logistics-info">
              <span class="logistics-label">📦 物流信息</span>
              <span>{{ order.shippingCompany }}　运单号：{{ order.trackingNo }}</span>
              <span v-if="order.shipTime" class="logistics-time">发货时间：{{ formatDate(order.shipTime) }}</span>
            </div>

            <div v-if="getOrderReturnRequests(order.id).length > 0" class="return-requests">
              <div 
                v-for="request in getOrderReturnRequests(order.id)" 
                :key="request.id" 
                class="return-request"
              >
                <div class="return-request-header">
                  <span class="return-type">{{ returnTypeOptions.find(o => o.value === request.returnType)?.label || request.returnType }}</span>
                  <span 
                    class="return-status" 
                    :style="{ backgroundColor: getReturnStatusColor(request.status) }"
                  >
                    {{ getReturnStatusName(request.status) }}
                  </span>
                </div>
                <div class="return-request-content">
                  <div class="return-product">{{ request.productName }} ×{{ request.quantity }}</div>
                  <div class="return-reason">原因: {{ returnReasonOptions.find(o => o.value === request.reason)?.label || request.reason }}</div>
                  <div class="return-amount">退款金额: {{ formatCurrency(request.refundAmount || 0) }}</div>
                  <div v-if="request.rejectReason" class="return-reject">拒绝原因: {{ request.rejectReason }}</div>
                </div>
              </div>
            </div>
            
            <div class="order-footer">
              <div class="order-total">
                共 {{ getOrderItems(order.id).reduce((sum, item) => sum + item.quantity, 0) }} 件商品，合计: 
                <span class="total-amount">{{ formatCurrency(order.totalAmount) }}</span>
              </div>
              <div class="order-actions">
                <button
                  v-if="order.status === 'PENDING_PAY'"
                  class="btn btn-primary"
                  :disabled="isPayExpired(order)"
                  @click="handleOrderAction(order, '立即支付')"
                >
                  {{ isPayExpired(order) ? '已超时' : '立即支付' }}
                </button>
                <button
                  v-else-if="order.status === 'PAID' && !hasRefunded(order.id)"
                  class="btn btn-secondary"
                  @click="handleOrderAction(order, '查看物流')"
                >
                  查看物流
                </button>
                <button 
                  v-else-if="order.status === 'SHIPPED'" 
                  class="btn btn-secondary"
                  @click="handleOrderAction(order, '物流跟踪')"
                >
                  物流跟踪
                </button>
                <button
                  v-if="order.status === 'SHIPPED'"
                  class="btn btn-success"
                  @click="handleOrderAction(order, '确认收货')"
                >
                  确认收货
                </button>
                <button
                  v-if="isPendingReview(order)"
                  class="btn btn-primary"
                  @click="goReview(order)"
                >
                  去评价<span v-if="unreviewedCount(order) > 1">（剩 {{ unreviewedCount(order) }} 件）</span>
                </button>
                <button
                  v-if="canApplyRefund(order) && !hasPendingReturn(order.id) && !hasRefunded(order.id)"
                  class="btn btn-warning"
                  @click="handleOrderAction(order, '申请退款')"
                >
                  申请退款
                </button>
                <button
                  v-if="canApplyReturn(order) && !hasPendingReturn(order.id) && !hasRefunded(order.id)"
                  class="btn btn-warning"
                  @click="handleOrderAction(order, '申请退货')"
                >
                  申请退货
                </button>
                <button
                  v-if="(order.status === 'PENDING_PAY' || order.status === 'PAID') && !hasRefunded(order.id)"
                  class="btn btn-danger"
                  @click="handleOrderAction(order, '取消订单')"
                >
                  取消订单
                </button>
              </div>
            </div>
          </div>
        </div>
        
        <div v-else class="empty-state">
          <p>暂无订单</p>
        </div>
      </div>
      
      <div v-if="showReturnModal" class="modal-overlay" @click="closeReturnModal">
        <div class="modal-content" @click.stop>
          <div class="modal-header">
            <h2>{{ newReturnRequest.returnType === 'refund_only' ? '申请退款' : '申请退货退款' }}</h2>
            <button class="close-btn" @click="closeReturnModal">×</button>
          </div>
          
          <div class="modal-body">
            <div class="form-group">
              <label>退货类型</label>
              <select v-model="newReturnRequest.returnType" class="form-select" :disabled="selectedOrder?.status === 'SHIPPED'">
                <option v-for="option in returnTypeOptions" :key="option.value" :value="option.value" :disabled="selectedOrder?.status === 'SHIPPED' && option.value !== 'refund_only'">
                  {{ option.label }}
                </option>
              </select>
            </div>
            
            <div class="form-group">
              <label>选择商品</label>
              <div class="select-all-row">
                <input 
                  type="checkbox" 
                  :checked="selectedItems.length === getOrderItems(selectedOrder?.id || 0).length"
                  @change="selectedItems.length === getOrderItems(selectedOrder?.id || 0).length ? deselectAllItems() : selectAllItems()"
                />
                <span>全选</span>
              </div>
              <div class="items-select">
                <div 
                  v-for="item in getOrderItems(selectedOrder?.id || 0)" 
                  :key="item.id" 
                  class="item-select-row"
                  :class="{ selected: selectedItems.some(i => i.id === item.id) }"
                  @click="toggleItemSelect(item)"
                >
                  <input 
                    type="checkbox" 
                    :checked="selectedItems.some(i => i.id === item.id)"
                    @click.stop="toggleItemSelect(item)"
                  />
                  <img :src="item.imageUrl || `/images/products/${item.productId}.jpg`" :alt="item.productName" class="small-image" />
                  <div class="item-info">
                    <div class="item-name">{{ item.productName }}</div>
                    <div class="item-price">¥{{ item.unitPrice }} ×{{ item.quantity }}</div>
                  </div>
                </div>
              </div>
            </div>
            
            <div class="form-group">
              <label>退货原因 <span class="required">*</span></label>
              <select v-model="newReturnRequest.reason" class="form-select">
                <option value="">请选择退货原因</option>
                <option v-for="option in returnReasonOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </div>
            
            <div class="form-group">
              <label>问题描述</label>
              <textarea 
                v-model="newReturnRequest.description" 
                class="form-textarea" 
                rows="4"
                placeholder="请详细描述您遇到的问题..."
              ></textarea>
            </div>
            
            <div class="return-summary">
              <div class="summary-row">
                <span>退货商品数量:</span>
                <span>{{ selectedItems.reduce((sum, item) => sum + item.quantity, 0) }} 件</span>
              </div>
              <div class="summary-row">
                <span>预计退款金额:</span>
                <span class="total-amount">{{ formatCurrency(selectedItems.reduce((sum, item) => sum + item.subtotal, 0)) }}</span>
              </div>
            </div>
          </div>
          
          <div class="modal-footer">
            <button class="btn btn-secondary" @click="closeReturnModal">取消</button>
            <button class="btn btn-warning" @click="submitReturnRequest">提交申请</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.order-management-page {
  padding: var(--sp-6) 0;
  min-height: calc(100vh - 120px);
  background: var(--c-bg);
}

.container {
  max-width: 1000px;
  margin: 0 auto;
  padding: 0 var(--sp-4);
}

h1 {
  text-align: center;
  margin-bottom: var(--sp-6);
  color: var(--c-text);
}

.loading {
  text-align: center;
  padding: var(--sp-6);
  color: var(--c-text-muted);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

.order-tabs {
  display: flex;
  gap: var(--sp-1);
  margin-bottom: var(--sp-4);
  padding: var(--sp-1);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  overflow-x: auto;
}

.order-tab {
  position: relative;
  flex: 1;
  min-width: max-content;
  padding: var(--sp-3) var(--sp-4);
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  color: var(--c-text-muted);
  font-size: 0.95rem;
  cursor: pointer;
  white-space: nowrap;
  transition: background-color 0.15s, color 0.15s;
}

.order-tab:hover {
  color: var(--c-primary-dark);
  background: var(--c-primary-soft);
}

.order-tab.active {
  color: var(--c-primary-dark);
  font-weight: 700;
  background: var(--c-primary-soft);
}

.order-tab.active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 2px;
  transform: translateX(-50%);
  width: 24px;
  height: 3px;
  border-radius: 999px;
  background: var(--c-primary);
}

.tab-badge {
  display: inline-block;
  min-width: 18px;
  margin-left: var(--sp-1);
  padding: 0 5px;
  font-size: 0.72rem;
  line-height: 18px;
  text-align: center;
  color: #fff;
  background: var(--c-accent);
  border-radius: 999px;
  vertical-align: top;
}

.order-filters {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--sp-6);
  padding: var(--sp-4);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

.filter-group {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.filter-group label {
  font-weight: bold;
  color: var(--c-text);
}

.filter-select {
  padding: var(--sp-2);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-sm);
  font-size: 1rem;
  color: var(--c-text);
  background: var(--c-surface);
}

.search-box input {
  padding: var(--sp-2);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-sm);
  font-size: 1rem;
  color: var(--c-text);
  background: var(--c-surface);
}

.filter-select:focus,
.search-box input:focus {
  outline: none;
  border-color: var(--c-primary);
}

.orders-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--sp-4);
  margin-bottom: var(--sp-6);
}

.summary-item {
  background: var(--c-surface);
  padding: var(--sp-5);
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  text-align: center;
}

.summary-label {
  display: block;
  font-size: 0.9rem;
  color: var(--c-text-muted);
  margin-bottom: var(--sp-2);
}

.summary-value {
  font-size: 1.5rem;
  font-weight: bold;
  color: var(--c-text);
}

.orders-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.order-card {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow);
  overflow: hidden;
  transition: transform 0.2s, box-shadow 0.2s;
}

.order-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-lg);
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--sp-4) var(--sp-5);
  background: var(--c-primary-soft);
  border-bottom: 1px solid var(--c-border);
}

.order-info {
  display: flex;
  gap: var(--sp-4);
  flex-wrap: wrap;
}

.order-no {
  font-weight: bold;
  color: var(--c-text);
}

.order-date {
  color: var(--c-text-muted);
  font-size: 0.9rem;
}

.order-status {
  padding: var(--sp-1) var(--sp-3);
  border-radius: 999px;
  color: var(--c-text);
  font-weight: bold;
  font-size: 0.9rem;
}

.order-items {
  padding: var(--sp-4) var(--sp-5);
}

.pay-countdown-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-2);
  padding: var(--sp-3) var(--sp-5);
  background: var(--c-primary-soft);
  border-bottom: 1px solid var(--c-border);
  color: var(--c-primary-dark);
  font-size: 0.9rem;
}

.pay-countdown-bar strong {
  color: var(--c-accent);
  font-variant-numeric: tabular-nums;
  font-size: 1.05rem;
}

.pay-countdown-bar.expired {
  background: #fdecea;
  color: var(--c-accent-dark);
}

.order-item {
  display: flex;
  align-items: center;
  padding: var(--sp-4) 0;
  border-bottom: 1px solid var(--c-border);
}

.order-item.clickable {
  cursor: pointer;
  transition: background-color 0.15s;
}

.order-item.clickable:hover {
  background: var(--c-primary-soft);
}

.item-arrow {
  margin-left: var(--sp-3);
  color: var(--c-text-muted);
  font-size: 1.3rem;
  line-height: 1;
}

.order-item:last-child {
  border-bottom: none;
}

.item-image {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: var(--radius-sm);
  margin-right: var(--sp-4);
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-size: 0.95rem;
  color: var(--c-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: var(--sp-1);
}

.item-price {
  font-size: 0.9rem;
  color: var(--c-accent);
}

.item-quantity {
  font-size: 0.9rem;
  color: var(--c-text-muted);
  margin: 0 var(--sp-2);
}

.item-total {
  font-size: 0.95rem;
  font-weight: bold;
  color: var(--c-text);
  min-width: 80px;
  text-align: right;
}

.return-requests {
  padding: var(--sp-4) var(--sp-5);
  background: var(--c-bg);
  border-top: 1px solid var(--c-border);
}

.logistics-info {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-3) var(--sp-5);
  align-items: center;
  padding: var(--sp-3) var(--sp-5);
  background: var(--c-primary-soft);
  border-top: 1px solid var(--c-border);
  font-size: 0.9rem;
  color: var(--c-text);
}

.logistics-label {
  font-weight: bold;
  color: var(--c-primary-dark);
}

.logistics-time {
  color: var(--c-text-muted);
}

.return-request {
  background: var(--c-surface);
  padding: var(--sp-4);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-sm);
  margin-bottom: var(--sp-2);
}

.return-request:last-child {
  margin-bottom: 0;
}

.return-request-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--sp-2);
}

.return-type {
  font-weight: bold;
  color: var(--c-accent);
}

.return-status {
  padding: 0.15rem var(--sp-2);
  border-radius: 999px;
  color: var(--c-text);
  font-size: 0.8rem;
}

.return-request-content {
  font-size: 0.9rem;
  color: var(--c-text-muted);
}

.return-product {
  margin-bottom: var(--sp-1);
}

.return-reason {
  margin-bottom: var(--sp-1);
}

.return-amount {
  margin-bottom: var(--sp-1);
  font-weight: bold;
  color: var(--c-accent);
}

.return-reject {
  color: var(--c-accent-dark);
}

.order-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--sp-4) var(--sp-5);
  background: var(--c-bg);
  border-top: 1px solid var(--c-border);
}

.order-total {
  font-size: 0.95rem;
  color: var(--c-text);
}

.total-amount {
  font-size: 1.1rem;
  font-weight: bold;
  color: var(--c-accent);
}

.order-actions {
  display: flex;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

.btn {
  padding: var(--sp-2) var(--sp-4);
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  cursor: pointer;
  text-decoration: none;
  display: inline-block;
  text-align: center;
  font-size: 0.9rem;
  transition: background-color 0.2s, color 0.2s, border-color 0.2s;
  white-space: nowrap;
}

.btn-outline {
  background: transparent;
  border: 1px solid var(--c-border);
  color: var(--c-text);
}

.btn-outline:hover {
  background: var(--c-primary-soft);
  border-color: var(--c-primary);
  color: var(--c-primary-dark);
}

.btn-primary {
  background: var(--c-primary);
  color: #fff;
}

.btn-primary:hover {
  background: var(--c-primary-dark);
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

.btn-success {
  background: var(--c-primary);
  color: #fff;
}

.btn-success:hover {
  background: var(--c-primary-dark);
}

.btn-warning {
  background: var(--c-accent);
  color: #fff;
}

.btn-warning:hover {
  background: var(--c-accent-dark);
}

.btn-danger {
  background: transparent;
  border: 1px solid var(--c-border);
  color: var(--c-accent);
}

.btn-danger:hover {
  background: var(--c-accent);
  color: #fff;
  border-color: var(--c-accent);
}

.empty-state {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: var(--sp-6);
  text-align: center;
  color: var(--c-text-muted);
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: var(--c-surface);
  border-radius: var(--radius-lg);
  padding: var(--sp-5);
  max-width: 600px;
  width: 90%;
  max-height: 80vh;
  overflow-y: auto;
  position: relative;
  box-shadow: var(--shadow-lg);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--sp-5);
  padding-bottom: var(--sp-4);
  border-bottom: 1px solid var(--c-border);
}

.modal-header h2 {
  margin: 0;
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
  font-weight: bold;
  color: var(--c-text);
}

.required {
  color: var(--c-accent);
}

.form-select,
.form-input,
.form-textarea {
  padding: var(--sp-3);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-sm);
  font-size: 1rem;
  color: var(--c-text);
  background: var(--c-surface);
}

.form-select:focus,
.form-input:focus,
.form-textarea:focus {
  outline: none;
  border-color: var(--c-primary);
}

.form-textarea {
  resize: vertical;
}

.select-all-row {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-bottom: var(--sp-2);
  font-weight: bold;
}

.items-select {
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid var(--c-border);
  border-radius: var(--radius-sm);
}

.item-select-row {
  display: flex;
  align-items: center;
  padding: var(--sp-3);
  border-bottom: 1px solid var(--c-border);
  cursor: pointer;
  transition: background-color 0.2s;
}

.item-select-row:hover {
  background: var(--c-primary-soft);
}

.item-select-row.selected {
  background: var(--c-primary-soft);
}

.item-select-row:last-child {
  border-bottom: none;
}

.small-image {
  width: 50px;
  height: 50px;
  object-fit: cover;
  border-radius: var(--radius-sm);
  margin: 0 var(--sp-3);
}

.return-summary {
  background: var(--c-bg);
  padding: var(--sp-4);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-sm);
}

.summary-row {
  display: flex;
  justify-content: space-between;
  padding: var(--sp-2) 0;
  font-size: 0.95rem;
}

.summary-row span:first-child {
  color: var(--c-text-muted);
}

.modal-footer {
  display: flex;
  gap: var(--sp-4);
  justify-content: flex-end;
  margin-top: var(--sp-5);
  padding-top: var(--sp-4);
  border-top: 1px solid var(--c-border);
}

@media (max-width: 768px) {
  .order-filters {
    flex-direction: column;
    gap: var(--sp-4);
  }

  .order-header {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--sp-2);
  }

  .order-item {
    flex-wrap: wrap;
    gap: var(--sp-2);
  }

  .item-image {
    width: 60px;
    height: 60px;
  }

  .order-footer {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--sp-4);
  }

  .order-actions {
    width: 100%;
    flex-direction: column;
  }

  .btn {
    width: 100%;
  }
}
</style>