<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/services/api'
import { useNotificationStore } from '@/stores/notification'
import type { Customization, CustomizationProgress } from '@/types'
import PaymentGatewayModal from '@/components/common/PaymentGatewayModal.vue'

const router = useRouter()
const notificationStore = useNotificationStore()

const items = ref<Customization[]>([])
const loading = ref(true)
const selectedItem = ref<Customization | null>(null)
const progressList = ref<CustomizationProgress[]>([])
const showDetail = ref(false)
const acting = ref(false)

// —— 支付收银台弹窗 ——
const showPayModal = ref(false)
const payBizType = ref<'CUSTOM_DEPOSIT' | 'CUSTOM_BALANCE'>('CUSTOM_DEPOSIT')
const payAmount = ref<number | string>(0)
const payTitle = ref('模拟收银台')

const labels: Record<string, string> = {
  PENDING: '待报价',
  QUOTED: '已报价',
  CONFIRMED: '已确认',
  IN_PROGRESS: '制作中',
  QUALITY_CHECK: '质检中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REJECTED: '已拒绝'
}

// 定制方案 designSpecifications 里的枚举值 → 中文，供结构化展示
const specValueLabels: Record<string, string> = {
  teacup: '茶杯', vase: '花瓶', bowl: '碗', plate: '盘', tableware_set: '餐具套装', ornament: '摆件',
  kaolin: '高岭土', porcelain_clay: '瓷土', stoneware_clay: '陶土',
  celadon: '青釉', white_glaze: '白釉', blue_and_white: '青花', crystalline_glaze: '结晶釉',
  small: '小号', medium: '中号', large: '大号'
}

const specLabel = (v?: string) => (v ? (specValueLabels[v] || v) : '')

interface ParsedDesign {
  shapeType?: string
  baseMaterial?: string
  glazeType?: string
  size?: string
  customText?: string
  customPattern?: string
  additionalNotes?: string
}

/** 解析选中定制单的设计方案 JSON，用于结构化展示；解析失败返回 null。 */
const parsedDesign = computed<ParsedDesign | null>(() => {
  const raw = selectedItem.value?.designSpecifications
  if (!raw) return null
  try {
    const obj = typeof raw === 'string' ? JSON.parse(raw) : raw
    return obj && typeof obj === 'object' ? (obj as ParsedDesign) : null
  } catch {
    return null
  }
})

const statusClass = (s: string) => `st-${s}`

const terminalStatuses = ['COMPLETED', 'CANCELLED', 'REJECTED']

const sortedItems = computed(() => [...items.value].sort((a, b) => b.id - a.id))

const formatDate = (d?: string) => {
  if (!d) return '-'
  try {
    return new Date(d).toLocaleString('zh-CN')
  } catch {
    return d
  }
}

const load = async () => {
  loading.value = true
  try {
    items.value = await api.customizations()
  } catch (e) {
    console.error('加载定制单失败', e)
  } finally {
    loading.value = false
  }
}

const openDetail = async (item: Customization) => {
  selectedItem.value = item
  showDetail.value = true
  progressList.value = []
  try {
    progressList.value = await api.customizationProgress(item.id)
  } catch (e) {
    console.error('加载进度失败', e)
  }
}

const closeDetail = () => {
  showDetail.value = false
  selectedItem.value = null
}

const refreshSelected = async () => {
  if (!selectedItem.value) return
  try {
    const fresh = await api.customizationDetail(selectedItem.value.id)
    selectedItem.value = fresh
    progressList.value = await api.customizationProgress(fresh.id)
    await load()
  } catch (e) {
    console.error(e)
  }
}

const confirmQuote = async () => {
  if (!selectedItem.value) return
  if (!confirm('确认接受此报价？确认后需支付定金即可进入制作。')) return
  acting.value = true
  try {
    await api.confirmCustomizationQuote(selectedItem.value.id)
    await refreshSelected()
  } catch (e) {
    alert(e instanceof Error ? e.message : '操作失败')
  } finally {
    acting.value = false
  }
}

const payDeposit = () => {
  if (!selectedItem.value) return
  payBizType.value = 'CUSTOM_DEPOSIT'
  payAmount.value =
    selectedItem.value.depositAmount || (Number(selectedItem.value.quotedPrice) * 0.3).toFixed(2)
  payTitle.value = '支付定金'
  showPayModal.value = true
}

const payBalance = () => {
  if (!selectedItem.value) return
  payBizType.value = 'CUSTOM_BALANCE'
  payAmount.value =
    selectedItem.value.finalAmount ??
    (Number(selectedItem.value.quotedPrice) - Number(selectedItem.value.depositAmount || 0)).toFixed(2)
  payTitle.value = '支付尾款'
  showPayModal.value = true
}

// 收银台确认支付成功后：关闭弹窗并刷新定制单状态
const onPaid = async () => {
  showPayModal.value = false
  await refreshSelected()
}

const acceptProduct = async () => {
  if (!selectedItem.value) return
  if (!confirm('确认验收此定制成品？验收后订单将标记为已完成。')) return
  acting.value = true
  try {
    await api.acceptCustomization(selectedItem.value.id)
    await refreshSelected()
  } catch (e) {
    alert(e instanceof Error ? e.message : '操作失败')
  } finally {
    acting.value = false
  }
}

const rejectQuote = async () => {
  if (!selectedItem.value) return
  const reason = prompt('请输入拒绝原因（可选）') ?? ''
  if (reason === null) return
  if (!confirm('确定拒绝此报价？')) return
  acting.value = true
  try {
    await api.rejectCustomizationQuote(selectedItem.value.id, reason || undefined)
    await refreshSelected()
  } catch (e) {
    alert(e instanceof Error ? e.message : '操作失败')
  } finally {
    acting.value = false
  }
}

const cancel = async () => {
  if (!selectedItem.value) return
  const reason = prompt('请输入取消原因（可选）') ?? ''
  if (!confirm('确定取消此定制单？')) return
  acting.value = true
  try {
    await api.cancelCustomization(selectedItem.value.id, reason || undefined)
    await refreshSelected()
  } catch (e) {
    alert(e instanceof Error ? e.message : '操作失败')
  } finally {
    acting.value = false
  }
}

onMounted(load)

// 实时刷新：定制单状态变更时后端会推送 CUSTOMIZATION 类型的站内通知，
// 通知 store 已订阅 /user/queue/notifications 并把新通知 prepend 到 list。
// 这里监听最新通知，收到定制相关通知即静默刷新列表与当前打开的详情，
// 无需用户手动刷新页面。
const stopWatch = watch(
  () => notificationStore.list[0],
  (latest, prev) => {
    if (!latest || latest.id === prev?.id) return
    if (latest.type === 'CUSTOMIZATION') {
      load()
      if (showDetail.value && selectedItem.value) {
        refreshSelected()
      }
    }
  }
)

onUnmounted(() => stopWatch())
</script>

<template>
  <div class="my-custom-page">
    <div class="container">
      <header class="page-header">
        <div>
          <h1>我的定制</h1>
          <p>查看定制进度、确认报价与跟进制作</p>
        </div>
        <div class="header-actions">
          <button class="btn-ghost" @click="router.push('/')">返回首页</button>
          <button class="btn-primary" @click="router.push('/customize')">新建定制</button>
        </div>
      </header>

      <p v-if="loading" class="loading-text">加载中…</p>

      <div v-else-if="!sortedItems.length" class="empty">
        <p>您还没有定制单</p>
        <button class="btn-primary" @click="router.push('/customize')">去定制</button>
      </div>

      <div v-else class="cards-grid">
        <article
          v-for="item in sortedItems"
          :key="item.id"
          class="card"
          @click="openDetail(item)"
        >
          <div class="card-top">
            <strong class="card-no">#{{ item.id }}</strong>
            <span class="status" :class="statusClass(item.status)">{{ labels[item.status] || item.status }}</span>
          </div>
          <div class="card-body">
            <div class="info-row"><span class="label">器型</span><span>{{ specLabel(item.shape) || '-' }}</span></div>
            <div class="info-row"><span class="label">釉色</span><span>{{ specLabel(item.glazeColor) || '-' }}</span></div>
            <div class="info-row"><span class="label">尺寸</span><span>{{ specLabel(item.size) || '-' }}</span></div>
            <div class="info-row"><span class="label">数量</span><span>{{ item.quantity || 1 }} 件</span></div>
            <div class="info-row"><span class="label">需求</span><span class="ellipsis">{{ item.requirement || '未填写' }}</span></div>
            <div class="info-row" v-if="item.quotedPrice">
              <span class="label">报价</span><span class="price">¥{{ item.quotedPrice }}</span>
            </div>
            <div class="info-row" v-if="item.expectedCompleteDate">
              <span class="label">预计完成</span><span>{{ item.expectedCompleteDate }}</span>
            </div>
            <div class="info-row"><span class="label">提交时间</span><span>{{ formatDate(item.createdAt) }}</span></div>
          </div>
          <div v-if="item.status === 'QUOTED'" class="card-tip">待您确认报价，点击查看</div>
          <div v-else-if="item.status === 'CONFIRMED' && !item.depositPaid" class="card-tip">待支付定金，点击查看</div>
          <div v-else-if="item.status === 'QUALITY_CHECK' && !item.finalPaid" class="card-tip">成品待验收 · 请支付尾款</div>
          <div v-else-if="item.status === 'QUALITY_CHECK' && item.finalPaid" class="card-tip">尾款已付 · 请确认验收</div>
        </article>
      </div>

      <!-- 详情弹窗 -->
      <div v-if="showDetail && selectedItem" class="modal-mask" @click.self="closeDetail">
        <div class="modal">
          <div class="modal-header">
            <div>
              <h2>定制单 #{{ selectedItem.id }}</h2>
              <p class="modal-sub">
                <span class="status" :class="statusClass(selectedItem.status)">{{ labels[selectedItem.status] || selectedItem.status }}</span>
              </p>
            </div>
            <button class="btn-close" @click="closeDetail">×</button>
          </div>

          <div class="modal-body">
            <!-- 基本信息 -->
            <section class="section">
              <h3>定制需求</h3>
              <div class="detail-grid">
                <div class="info-row"><span class="label">器型</span><span>{{ specLabel(selectedItem.shape) || '-' }}</span></div>
                <div class="info-row"><span class="label">材质</span><span>{{ specLabel(parsedDesign?.baseMaterial) || specLabel(selectedItem.material) || '-' }}</span></div>
                <div class="info-row"><span class="label">釉色</span><span>{{ specLabel(selectedItem.glazeColor) || '-' }}</span></div>
                <div class="info-row"><span class="label">尺寸</span><span>{{ specLabel(selectedItem.size) || '-' }}</span></div>
                <div class="info-row"><span class="label">数量</span><span>{{ selectedItem.quantity || 1 }} 件</span></div>
                <div class="info-row"><span class="label">预算</span><span>¥{{ selectedItem.budget || selectedItem.price || 0 }}</span></div>
                <div class="info-row" v-if="selectedItem.inscription || parsedDesign?.customText">
                  <span class="label">刻字</span><span>{{ selectedItem.inscription || parsedDesign?.customText }}</span>
                </div>
                <div class="info-row" v-if="selectedItem.pattern || parsedDesign?.customPattern">
                  <span class="label">图案</span><span>{{ selectedItem.pattern || parsedDesign?.customPattern }}</span>
                </div>
                <div class="info-row"><span class="label">联系人</span><span>{{ selectedItem.contactName }} · {{ selectedItem.contactPhone }}</span></div>
                <div class="info-row full" v-if="selectedItem.shippingAddress">
                  <span class="label">收货地址</span><span>{{ selectedItem.shippingAddress }}</span>
                </div>
                <div class="info-row full"><span class="label">需求说明</span><span>{{ selectedItem.requirement || parsedDesign?.additionalNotes || '无' }}</span></div>
                <div class="info-row full" v-if="selectedItem.designImageUrl">
                  <span class="label">设计图</span>
                  <img :src="selectedItem.designImageUrl" alt="设计图" class="design-img" />
                </div>
              </div>
            </section>

            <!-- 报价信息 -->
            <section class="section" v-if="selectedItem.quotedPrice">
              <h3>报价信息</h3>
              <div class="quote-box">
                <div class="quote-row"><span>报价金额</span><strong>¥{{ selectedItem.quotedPrice }}</strong></div>
                <div class="quote-row"><span>定金（30%）</span><strong>¥{{ selectedItem.depositAmount || (Number(selectedItem.quotedPrice) * 0.3).toFixed(2) }}<span v-if="selectedItem.depositPaid" class="paid-tag">已支付</span></strong></div>
                <div class="quote-row"><span>尾款</span><strong>¥{{ selectedItem.finalAmount ?? (Number(selectedItem.quotedPrice) - Number(selectedItem.depositAmount || 0)).toFixed(2) }}<span v-if="selectedItem.finalPaid" class="paid-tag">已支付</span></strong></div>
                <div class="quote-row" v-if="selectedItem.expectedCompleteDate"><span>预计完成日期</span><strong>{{ selectedItem.expectedCompleteDate }}</strong></div>
                <div class="quote-row" v-if="selectedItem.timelineNote"><span>制作说明</span><span>{{ selectedItem.timelineNote }}</span></div>
              </div>
            </section>

            <!-- 成品图 -->
            <section class="section" v-if="selectedItem.finishedProductUrl">
              <h3>成品图</h3>
              <img :src="selectedItem.finishedProductUrl" alt="成品图" class="finished-img" />
            </section>

            <!-- 进度时间线 -->
            <section class="section">
              <h3>制作进度时间线</h3>
              <div class="timeline">
                <div v-if="!progressList.length" class="empty-mini">暂无进度记录</div>
                <div v-for="p in progressList" :key="p.id" class="timeline-item">
                  <div class="dot"></div>
                  <div class="timeline-content">
                    <div class="timeline-stage">{{ p.stage }}</div>
                    <div class="timeline-desc">{{ p.description }}</div>
                    <div class="timeline-meta">
                      <span>{{ p.operatorRole === 'admin' ? '管理员' : (p.operatorRole === 'customer' ? '客户' : (p.operatorRole || '')) }}</span>
                      <span>{{ formatDate(p.createdAt) }}</span>
                    </div>
                    <img v-if="p.imageUrl" :src="p.imageUrl" alt="进度图" class="timeline-img" />
                  </div>
                </div>
              </div>
            </section>

            <!-- 操作按钮 -->
            <section class="actions" v-if="selectedItem.status === 'QUOTED' || !terminalStatuses.includes(selectedItem.status)">
              <button v-if="selectedItem.status === 'QUOTED'" class="btn-primary" :disabled="acting" @click="confirmQuote">确认报价</button>
              <button v-if="selectedItem.status === 'QUOTED'" class="btn-danger" :disabled="acting" @click="rejectQuote">拒绝报价</button>
              <button v-if="selectedItem.status === 'CONFIRMED' && !selectedItem.depositPaid" class="btn-primary" :disabled="acting" @click="payDeposit">支付定金</button>
              <button v-if="selectedItem.status === 'QUALITY_CHECK' && !selectedItem.finalPaid" class="btn-primary" :disabled="acting" @click="payBalance">支付尾款</button>
              <button v-if="selectedItem.status === 'QUALITY_CHECK' && selectedItem.finalPaid" class="btn-primary" :disabled="acting" @click="acceptProduct">确认验收</button>
              <button v-if="!terminalStatuses.includes(selectedItem.status)" class="btn-ghost" :disabled="acting" @click="cancel">取消定制</button>
            </section>
          </div>
        </div>
      </div>

      <!-- 支付收银台弹窗 -->
      <PaymentGatewayModal
        :visible="showPayModal"
        :biz-type="payBizType"
        :biz-id="selectedItem?.id ?? null"
        :amount="payAmount"
        :title="payTitle"
        @paid="onPaid"
        @close="showPayModal = false"
      />
    </div>
  </div>
</template>

<style scoped>
.my-custom-page {
  padding: 2rem 0;
  min-height: calc(100vh - 120px);
  background: var(--c-bg);
  color: var(--c-text);
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 1rem;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.page-header h1 {
  margin: 0 0 0.25rem;
  color: var(--c-text);
  font-size: 1.6rem;
}

.page-header p {
  margin: 0;
  color: var(--c-text-muted);
  font-size: 0.9rem;
}

.header-actions {
  display: flex;
  gap: 0.6rem;
}

.loading-text, .empty {
  text-align: center;
  padding: 3rem 1rem;
  color: var(--c-text-muted);
  background: var(--c-surface);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

.empty p {
  margin: 0 0 1rem;
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 1rem;
}

.card {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  padding: 1.1rem 1.2rem;
  box-shadow: var(--shadow);
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}

.card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.8rem;
}

.card-no {
  color: var(--c-text);
  font-size: 1rem;
}

.status {
  padding: 0.2rem 0.6rem;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 600;
}

.st-PENDING { background: #fef3e2; color: #b9770e; }
.st-QUOTED { background: #e8f4fd; color: #2874a6; }
.st-CONFIRMED { background: #e8f8f0; color: #1e8449; }
.st-IN_PROGRESS { background: #fdf2e9; color: #ca6f1e; }
.st-QUALITY_CHECK { background: #f4ecf7; color: #7d3c98; }
.st-COMPLETED { background: #e8f8f0; color: #1e8449; }
.st-CANCELLED { background: #fadbd8; color: #922b21; }
.st-REJECTED { background: #fadbd8; color: #922b21; }

.card-body {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  font-size: 0.88rem;
}

.info-row {
  display: flex;
  gap: 0.6rem;
  line-height: 1.6;
}

.info-row .label {
  flex: 0 0 70px;
  color: #95a5a6;
  font-size: 0.82rem;
}

.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.price {
  color: var(--c-accent);
  font-weight: 700;
}

.card-tip {
  margin-top: 0.7rem;
  padding-top: 0.6rem;
  border-top: 1px dashed var(--c-border);
  font-size: 0.8rem;
  color: var(--c-primary-dark);
  font-weight: 600;
  text-align: center;
}

.paid-tag {
  margin-left: 0.5rem;
  padding: 0.1rem 0.45rem;
  border-radius: 10px;
  background: #e8f8f0;
  color: #1e8449;
  font-size: 0.72rem;
  font-weight: 600;
}

/* 弹窗 */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(36, 59, 83, 0.5);
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 2rem 1rem;
  z-index: 1000;
  overflow-y: auto;
}

.modal {
  background: #fff;
  border-radius: 12px;
  width: 100%;
  max-width: 720px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.2);
  overflow: hidden;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 1.1rem 1.3rem;
  border-bottom: 1px solid #eef2ef;
}

.modal-header h2 {
  margin: 0;
  font-size: 1.2rem;
  color: #243b53;
}

.modal-sub {
  margin: 0.4rem 0 0;
}

.btn-close {
  border: 0;
  background: transparent;
  font-size: 1.6rem;
  cursor: pointer;
  color: #718096;
  line-height: 1;
}

.modal-body {
  padding: 1.3rem;
  max-height: 70vh;
  overflow-y: auto;
}

.section {
  margin-bottom: 1.3rem;
}

.section h3 {
  margin: 0 0 0.7rem;
  font-size: 1rem;
  color: #243b53;
  padding-bottom: 0.4rem;
  border-bottom: 1px solid #eef2ef;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.5rem;
  font-size: 0.9rem;
}

.detail-grid .info-row.full {
  grid-column: 1 / -1;
}

.quote-box {
  background: #f9fbfa;
  border-radius: 8px;
  padding: 0.9rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  font-size: 0.9rem;
}

.quote-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.8rem;
}

.quote-row strong {
  color: #1e8449;
}

.design-img, .finished-img {
  max-width: 100%;
  max-height: 220px;
  border-radius: 8px;
  display: block;
}

/* 时间线 */
.timeline {
  position: relative;
  padding-left: 1.2rem;
  border-left: 2px solid #e1e8e4;
  margin-left: 0.4rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.timeline-item {
  position: relative;
}

.dot {
  position: absolute;
  left: -1.6rem;
  top: 0.2rem;
  width: 0.7rem;
  height: 0.7rem;
  border-radius: 50%;
  background: var(--c-primary);
  border: 2px solid var(--c-surface);
  box-shadow: 0 0 0 2px var(--c-primary);
}

.timeline-content {
  background: #f9fbfa;
  border-radius: 8px;
  padding: 0.7rem 0.9rem;
}

.timeline-stage {
  font-weight: 600;
  color: #2c4a3e;
  font-size: 0.92rem;
}

.timeline-desc {
  margin-top: 0.25rem;
  font-size: 0.85rem;
  color: #384951;
  line-height: 1.6;
  word-break: break-word;
}

.timeline-meta {
  margin-top: 0.4rem;
  font-size: 0.75rem;
  color: #95a5a6;
  display: flex;
  gap: 0.8rem;
}

.timeline-img {
  margin-top: 0.5rem;
  width: 100%;
  max-height: 200px;
  object-fit: cover;
  border-radius: 6px;
}

.empty-mini {
  color: #718096;
  font-size: 0.85rem;
  padding: 0.6rem 0;
}

.actions {
  display: flex;
  gap: 0.7rem;
  flex-wrap: wrap;
  padding-top: 0.8rem;
  border-top: 1px solid #eef2ef;
}

.btn-primary {
  border: 0;
  border-radius: var(--radius-sm);
  padding: 0.55rem 1.2rem;
  background: var(--c-primary);
  color: #fff;
  cursor: pointer;
  font-size: 0.9rem;
  transition: background 0.15s;
}

.btn-primary:hover { background: var(--c-primary-dark); }
.btn-primary:disabled { background: #a0c4b5; cursor: not-allowed; }

.btn-danger {
  border: 0;
  border-radius: 6px;
  padding: 0.55rem 1.2rem;
  background: #e74c3c;
  color: #fff;
  cursor: pointer;
  font-size: 0.9rem;
}

.btn-danger:hover { background: #cb4335; }
.btn-danger:disabled { background: #f1948a; cursor: not-allowed; }

.btn-ghost {
  border: 1px solid var(--c-border);
  border-radius: var(--radius-sm);
  padding: 0.5rem 1.1rem;
  background: var(--c-surface);
  color: var(--c-text);
  cursor: pointer;
  font-size: 0.9rem;
}

.btn-ghost:hover { background: var(--c-primary-soft); }
.btn-ghost:disabled { opacity: 0.6; cursor: not-allowed; }

@media (max-width: 768px) {
  .cards-grid { grid-template-columns: 1fr; }
  .detail-grid { grid-template-columns: 1fr; }
  .page-header { flex-direction: column; align-items: flex-start; gap: 0.8rem; }
}
</style>
