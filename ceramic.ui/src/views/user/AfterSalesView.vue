<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/services/api'
import { useUserStore } from '@/stores/user'
import type { ReturnRequest, ReturnRequestLog, Order, OrderItem } from '@/types'

const router = useRouter()
const userStore = useUserStore()

const returnRequests = ref<ReturnRequest[]>([])
const loading = ref(true)
const submitting = ref(false)
const uploading = ref(false)

// 申请售后弹窗状态
const showApplyModal = ref(false)
const orders = ref<Order[]>([])
const orderItems = ref<OrderItem[]>([])

interface ApplyForm {
  orderId: number | null
  orderItemId: number | null
  returnType: ReturnRequest['returnType']
  reason: string
  description: string
  refundAmount: number
  evidenceImages: string[]
}

const form = ref<ApplyForm>({
  orderId: null,
  orderItemId: null,
  returnType: 'refund_only',
  reason: '',
  description: '',
  refundAmount: 0,
  evidenceImages: []
})

// 详情弹窗状态
const showDetailModal = ref(false)
const detail = ref<ReturnRequest | null>(null)
const detailLogs = ref<ReturnRequestLog[]>([])
const trackingNoInput = ref('')

const statusLabels: Record<string, string> = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已拒绝',
  RETURNING: '退货中',
  RECEIVED: '已收货',
  REFUNDING: '退款中',
  REFUNDED: '已退款',
  CLOSED: '已关闭'
}

const statusColors: Record<string, string> = {
  PENDING: '#f39c12',
  APPROVED: '#3498db',
  REJECTED: '#e74c3c',
  RETURNING: '#e67e22',
  RECEIVED: '#16a085',
  REFUNDING: '#8e44ad',
  REFUNDED: '#00b894',
  CLOSED: '#7f8c8d'
}

const returnTypeLabels: Record<string, string> = {
  refund_only: '仅退款',
  refund_and_return: '退货退款',
  exchange: '换货'
}

const reasonOptions = [
  { value: '七天无理由/不想要了', label: '七天无理由/不想要了' },
  { value: '运输破损', label: '运输破损' },
  { value: '商品质量问题', label: '商品质量问题' },
  { value: '商品与描述不符', label: '商品与描述不符' },
  { value: '少件/漏发', label: '少件/漏发' },
  { value: '个人原因', label: '个人原因' },
  { value: '其他', label: '其他' }
]

const eligibleOrders = computed(() => {
  return orders.value.filter(
    o => o.status === 'PAID' || o.status === 'SHIPPED' || o.status === 'COMPLETED'
  )
})

const selectedOrderItem = computed(() => {
  return orderItems.value.find(item => item.id === form.value.orderItemId) || null
})

const formatCurrency = (amount?: number): string => {
  if (amount === undefined || amount === null) return '¥0.00'
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY'
  }).format(amount)
}

const formatDate = (dateString: string): string => {
  if (!dateString) return ''
  return new Date(dateString).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const getStatusLabel = (status: string): string => {
  return statusLabels[status] || status
}

const getStatusColor = (status: string): string => {
  return statusColors[status] || '#95a5a6'
}

const getReturnTypeLabel = (type: string): string => {
  return returnTypeLabels[type] || type
}

const parseEvidenceImages = (evidence?: string): string[] => {
  if (!evidence) return []
  try {
    const parsed = JSON.parse(evidence)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

const loadReturnRequests = async () => {
  try {
    returnRequests.value = await api.returnRequests()
  } catch (e) {
    console.error('获取售后列表失败:', e)
  }
}

const loadOrders = async () => {
  try {
    orders.value = await api.orders()
  } catch (e) {
    console.error('获取订单失败:', e)
  }
}

const onOrderChange = async () => {
  form.value.orderItemId = null
  orderItems.value = []
  form.value.refundAmount = 0
  if (form.value.orderId) {
    try {
      orderItems.value = await api.orderItems(form.value.orderId)
    } catch (e) {
      console.error('获取订单商品失败:', e)
    }
  }
}

const onOrderItemChange = () => {
  const item = selectedOrderItem.value
  if (item) {
    form.value.refundAmount = item.unitPrice * item.quantity
  }
}

const resetForm = () => {
  form.value = {
    orderId: null,
    orderItemId: null,
    returnType: 'refund_only',
    reason: '',
    description: '',
    refundAmount: 0,
    evidenceImages: []
  }
  orderItems.value = []
}

const openApplyModal = async () => {
  resetForm()
  showApplyModal.value = true
  if (orders.value.length === 0) {
    await loadOrders()
  }
}

const closeApplyModal = () => {
  showApplyModal.value = false
  resetForm()
}

const onFileChange = async (event: Event) => {
  const target = event.target as HTMLInputElement
  if (!target.files || target.files.length === 0) return
  uploading.value = true
  try {
    for (const file of Array.from(target.files)) {
      const url = await api.uploadImage(file)
      form.value.evidenceImages.push(url)
    }
  } catch (e) {
    alert(e instanceof Error ? e.message : '图片上传失败')
  } finally {
    uploading.value = false
    target.value = ''
  }
}

const removeImage = (index: number) => {
  form.value.evidenceImages.splice(index, 1)
}

const submitApply = async () => {
  if (!form.value.orderId) {
    alert('请选择订单')
    return
  }
  if (!form.value.orderItemId) {
    alert('请选择售后商品')
    return
  }
  if (!form.value.reason) {
    alert('请选择售后原因')
    return
  }
  if (!form.value.description.trim()) {
    alert('请填写详细描述')
    return
  }
  if (!form.value.refundAmount || form.value.refundAmount <= 0) {
    alert('退款金额必须大于0')
    return
  }

  const item = selectedOrderItem.value
  if (!item) {
    alert('请选择售后商品')
    return
  }

  submitting.value = true
  try {
    await api.createReturnRequest({
      orderId: form.value.orderId,
      orderItemId: form.value.orderItemId,
      productId: item.productId,
      productName: item.productName,
      imageUrl: item.imageUrl,
      unitPrice: item.unitPrice,
      quantity: item.quantity,
      returnType: form.value.returnType,
      reason: form.value.reason,
      description: form.value.description,
      refundAmount: form.value.refundAmount,
      evidenceImages: JSON.stringify(form.value.evidenceImages),
      status: 'PENDING'
    })
    alert('售后申请已提交')
    closeApplyModal()
    await loadReturnRequests()
  } catch (e) {
    alert(e instanceof Error ? e.message : '操作失败')
  } finally {
    submitting.value = false
  }
}

const loadDetail = async (id: number) => {
  try {
    const [d, logs] = await Promise.all([
      api.returnRequestDetail(id),
      api.returnRequestLogs(id)
    ])
    detail.value = d
    detailLogs.value = logs
      .slice()
      .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
  } catch (e) {
    console.error('获取售后详情失败:', e)
  }
}

const openDetailModal = async (request: ReturnRequest) => {
  detail.value = request
  trackingNoInput.value = request.trackingNo || ''
  showDetailModal.value = true
  await loadDetail(request.id)
}

const closeDetailModal = () => {
  showDetailModal.value = false
  detail.value = null
  detailLogs.value = []
  trackingNoInput.value = ''
}

const submitTrackingNo = async () => {
  if (!detail.value) return
  if (!trackingNoInput.value.trim()) {
    alert('请填写快递单号')
    return
  }
  try {
    await api.shipBackReturn(detail.value.id, trackingNoInput.value.trim())
    alert('快递单号已提交')
    await loadDetail(detail.value.id)
    await loadReturnRequests()
  } catch (e) {
    alert(e instanceof Error ? e.message : '操作失败')
  }
}

onMounted(async () => {
  if (!userStore.isLoggedIn) {
    alert('请先登录')
    router.push('/login')
    return
  }
  loading.value = true
  await loadReturnRequests()
  loading.value = false
})
</script>

<template>
  <div class="after-sales-page">
    <div class="container">
      <div class="page-header">
        <h1>售后中心</h1>
        <button class="btn btn-primary" @click="openApplyModal">申请售后</button>
      </div>

      <div v-if="loading" class="loading">加载中...</div>

      <div v-else>
        <div v-if="returnRequests.length > 0" class="request-list">
          <div
            v-for="request in returnRequests"
            :key="request.id"
            class="request-card"
            @click="openDetailModal(request)"
          >
            <div class="card-header">
              <div class="card-info">
                <span class="product-name">{{ request.productName }}</span>
                <span class="request-no">#{{ request.id }}</span>
              </div>
              <span
                class="status-tag"
                :style="{ backgroundColor: getStatusColor(request.status) }"
              >
                {{ getStatusLabel(request.status) }}
              </span>
            </div>
            <div class="card-body">
              <div class="product-image" v-if="request.imageUrl">
                <img :src="request.imageUrl" :alt="request.productName" />
              </div>
              <div class="card-details">
                <div class="detail-row">
                  <span class="detail-label">类型:</span>
                  <span class="detail-value">{{ getReturnTypeLabel(request.returnType) }}</span>
                </div>
                <div class="detail-row">
                  <span class="detail-label">原因:</span>
                  <span class="detail-value">{{ request.reason }}</span>
                </div>
                <div class="detail-row">
                  <span class="detail-label">退款金额:</span>
                  <span class="detail-value price">{{ formatCurrency(request.refundAmount) }}</span>
                </div>
                <div class="detail-row">
                  <span class="detail-label">申请时间:</span>
                  <span class="detail-value">{{ formatDate(request.createdAt) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-else class="empty-state">
          <p>暂无售后申请</p>
          <p class="empty-hint">点击右上角"申请售后"按钮提交申请</p>
        </div>
      </div>

      <!-- 申请售后弹窗 -->
      <div v-if="showApplyModal" class="modal-overlay" @click="closeApplyModal">
        <div class="modal-content" @click.stop>
          <div class="modal-header">
            <h2>申请售后</h2>
            <button class="close-btn" @click="closeApplyModal">×</button>
          </div>
          <div class="modal-body">
            <div class="form-group">
              <label>选择订单 <span class="required">*</span></label>
              <select v-model="form.orderId" class="form-select" @change="onOrderChange">
                <option :value="null" disabled>请选择订单</option>
                <option
                  v-for="order in eligibleOrders"
                  :key="order.id"
                  :value="order.id"
                >
                  {{ order.orderNo || '#' + order.id }} - {{ formatCurrency(order.totalAmount) }}
                </option>
              </select>
              <p v-if="eligibleOrders.length === 0" class="form-hint">
                暂无可申请售后的订单（仅已支付/已发货/已完成订单可申请）
              </p>
            </div>

            <div class="form-group" v-if="form.orderId">
              <label>选择商品 <span class="required">*</span></label>
              <select v-model="form.orderItemId" class="form-select" @change="onOrderItemChange">
                <option :value="null" disabled>请选择商品</option>
                <option
                  v-for="item in orderItems"
                  :key="item.id"
                  :value="item.id"
                >
                  {{ item.productName }} - {{ formatCurrency(item.unitPrice) }} × {{ item.quantity }}
                </option>
              </select>
            </div>

            <div class="form-group">
              <label>申请类型 <span class="required">*</span></label>
              <select v-model="form.returnType" class="form-select">
                <option value="refund_only">仅退款</option>
                <option value="refund_and_return">退货退款</option>
                <option value="exchange">换货</option>
              </select>
            </div>

            <div class="form-group">
              <label>售后原因 <span class="required">*</span></label>
              <select v-model="form.reason" class="form-select">
                <option value="" disabled>请选择售后原因</option>
                <option v-for="opt in reasonOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </option>
              </select>
            </div>

            <div class="form-group">
              <label>详细描述 <span class="required">*</span></label>
              <textarea
                v-model="form.description"
                class="form-textarea"
                rows="4"
                placeholder="请详细描述您遇到的问题..."
              ></textarea>
            </div>

            <div class="form-group">
              <label>退款金额 (元) <span class="required">*</span></label>
              <input
                v-model.number="form.refundAmount"
                type="number"
                min="0"
                step="0.01"
                class="form-input"
                placeholder="退款金额"
              />
              <p class="form-hint">默认为商品单价 × 数量，可手动修改</p>
            </div>

            <div class="form-group">
              <label>凭证图片</label>
              <div class="image-upload">
                <div class="image-list">
                  <div
                    v-for="(img, index) in form.evidenceImages"
                    :key="index"
                    class="image-item"
                  >
                    <img :src="img" :alt="`凭证${index + 1}`" />
                    <button class="remove-image" type="button" @click="removeImage(index)">×</button>
                  </div>
                  <label class="upload-btn" v-if="!uploading">
                    <input type="file" accept="image/*" multiple @change="onFileChange" />
                    <span class="upload-icon">+</span>
                    <span class="upload-text">上传凭证</span>
                  </label>
                  <div class="uploading-hint" v-else>上传中...</div>
                </div>
              </div>
            </div>
          </div>

          <div class="modal-footer">
            <button class="btn btn-secondary" @click="closeApplyModal" :disabled="submitting">取消</button>
            <button class="btn btn-primary" @click="submitApply" :disabled="submitting">
              {{ submitting ? '提交中...' : '提交申请' }}
            </button>
          </div>
        </div>
      </div>

      <!-- 售后详情弹窗 -->
      <div v-if="showDetailModal && detail" class="modal-overlay" @click="closeDetailModal">
        <div class="modal-content" @click.stop>
          <div class="modal-header">
            <h2>售后详情</h2>
            <button class="close-btn" @click="closeDetailModal">×</button>
          </div>
          <div class="modal-body">
            <div class="detail-section">
              <div class="detail-section-title">售后单信息</div>
              <div class="info-grid">
                <div class="info-item">
                  <span class="info-label">售后单号:</span>
                  <span class="info-value">#{{ detail.id }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">状态:</span>
                  <span
                    class="status-tag"
                    :style="{ backgroundColor: getStatusColor(detail.status) }"
                  >
                    {{ getStatusLabel(detail.status) }}
                  </span>
                </div>
                <div class="info-item">
                  <span class="info-label">申请类型:</span>
                  <span class="info-value">{{ getReturnTypeLabel(detail.returnType) }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">售后原因:</span>
                  <span class="info-value">{{ detail.reason }}</span>
                </div>
                <div class="info-item full" v-if="detail.description">
                  <span class="info-label">详细描述:</span>
                  <span class="info-value">{{ detail.description }}</span>
                </div>
                <div class="info-item full" v-if="detail.rejectReason">
                  <span class="info-label">拒绝原因:</span>
                  <span class="info-value reject">{{ detail.rejectReason }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">申请时间:</span>
                  <span class="info-value">{{ formatDate(detail.createdAt) }}</span>
                </div>
              </div>
            </div>

            <div class="detail-section">
              <div class="detail-section-title">商品信息</div>
              <div class="product-info">
                <div class="product-image" v-if="detail.imageUrl">
                  <img :src="detail.imageUrl" :alt="detail.productName" />
                </div>
                <div class="product-meta">
                  <div class="info-item">
                    <span class="info-label">商品名称:</span>
                    <span class="info-value">{{ detail.productName }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">单价:</span>
                    <span class="info-value">{{ formatCurrency(detail.unitPrice) }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">数量:</span>
                    <span class="info-value">{{ detail.quantity }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">退款金额:</span>
                    <span class="info-value price">{{ formatCurrency(detail.refundAmount) }}</span>
                  </div>
                </div>
              </div>
            </div>

            <div class="detail-section">
              <div class="detail-section-title">退货物流</div>
              <div class="info-grid">
                <div class="info-item full" v-if="detail.returnAddress">
                  <span class="info-label">退货地址:</span>
                  <span class="info-value">{{ detail.returnAddress }}</span>
                </div>
                <div class="info-item" v-if="detail.trackingNo">
                  <span class="info-label">快递单号:</span>
                  <span class="info-value">{{ detail.trackingNo }}</span>
                </div>
                <div class="info-item full" v-if="!detail.returnAddress && !detail.trackingNo">
                  <span class="info-value empty">暂无物流信息</span>
                </div>
              </div>
            </div>

            <div class="detail-section" v-if="parseEvidenceImages(detail.evidenceImages).length > 0">
              <div class="detail-section-title">凭证图片</div>
              <div class="image-list">
                <div
                  v-for="(img, index) in parseEvidenceImages(detail.evidenceImages)"
                  :key="index"
                  class="image-item"
                >
                  <img :src="img" :alt="`凭证${index + 1}`" />
                </div>
              </div>
            </div>

            <div class="detail-section" v-if="detail.status === 'APPROVED'">
              <div class="detail-section-title">填写快递单号</div>
              <p class="form-hint">您的售后申请已通过，请寄回商品并填写快递单号</p>
              <div class="tracking-form">
                <input
                  v-model="trackingNoInput"
                  type="text"
                  class="form-input"
                  placeholder="请输入快递单号"
                />
                <button class="btn btn-primary" @click="submitTrackingNo">提交</button>
              </div>
            </div>

            <div class="detail-section">
              <div class="detail-section-title">进度时间线</div>
              <div class="timeline" v-if="detailLogs.length > 0">
                <div
                  v-for="(log, index) in detailLogs"
                  :key="log.id"
                  class="timeline-item"
                  :class="{ last: index === detailLogs.length - 1 }"
                >
                  <div class="timeline-dot"></div>
                  <div class="timeline-content">
                    <div class="timeline-header">
                      <span
                        class="status-tag"
                        :style="{ backgroundColor: getStatusColor(log.toStatus) }"
                      >
                        {{ getStatusLabel(log.toStatus) }}
                      </span>
                      <span class="timeline-time">{{ formatDate(log.createdAt) }}</span>
                    </div>
                    <div class="timeline-note" v-if="log.note">{{ log.note }}</div>
                    <div class="timeline-operator" v-if="log.operatorRole">
                      操作人: {{ log.operatorRole }}
                    </div>
                  </div>
                </div>
              </div>
              <div v-else class="empty-hint">暂无进度记录</div>
            </div>
          </div>

          <div class="modal-footer">
            <button class="btn btn-secondary" @click="closeDetailModal">关闭</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.after-sales-page {
  padding: var(--sp-6) 0;
  min-height: calc(100vh - 120px);
  background: var(--c-bg);
}

.container {
  max-width: 1000px;
  margin: 0 auto;
  padding: 0 var(--sp-4);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--sp-6);
}

h1 {
  margin: 0;
  color: var(--c-text);
  font-size: 1.8rem;
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

.request-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.request-card {
  background: var(--c-surface);
  border-radius: var(--radius);
  padding: var(--sp-5);
  box-shadow: var(--shadow);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s, border-color 0.2s;
  border: 1px solid var(--c-border);
}

.request-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
  border-color: var(--c-primary);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--sp-4);
  padding-bottom: var(--sp-3);
  border-bottom: 1px solid var(--c-border);
}

.card-info {
  display: flex;
  gap: var(--sp-4);
  align-items: center;
  flex-wrap: wrap;
}

.product-name {
  font-weight: bold;
  color: var(--c-text);
  font-size: 1.05rem;
}

.request-no {
  color: var(--c-text-muted);
  font-size: 0.85rem;
}

.status-tag {
  padding: var(--sp-1) var(--sp-3);
  border-radius: 999px;
  color: white;
  font-weight: bold;
  font-size: 0.85rem;
  display: inline-block;
  white-space: nowrap;
  letter-spacing: 0.02em;
}

.card-body {
  display: flex;
  gap: var(--sp-4);
  align-items: flex-start;
}

.product-image {
  width: 80px;
  height: 80px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  flex-shrink: 0;
  background: var(--c-primary-soft);
}

.product-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.card-details {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
}

.detail-row {
  display: flex;
  gap: var(--sp-2);
  font-size: 0.9rem;
}

.detail-label {
  color: var(--c-text-muted);
  min-width: 5rem;
}

.detail-value {
  color: var(--c-text);
}

.detail-value.price {
  color: var(--c-accent);
  font-weight: bold;
}

.empty-state {
  background: var(--c-surface);
  border-radius: var(--radius);
  padding: var(--sp-6);
  text-align: center;
  color: var(--c-text-muted);
  border: 1px solid var(--c-border);
  box-shadow: var(--shadow);
}

.empty-state p {
  margin: var(--sp-1) 0;
}

.empty-hint {
  color: var(--c-text-muted);
  font-size: 0.9rem;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
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
  max-width: 620px;
  width: 90%;
  max-height: 85vh;
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
  font-size: 1.3rem;
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.8rem;
  cursor: pointer;
  color: var(--c-text-muted);
  line-height: 1;
  padding: 0;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  transition: background-color 0.2s, color 0.2s;
}

.close-btn:hover {
  color: var(--c-text);
  background: var(--c-bg);
}

.modal-body {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
}

.form-group label {
  font-weight: bold;
  color: var(--c-text);
  font-size: 0.92rem;
}

.required {
  color: var(--c-accent);
}

.form-hint {
  color: var(--c-text-muted);
  font-size: 0.82rem;
  margin: 0;
}

.form-input,
.form-select,
.form-textarea {
  padding: var(--sp-3);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-sm);
  font-size: 0.95rem;
  font-family: inherit;
  color: var(--c-text);
  background: var(--c-surface);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.form-input:focus,
.form-select:focus,
.form-textarea:focus {
  outline: none;
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px var(--c-primary-soft);
}

.form-textarea {
  resize: vertical;
}

.image-upload {
  width: 100%;
}

.image-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-3);
}

.image-item {
  position: relative;
  width: 80px;
  height: 80px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--c-primary-soft);
  border: 1px solid var(--c-border);
}

.image-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.remove-image {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.6);
  color: white;
  border: none;
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
}

.remove-image:hover {
  background: var(--c-accent);
}

.upload-btn {
  width: 80px;
  height: 80px;
  border: 2px dashed var(--c-border);
  border-radius: var(--radius-sm);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--c-text-muted);
  transition: border-color 0.2s, color 0.2s;
  gap: 2px;
}

.upload-btn:hover {
  border-color: var(--c-primary);
  color: var(--c-primary);
}

.upload-btn input {
  display: none;
}

.upload-icon {
  font-size: 1.6rem;
  line-height: 1;
}

.upload-text {
  font-size: 0.7rem;
}

.uploading-hint {
  width: 80px;
  height: 80px;
  border: 2px dashed var(--c-border);
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-text-muted);
  font-size: 0.8rem;
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
  padding: var(--sp-3) var(--sp-5);
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  cursor: pointer;
  text-decoration: none;
  display: inline-block;
  text-align: center;
  font-size: 0.92rem;
  transition: background-color 0.2s, opacity 0.2s, border-color 0.2s, color 0.2s;
  font-family: inherit;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-primary {
  background: var(--c-primary);
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: var(--c-primary-dark);
}

.btn-secondary {
  background: transparent;
  color: var(--c-text);
  border-color: var(--c-border);
}

.btn-secondary:hover:not(:disabled) {
  background: var(--c-bg);
  border-color: var(--c-primary);
  color: var(--c-primary-dark);
}

.detail-section {
  background: var(--c-bg);
  border-radius: var(--radius);
  padding: var(--sp-4);
  border: 1px solid var(--c-border);
}

.detail-section-title {
  font-weight: bold;
  color: var(--c-text);
  margin-bottom: var(--sp-3);
  font-size: 0.98rem;
  padding-left: var(--sp-2);
  border-left: 3px solid var(--c-primary);
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-2) var(--sp-4);
}

.info-item {
  display: flex;
  gap: var(--sp-1);
  font-size: 0.9rem;
  align-items: flex-start;
}

.info-item.full {
  grid-column: 1 / -1;
}

.info-label {
  color: var(--c-text-muted);
  white-space: nowrap;
  flex-shrink: 0;
}

.info-value {
  color: var(--c-text);
  word-break: break-all;
}

.info-value.price {
  color: var(--c-accent);
  font-weight: bold;
}

.info-value.reject {
  color: var(--c-accent);
}

.info-value.empty {
  color: var(--c-text-muted);
  font-style: italic;
}

.product-info {
  display: flex;
  gap: var(--sp-4);
  align-items: flex-start;
}

.product-info .product-image {
  width: 90px;
  height: 90px;
}

.product-meta {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
}

.tracking-form {
  display: flex;
  gap: var(--sp-2);
  margin-top: var(--sp-2);
}

.tracking-form .form-input {
  flex: 1;
}

.tracking-form .btn {
  white-space: nowrap;
}

.timeline {
  position: relative;
  padding-left: var(--sp-4);
}

.timeline-item {
  position: relative;
  padding-bottom: var(--sp-4);
  padding-left: var(--sp-2);
}

.timeline-item:not(.last)::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 14px;
  bottom: 0;
  width: 2px;
  background: var(--c-border);
}

.timeline-dot {
  position: absolute;
  left: 0;
  top: 6px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--c-primary);
  border: 2px solid var(--c-surface);
  box-shadow: 0 0 0 2px var(--c-primary);
}

.timeline-content {
  padding-left: var(--sp-2);
}

.timeline-header {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
  margin-bottom: var(--sp-1);
}

.timeline-time {
  color: var(--c-text-muted);
  font-size: 0.82rem;
}

.timeline-note {
  color: var(--c-text);
  font-size: 0.88rem;
  margin-bottom: var(--sp-1);
  line-height: 1.5;
}

.timeline-operator {
  color: var(--c-text-muted);
  font-size: 0.8rem;
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    gap: 1rem;
    align-items: stretch;
  }

  .btn {
    width: 100%;
  }

  .card-body {
    flex-direction: column;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }

  .product-info {
    flex-direction: column;
  }

  .tracking-form {
    flex-direction: column;
  }
}
</style>
