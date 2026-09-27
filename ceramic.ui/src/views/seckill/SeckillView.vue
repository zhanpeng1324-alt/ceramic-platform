<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/services/api'
import PaymentGatewayModal from '@/components/common/PaymentGatewayModal.vue'
import type { SeckillActivity, SeckillOrder } from '@/types'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const activities = ref<SeckillActivity[]>([])
const myOrders = ref<SeckillOrder[]>([])
const loading = ref(true)
const now = ref(Date.now())
const message = ref('')
const messageType = ref<'ok' | 'err'>('ok')

// 统一收银台（与购物/定制支付同一组件）
const payingOrder = ref<SeckillOrder | null>(null)

let timer: number | undefined

type DisplayStatus = 'PENDING' | 'ACTIVE' | 'ENDED' | 'CANCELLED'
const STATUS_TEXT: Record<DisplayStatus, string> = {
  PENDING: '未开始',
  ACTIVE: '抢购中',
  ENDED: '已结束',
  CANCELLED: '已作废',
}

/** 后端时间格式 "yyyy-MM-dd HH:mm:ss"，归一为 ISO 解析 */
function parseTime(s: string): number {
  return new Date(s.includes('T') ? s : s.replace(' ', 'T')).getTime()
}

function statusOf(a: SeckillActivity): DisplayStatus {
  if (a.status === 'CANCELLED') return 'CANCELLED'
  if (now.value < parseTime(a.startTime)) return 'PENDING'
  if (now.value >= parseTime(a.endTime)) return 'ENDED'
  return 'ACTIVE'
}

function countdownText(a: SeckillActivity): string {
  const st = statusOf(a)
  if (st === 'CANCELLED' || st === 'ENDED') return ''
  const target = st === 'PENDING' ? parseTime(a.startTime) : parseTime(a.endTime)
  let diff = Math.max(0, Math.floor((target - now.value) / 1000))
  const d = Math.floor(diff / 86400)
  diff %= 86400
  const h = Math.floor(diff / 3600)
  diff %= 3600
  const m = Math.floor(diff / 60)
  const s = diff % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return (d > 0 ? `${d}天 ` : '') + `${pad(h)}:${pad(m)}:${pad(s)}`
}

function soldOf(a: SeckillActivity): number {
  return Math.max(0, a.totalStock - a.availableStock)
}

function percentOf(a: SeckillActivity): number {
  if (a.totalStock <= 0) return 0
  return Math.min(100, Math.round((soldOf(a) / a.totalStock) * 100))
}

async function loadAll() {
  loading.value = true
  try {
    // 作废活动不对外展示（已结束的正常保留显示"已结束"）
    activities.value = (await api.seckillActivities()).filter((a) => a.status !== 'CANCELLED')
    if (userStore.isLoggedIn) await refreshOrders()
  } catch (e) {
    message.value = (e as Error).message
    messageType.value = 'err'
  } finally {
    loading.value = false
  }
}

async function refreshOrders() {
  try {
    myOrders.value = await api.seckillMyOrders()
  } catch {
    /* 未登录或网络异常时忽略 */
  }
}

async function buy(a: SeckillActivity) {
  // 抢购动作在商品详情页完成：专区只做入口与秒杀单管理
  router.push(`/products/${a.productId}`)
}

function openPay(order: SeckillOrder) {
  payingOrder.value = order
}

// 统一收银台支付成功：正式订单已生成，刷新秒杀单状态
async function onSeckillPaid() {
  payingOrder.value = null
  message.value = '支付成功！正式订单已生成，请到【订单】页补填收货地址'
  messageType.value = 'ok'
  await refreshOrders()
}

function orderStatusText(s: string): string {
  switch (s) {
    case 'PENDING': return '待支付'
    case 'PAID': return '已支付'
    case 'CANCELLED': return '已取消'
    case 'REFUNDED': return '已退款'
    default: return s
  }
}

function fmtTime(s?: string): string {
  return (s || '').replace('T', ' ')
}

onMounted(() => {
  loadAll()
  timer = window.setInterval(() => { now.value = Date.now() }, 1000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="seckill-page">
    <div class="page-header">
      <h1>秒杀专区</h1>
      <p>限量抢购 · 每人限购一件 · 下单后 30 分钟内完成支付</p>
    </div>

    <div v-if="message" :class="['message', messageType]">{{ message }}</div>

    <div v-if="loading" class="loading">加载中…</div>

    <div v-else class="activity-grid">
      <div v-for="a in activities" :key="a.id" class="activity-card">
        <div class="img-wrap">
          <img :src="a.productImage || `/images/products/${a.productId}.jpg`" :alt="a.productName" />
          <span :class="['status-badge', statusOf(a).toLowerCase()]">{{ STATUS_TEXT[statusOf(a)] }}</span>
        </div>
        <div class="card-body">
          <h3 class="p-name">{{ a.productName }}</h3>
          <div class="price-row">
            <span class="seckill-price">¥{{ a.seckillPrice }}</span>
            <span class="original-price">¥{{ a.originalPrice }}</span>
          </div>
          <div class="progress-row">
            <div class="progress-bar">
              <div class="progress-inner" :style="{ width: percentOf(a) + '%' }"></div>
            </div>
            <span class="sold-text">已抢 {{ soldOf(a) }}/{{ a.totalStock }}</span>
          </div>
          <div class="action-row">
            <span v-if="countdownText(a)" class="countdown">
              {{ statusOf(a) === 'PENDING' ? '距开始 ' : '距结束 ' }}{{ countdownText(a) }}
            </span>
            <button
              class="buy-btn"
              :disabled="statusOf(a) !== 'ACTIVE' || a.availableStock <= 0"
              @click="buy(a)"
            >
              <span v-if="statusOf(a) === 'PENDING'">未开始</span>
              <span v-else-if="statusOf(a) === 'ENDED' || statusOf(a) === 'CANCELLED'">已结束</span>
              <span v-else-if="a.availableStock <= 0">已抢光</span>
              <span v-else>立即抢购</span>
            </button>
          </div>
        </div>
      </div>
      <div v-if="activities.length === 0" class="empty">暂无秒杀活动，敬请期待</div>
    </div>

    <section v-if="userStore.isLoggedIn" class="my-orders">
      <h2>我的秒杀单</h2>
      <div v-if="myOrders.length" class="order-list">
        <div v-for="o in myOrders" :key="o.id" class="order-row">
          <img class="order-img" :src="o.productImage || `/images/products/${o.productId}.jpg`" :alt="o.productName" />
          <div class="order-info">
            <span class="order-name">{{ o.productName }}</span>
            <span class="order-time">{{ fmtTime(o.createdAt) }}</span>
          </div>
          <span class="order-price">¥{{ o.payAmount }}</span>
          <span :class="['order-status', o.status.toLowerCase()]">{{ orderStatusText(o.status) }}</span>
          <button
            v-if="o.status === 'PENDING'"
            class="pay-btn"
            @click="openPay(o)"
          >去支付</button>
          <span v-else class="order-no">{{ o.orderNo }}</span>
        </div>
      </div>
      <p v-else class="empty">还没有秒杀订单</p>
    </section>

    <!-- 统一收银台：与购物/定制支付同一组件 -->
    <PaymentGatewayModal
      :visible="!!payingOrder"
      biz-type="SECKILL"
      :biz-id="payingOrder?.id ?? null"
      :amount="payingOrder?.payAmount ?? 0"
      title="秒杀订单支付"
      initial-channel="alipay"
      @paid="onSeckillPaid"
      @close="payingOrder = null"
    />
  </div>
</template>

<style scoped>
.seckill-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 2rem 1rem;
}

.page-header {
  text-align: center;
  margin-bottom: 1.5rem;
}

.page-header h1 {
  font-size: 2rem;
  margin: 0 0 0.5rem;
  background: linear-gradient(135deg, #e63946, #ff7b54);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  display: inline-block;
}

.page-header p {
  color: #8a8f98;
  font-size: 0.9rem;
  margin: 0;
}

.message {
  max-width: 640px;
  margin: 0 auto 1rem;
  padding: 0.65rem 1rem;
  border-radius: 10px;
  font-size: 0.9rem;
  text-align: center;
}

.message.ok {
  background: #e8f6f3;
  color: #1d7a6f;
}

.message.err {
  background: #fdeaea;
  color: #c0392b;
}

.loading {
  text-align: center;
  color: #8a8f98;
  padding: 3rem 0;
}

.activity-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1.25rem;
}

.activity-card {
  background: #fff;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.activity-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.1);
}

.img-wrap {
  position: relative;
  height: 200px;
  background: #f5f6f7;
}

.img-wrap img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.status-badge {
  position: absolute;
  top: 10px;
  left: 10px;
  padding: 0.25rem 0.65rem;
  border-radius: 999px;
  font-size: 0.75rem;
  color: #fff;
}

.status-badge.active { background: #e63946; }
.status-badge.pending { background: #e8a33d; }
.status-badge.ended,
.status-badge.cancelled { background: #9aa0a6; }

.card-body {
  padding: 1rem;
}

.p-name {
  font-size: 1rem;
  margin: 0 0 0.5rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
}

.seckill-price {
  color: #e63946;
  font-size: 1.4rem;
  font-weight: 700;
}

.original-price {
  color: #b0b6bd;
  font-size: 0.85rem;
  text-decoration: line-through;
}

.progress-row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin: 0.75rem 0;
}

.progress-bar {
  flex: 1;
  height: 8px;
  border-radius: 999px;
  background: #fdeeec;
  overflow: hidden;
}

.progress-inner {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #ff7b54, #e63946);
  transition: width 0.4s ease;
}

.sold-text {
  font-size: 0.75rem;
  color: #8a8f98;
  white-space: nowrap;
}

.action-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.countdown {
  font-size: 0.8rem;
  color: #e63946;
  font-variant-numeric: tabular-nums;
}

.buy-btn {
  padding: 0.5rem 1.2rem;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #e63946, #ff7b54);
  color: #fff;
  font-size: 0.9rem;
  cursor: pointer;
  transition: opacity 0.2s, transform 0.15s;
}

.buy-btn:hover:not(:disabled) {
  opacity: 0.9;
  transform: translateY(-1px);
}

.buy-btn:disabled {
  background: #d3d7dc;
  cursor: not-allowed;
}

.my-orders {
  margin-top: 2.5rem;
}

.my-orders h2 {
  font-size: 1.2rem;
  margin-bottom: 1rem;
}

.order-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.order-row {
  display: flex;
  align-items: center;
  gap: 1rem;
  background: #fff;
  border-radius: 12px;
  padding: 0.75rem 1rem;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.05);
}

.order-img {
  width: 56px;
  height: 56px;
  border-radius: 8px;
  object-fit: cover;
  background: #f5f6f7;
}

.order-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  min-width: 0;
}

.order-name {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-time {
  font-size: 0.78rem;
  color: #8a8f98;
}

.order-price {
  color: #e63946;
  font-weight: 700;
}

.order-status {
  font-size: 0.85rem;
}

.order-status.paid { color: #1d7a6f; }
.order-status.pending { color: #e8a33d; }
.order-status.cancelled { color: #9aa0a6; }
.order-status.refunded { color: #b45f3c; }

.pay-btn {
  padding: 0.4rem 1rem;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #e63946, #ff7b54);
  color: #fff;
  cursor: pointer;
  font-size: 0.85rem;
}

.order-no {
  font-size: 0.75rem;
  color: #b0b6bd;
}

.empty {
  text-align: center;
  color: #8a8f98;
  padding: 2rem 0;
}
</style>
