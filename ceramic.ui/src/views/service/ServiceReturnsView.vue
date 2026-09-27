<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { api } from '@/services/api'
import type { ReturnRequest, ReturnRequestLog } from '@/types'
import ServiceNavbar from '@/components/service/ServiceNavbar.vue'

const returnRequests = ref<ReturnRequest[]>([])
const loading = ref(true)
const filterStatus = ref<string>('all')

const statusOptions = [
  { value: 'all', label: '全部' },
  { value: 'PENDING', label: '待审核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已拒绝' },
  { value: 'RETURNING', label: '退货中' },
  { value: 'RECEIVED', label: '已收货' },
  { value: 'REFUNDING', label: '退款中' },
  { value: 'REFUNDED', label: '已退款' },
  { value: 'CLOSED', label: '已关闭' }
]

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

const typeLabels: Record<string, string> = {
  refund_only: '仅退款',
  refund_and_return: '退货退款',
  exchange: '换货'
}

const statusClassFor = (status: string): string => {
  const map: Record<string, string> = {
    PENDING: 'tag-pending',
    APPROVED: 'tag-approved',
    REJECTED: 'tag-rejected',
    RETURNING: 'tag-returning',
    RECEIVED: 'tag-received',
    REFUNDING: 'tag-refunding',
    REFUNDED: 'tag-refunded',
    CLOSED: 'tag-closed'
  }
  return map[status] || 'tag-default'
}

const filteredRequests = computed(() => {
  if (filterStatus.value === 'all') return returnRequests.value
  return returnRequests.value.filter(r => r.status === filterStatus.value)
})

const formatDate = (dateStr: string | undefined): string => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

const parseImages = (raw?: string): string[] => {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter((s): s is string => typeof s === 'string') : []
  } catch {
    return []
  }
}

// 详情弹窗
const detailVisible = ref(false)
const detailLoading = ref(false)
const currentDetail = ref<ReturnRequest | null>(null)
const currentLogs = ref<ReturnRequestLog[]>([])

const openDetail = async (req: ReturnRequest) => {
  detailVisible.value = true
  detailLoading.value = true
  currentDetail.value = req
  currentLogs.value = []
  try {
    const [detail, logs] = await Promise.all([
      api.returnRequestDetail(req.id),
      api.returnRequestLogs(req.id)
    ])
    currentDetail.value = detail
    currentLogs.value = logs
      .slice()
      .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
  } catch (e) {
    alert(e instanceof Error ? e.message : '加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

const closeDetail = () => {
  detailVisible.value = false
  currentDetail.value = null
  currentLogs.value = []
}

// 操作定义
type ActionType = 'approve' | 'reject' | 'fillAddress' | 'receive' | 'startRefund' | 'completeRefund' | 'close'

interface ActionDescriptor {
  type: ActionType
  label: string
  variant: 'primary' | 'danger' | 'warning'
}

const actionsFor = (status: string): ActionDescriptor[] => {
  switch (status) {
    case 'PENDING':
      return [
        { type: 'approve', label: '审核通过', variant: 'primary' },
        { type: 'reject', label: '拒绝', variant: 'danger' },
        { type: 'close', label: '关闭', variant: 'warning' }
      ]
    case 'APPROVED':
      return [
        { type: 'fillAddress', label: '填写退货地址', variant: 'primary' },
        { type: 'close', label: '关闭', variant: 'warning' }
      ]
    case 'RETURNING':
      return [
        { type: 'receive', label: '确认收货', variant: 'primary' },
        { type: 'close', label: '关闭', variant: 'warning' }
      ]
    case 'RECEIVED':
      return [
        { type: 'startRefund', label: '确认退款', variant: 'primary' },
        { type: 'close', label: '关闭', variant: 'warning' }
      ]
    case 'REFUNDING':
      return [
        { type: 'completeRefund', label: '完成退款', variant: 'primary' },
        { type: 'close', label: '关闭', variant: 'warning' }
      ]
    default:
      return []
  }
}

// 操作弹窗（需要输入的操作）
const actionVisible = ref(false)
const actionType = ref<ActionType | null>(null)
const actionReq = ref<ReturnRequest | null>(null)
const actionInput = ref('')
const actionSubmitting = ref(false)

const actionTitleMap: Record<ActionType, string> = {
  approve: '审核通过 - 填写退货地址',
  reject: '拒绝售后申请',
  fillAddress: '填写退货地址',
  receive: '确认收货',
  startRefund: '确认开始退款',
  completeRefund: '完成退款 - 填写退款金额',
  close: '关闭售后申请'
}

interface FieldConfig {
  label: string
  placeholder: string
  inputType: 'textarea' | 'number'
}

const actionFieldMap: Record<ActionType, FieldConfig> = {
  approve: { label: '退货地址', placeholder: '请输入退货收货地址', inputType: 'textarea' },
  reject: { label: '拒绝原因', placeholder: '请输入拒绝原因', inputType: 'textarea' },
  fillAddress: { label: '退货地址', placeholder: '请输入退货收货地址', inputType: 'textarea' },
  receive: { label: '', placeholder: '', inputType: 'textarea' },
  startRefund: { label: '', placeholder: '', inputType: 'textarea' },
  completeRefund: { label: '退款金额', placeholder: '请输入退款金额', inputType: 'number' },
  close: { label: '关闭原因', placeholder: '请输入关闭原因', inputType: 'textarea' }
}

const defaultFieldConfig: FieldConfig = { label: '', placeholder: '', inputType: 'textarea' }

const currentFieldConfig = computed<FieldConfig>(() => {
  return actionType.value ? actionFieldMap[actionType.value] : defaultFieldConfig
})

const currentActionTitle = computed<string>(() => {
  return actionType.value ? actionTitleMap[actionType.value] : '操作'
})

const openActionModal = (req: ReturnRequest, type: ActionType) => {
  actionReq.value = req
  actionType.value = type
  actionInput.value = ''
  if (type === 'completeRefund') {
    actionInput.value = String(req.refundAmount ?? (req.unitPrice * req.quantity))
  }
  actionVisible.value = true
}

const closeAction = () => {
  actionVisible.value = false
  actionType.value = null
  actionReq.value = null
  actionInput.value = ''
}

const runConfirmAction = async (req: ReturnRequest, type: ActionType) => {
  const msg = type === 'receive'
    ? `确认已收到退货 #${req.id} 的商品吗？`
    : `确认开始为售后 #${req.id} 办理退款吗？`
  if (!confirm(msg)) return
  try {
    if (type === 'receive') {
      await api.receiveReturn(req.id)
    } else if (type === 'startRefund') {
      await api.refundReturn(req.id)
    }
    await loadReturns()
  } catch (e) {
    alert(e instanceof Error ? e.message : '操作失败')
  }
}

const handleAction = (req: ReturnRequest, type: ActionType) => {
  if (type === 'receive' || type === 'startRefund') {
    runConfirmAction(req, type)
  } else {
    openActionModal(req, type)
  }
}

const submitAction = async () => {
  if (!actionType.value || !actionReq.value) return
  const id = actionReq.value.id
  const type = actionType.value
  try {
    actionSubmitting.value = true
    switch (type) {
      case 'approve':
        await api.approveReturnRequest(id, actionInput.value.trim() || undefined)
        break
      case 'reject':
        await api.rejectReturnRequest(id, actionInput.value.trim() || undefined)
        break
      case 'fillAddress':
        await api.fillReturnAddress(id, actionInput.value.trim())
        break
      case 'completeRefund':
        await api.refundReturn(id, Number(actionInput.value) || undefined)
        break
      case 'close':
        await api.closeReturn(id, actionInput.value.trim() || undefined)
        break
    }
    closeAction()
    await loadReturns()
  } catch (e) {
    alert(e instanceof Error ? e.message : '操作失败')
  } finally {
    actionSubmitting.value = false
  }
}

const loadReturns = async () => {
  loading.value = true
  try {
    returnRequests.value = await api.returnRequests()
  } catch (e) {
    alert(e instanceof Error ? e.message : '加载售后列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadReturns()
})
</script>

<template>
  <div class="returns-page">
    <ServiceNavbar />
    <main class="content">
      <header class="page-header">
        <div class="header-left">
          <h1>售后申请管理</h1>
          <p>审核退换货申请，跟踪退货进度与退款</p>
        </div>
        <button class="btn btn-refresh" @click="loadReturns">刷新</button>
      </header>

      <div class="toolbar">
        <label class="filter-label">状态筛选：</label>
        <select v-model="filterStatus" class="filter-select">
          <option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
        <span class="count">共 {{ filteredRequests.length }} 条</span>
      </div>

      <div v-if="loading" class="state-box">加载中...</div>
      <div v-else-if="filteredRequests.length === 0" class="state-box">暂无售后申请</div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>订单号</th>
              <th>商品名</th>
              <th>类型</th>
              <th>退款金额</th>
              <th>状态</th>
              <th>申请时间</th>
              <th>原因</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="req in filteredRequests" :key="req.id">
              <td>#{{ req.id }}</td>
              <td>#{{ req.orderId }}</td>
              <td class="name-cell">{{ req.productName }}</td>
              <td>{{ typeLabels[req.returnType] || req.returnType }}</td>
              <td>{{ req.refundAmount != null ? '¥' + req.refundAmount : '-' }}</td>
              <td>
                <span class="status-tag" :class="statusClassFor(req.status)">
                  {{ statusLabels[req.status] || req.status }}
                </span>
              </td>
              <td class="time-cell">{{ formatDate(req.createdAt) }}</td>
              <td class="reason-cell" :title="req.reason">{{ req.reason || '-' }}</td>
              <td>
                <div class="actions">
                  <button class="btn btn-view" @click="openDetail(req)">查看详情</button>
                  <button
                    v-for="act in actionsFor(req.status)"
                    :key="act.type"
                    class="btn"
                    :class="'btn-' + act.variant"
                    @click="handleAction(req, act.type)"
                  >
                    {{ act.label }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </main>

    <!-- 详情弹窗 -->
    <div v-if="detailVisible" class="modal-overlay" @click.self="closeDetail">
      <div class="modal-content modal-lg">
        <div class="modal-header">
          <h2>售后详情 #{{ currentDetail?.id }}</h2>
          <button class="modal-close" @click="closeDetail">×</button>
        </div>
        <div class="modal-body">
          <div v-if="detailLoading" class="state-box">加载中...</div>
          <template v-else-if="currentDetail">
            <div class="detail-section">
              <div class="section-title">基本信息</div>
              <div class="detail-grid">
                <div class="info-item"><span class="info-label">订单号：</span>#{{ currentDetail.orderId }}</div>
                <div class="info-item"><span class="info-label">商品名：</span>{{ currentDetail.productName }}</div>
                <div class="info-item"><span class="info-label">类型：</span>{{ typeLabels[currentDetail.returnType] || currentDetail.returnType }}</div>
                <div class="info-item"><span class="info-label">单价：</span>¥{{ currentDetail.unitPrice }}</div>
                <div class="info-item"><span class="info-label">数量：</span>{{ currentDetail.quantity }}</div>
                <div class="info-item"><span class="info-label">退款金额：</span>{{ currentDetail.refundAmount != null ? '¥' + currentDetail.refundAmount : '-' }}</div>
                <div class="info-item"><span class="info-label">状态：</span>
                  <span class="status-tag" :class="statusClassFor(currentDetail.status)">{{ statusLabels[currentDetail.status] || currentDetail.status }}</span>
                </div>
                <div class="info-item"><span class="info-label">申请时间：</span>{{ formatDate(currentDetail.createdAt) }}</div>
                <div class="info-item"><span class="info-label">更新时间：</span>{{ formatDate(currentDetail.updatedAt) }}</div>
              </div>
            </div>

            <div class="detail-section">
              <div class="section-title">申请原因</div>
              <p class="reason-text">{{ currentDetail.reason || '-' }}</p>
              <p v-if="currentDetail.description" class="reason-text"><span class="info-label">补充描述：</span>{{ currentDetail.description }}</p>
              <p v-if="currentDetail.rejectReason" class="reason-text reject"><span class="info-label">拒绝原因：</span>{{ currentDetail.rejectReason }}</p>
            </div>

            <div v-if="currentDetail.trackingNo || currentDetail.returnAddress" class="detail-section">
              <div class="section-title">物流信息</div>
              <div class="detail-grid">
                <div v-if="currentDetail.trackingNo" class="info-item"><span class="info-label">物流单号：</span>{{ currentDetail.trackingNo }}</div>
                <div v-if="currentDetail.returnAddress" class="info-item"><span class="info-label">退货地址：</span>{{ currentDetail.returnAddress }}</div>
              </div>
            </div>

            <div v-if="parseImages(currentDetail.evidenceImages).length > 0" class="detail-section">
              <div class="section-title">凭证图片</div>
              <div class="evidence-list">
                <img
                  v-for="(img, idx) in parseImages(currentDetail.evidenceImages)"
                  :key="idx"
                  :src="img"
                  class="evidence-img"
                  alt="凭证"
                />
              </div>
            </div>

            <div class="detail-section">
              <div class="section-title">进度时间线</div>
              <div v-if="currentLogs.length === 0" class="state-box small">暂无日志</div>
              <ul v-else class="timeline">
                <li v-for="(log, idx) in currentLogs" :key="log.id" class="timeline-item" :class="{ last: idx === currentLogs.length - 1 }">
                  <div class="timeline-dot"></div>
                  <div class="timeline-content">
                    <div class="timeline-header">
                      <span class="status-tag" :class="statusClassFor(log.toStatus)">{{ statusLabels[log.toStatus] || log.toStatus }}</span>
                      <span v-if="log.fromStatus" class="timeline-from">← {{ statusLabels[log.fromStatus] || log.fromStatus }}</span>
                      <span class="timeline-time">{{ formatDate(log.createdAt) }}</span>
                    </div>
                    <div v-if="log.note" class="timeline-note">{{ log.note }}</div>
                    <div v-if="log.operatorRole" class="timeline-operator">操作人角色：{{ log.operatorRole }}</div>
                  </div>
                </li>
              </ul>
            </div>
          </template>
        </div>
        <div class="modal-footer">
          <button class="btn btn-cancel" @click="closeDetail">关闭</button>
        </div>
      </div>
    </div>

    <!-- 操作弹窗 -->
    <div v-if="actionVisible" class="modal-overlay" @click.self="closeAction">
      <div class="modal-content modal-sm">
        <div class="modal-header">
          <h2>{{ currentActionTitle }}</h2>
          <button class="modal-close" @click="closeAction">×</button>
        </div>
        <div class="modal-body">
          <div class="action-form">
            <label class="form-label">{{ currentFieldConfig.label }}</label>
            <textarea
              v-if="currentFieldConfig.inputType === 'textarea'"
              v-model="actionInput"
              class="form-textarea"
              :placeholder="currentFieldConfig.placeholder"
              rows="4"
            ></textarea>
            <input
              v-else
              v-model="actionInput"
              type="number"
              class="form-input"
              :placeholder="currentFieldConfig.placeholder"
            />
            <p v-if="actionType === 'approve' || actionType === 'fillAddress'" class="form-hint">填写后用户将看到退货收货地址</p>
            <p v-if="actionType === 'completeRefund'" class="form-hint">请确认退款金额，提交后将完成退款</p>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-cancel" @click="closeAction" :disabled="actionSubmitting">取消</button>
          <button class="btn btn-primary" @click="submitAction" :disabled="actionSubmitting">
            {{ actionSubmitting ? '提交中...' : '确定' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.returns-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.content {
  flex: 1;
  max-width: 1280px;
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

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.filter-label {
  color: #4a5568;
  font-size: 0.92rem;
}

.filter-select {
  padding: 7px 12px;
  border: 1px solid #ccd7d1;
  border-radius: 6px;
  background: #fff;
  font-size: 0.92rem;
  cursor: pointer;
  min-width: 140px;
}

.filter-select:focus {
  outline: none;
  border-color: #42b883;
}

.count {
  color: #718096;
  font-size: 0.88rem;
}

.state-box {
  background: #fff;
  border-radius: 10px;
  padding: 48px;
  text-align: center;
  color: #718096;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
}

.state-box.small {
  padding: 16px;
}

.table-wrap {
  background: #fff;
  border-radius: 12px;
  overflow: auto;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

table {
  border-collapse: collapse;
  width: 100%;
  min-width: 980px;
}

th,
td {
  padding: 13px 16px;
  text-align: left;
  border-bottom: 1px solid #edf1ef;
  font-size: 0.9rem;
  color: #2d3748;
  vertical-align: middle;
}

th {
  background: #f8fbf9;
  font-weight: 600;
  color: #4a5568;
  white-space: nowrap;
}

tr:hover td {
  background: #fafdfb;
}

.name-cell {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reason-cell {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #4a5568;
}

.time-cell {
  white-space: nowrap;
  color: #718096;
  font-size: 0.85rem;
}

.status-tag {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 12px;
  color: #fff;
  font-size: 0.78rem;
  font-weight: 600;
  white-space: nowrap;
}

.tag-pending { background: #f39c12; }
.tag-approved { background: #3498db; }
.tag-rejected { background: #e74c3c; }
.tag-returning { background: #e67e22; }
.tag-received { background: #16a085; }
.tag-refunding { background: #8e44ad; }
.tag-refunded { background: #42b883; }
.tag-closed { background: #7f8c8d; }
.tag-default { background: #95a5a6; }

.actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.btn {
  border: none;
  border-radius: 6px;
  padding: 6px 12px;
  cursor: pointer;
  font-size: 0.82rem;
  transition: background-color 0.2s, opacity 0.2s;
  font-family: inherit;
  white-space: nowrap;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-view {
  background: #eef7f3;
  color: #42b883;
}

.btn-view:hover { background: #dcefe5; }

.btn-primary {
  background: #42b883;
  color: #fff;
}

.btn-primary:hover:not(:disabled) { background: #369870; }

.btn-danger {
  background: #e74c3c;
  color: #fff;
}

.btn-danger:hover:not(:disabled) { background: #c0392b; }

.btn-warning {
  background: #e67e22;
  color: #fff;
}

.btn-warning:hover:not(:disabled) { background: #c0681b; }

.btn-refresh {
  background: #42b883;
  color: #fff;
  padding: 8px 16px;
}

.btn-refresh:hover { background: #369870; }

.btn-cancel {
  background: #e2e8f0;
  color: #4a5568;
}

.btn-cancel:hover:not(:disabled) { background: #cbd5e0; }

/* 弹窗 */
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
  background: #fff;
  border-radius: 12px;
  width: 90%;
  max-height: 86vh;
  overflow-y: auto;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
}

.modal-lg {
  max-width: 720px;
}

.modal-sm {
  max-width: 460px;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #edf1ef;
  position: sticky;
  top: 0;
  background: #fff;
  z-index: 1;
  border-radius: 12px 12px 0 0;
}

.modal-header h2 {
  margin: 0;
  color: #243b53;
  font-size: 1.15rem;
}

.modal-close {
  background: none;
  border: none;
  font-size: 1.6rem;
  cursor: pointer;
  color: #999;
  line-height: 1;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-close:hover {
  color: #333;
  background: #f5f5f5;
}

.modal-body {
  padding: 20px;
}

.detail-section {
  background: #fafdfb;
  border-radius: 10px;
  padding: 14px 16px;
  border: 1px solid #edf1ef;
  margin-bottom: 14px;
}

.detail-section:last-child {
  margin-bottom: 0;
}

.section-title {
  font-weight: 600;
  color: #243b53;
  margin-bottom: 12px;
  font-size: 0.98rem;
  padding-left: 10px;
  border-left: 3px solid #42b883;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 16px;
}

.info-item {
  display: flex;
  gap: 4px;
  font-size: 0.9rem;
  align-items: flex-start;
}

.info-label {
  color: #888;
  white-space: nowrap;
  flex-shrink: 0;
}

.reason-text {
  margin: 4px 0;
  color: #2d3748;
  line-height: 1.6;
  font-size: 0.9rem;
}

.reason-text.reject {
  color: #e74c3c;
}

.evidence-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.evidence-img {
  width: 90px;
  height: 90px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid #edf1ef;
}

.timeline {
  list-style: none;
  margin: 0;
  padding: 0 0 0 8px;
  position: relative;
}

.timeline-item {
  position: relative;
  padding: 0 0 18px 20px;
}

.timeline-item:not(.last)::before {
  content: '';
  position: absolute;
  left: 4px;
  top: 16px;
  bottom: 0;
  width: 2px;
  background: #e0dcd5;
}

.timeline-dot {
  position: absolute;
  left: 0;
  top: 6px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #42b883;
  border: 2px solid #fff;
  box-shadow: 0 0 0 2px #42b883;
}

.timeline-content {
  padding-left: 6px;
}

.timeline-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 4px;
}

.timeline-from {
  color: #999;
  font-size: 0.8rem;
}

.timeline-time {
  color: #999;
  font-size: 0.8rem;
  margin-left: auto;
}

.timeline-note {
  color: #4a5568;
  font-size: 0.88rem;
  line-height: 1.5;
  margin: 2px 0;
}

.timeline-operator {
  color: #999;
  font-size: 0.8rem;
}

.action-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-label {
  font-weight: 600;
  color: #2d3748;
  font-size: 0.92rem;
}

.form-textarea,
.form-input {
  padding: 10px 12px;
  border: 1px solid #ccd7d1;
  border-radius: 6px;
  font-size: 0.92rem;
  font-family: inherit;
  transition: border-color 0.2s;
  width: 100%;
  box-sizing: border-box;
}

.form-textarea:focus,
.form-input:focus {
  outline: none;
  border-color: #42b883;
}

.form-textarea {
  resize: vertical;
}

.form-hint {
  color: #999;
  font-size: 0.82rem;
  margin: 0;
}

.modal-footer {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  padding: 14px 20px;
  border-top: 1px solid #edf1ef;
}

@media (max-width: 768px) {
  .content {
    padding: 16px;
  }

  .page-header {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }

  .modal-content {
    width: 96%;
  }
}
</style>
