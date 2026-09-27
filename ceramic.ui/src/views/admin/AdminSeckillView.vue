<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { api } from '@/services/api'
import AdminNavbar from '@/components/admin/AdminNavbar.vue'
import type { Product, SeckillActivity } from '@/types'

const activities = ref<SeckillActivity[]>([])
const products = ref<Product[]>([])
const loading = ref(true)
const message = ref('')
const messageType = ref<'ok' | 'err'>('ok')
const submitting = ref(false)

// 创建表单
const form = ref({
  productId: 0,
  seckillPrice: 0,
  totalStock: 10,
  startTime: '',
  endTime: '',
})

function nowInput(offsetMin: number): string {
  const d = new Date(Date.now() + offsetMin * 60 * 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 用户是否手动改过结束时间：没改过则始终联动为 开始+24h */
const endTouched = ref(false)

watch(() => form.value.startTime, (v) => {
  if (!v || endTouched.value) return
  const ms = new Date(v).getTime()
  if (Number.isNaN(ms)) return
  // 结束时间联动：始终 = 开始时间 + 24 小时
  form.value.endTime = new Date(ms + 24 * 3600 * 1000)
    .toLocaleString('sv-SE', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
    .replace(' ', 'T')
})

/** 表单当前实际活动时长预览 */
const durationText = computed(() => {
  const s = new Date(form.value.startTime).getTime()
  const e = new Date(form.value.endTime).getTime()
  if (Number.isNaN(s) || Number.isNaN(e) || e <= s) return '—'
  let min = Math.round((e - s) / 60000)
  const d = Math.floor(min / 1440)
  min %= 1440
  const h = Math.floor(min / 60)
  min %= 60
  const parts: string[] = []
  if (d) parts.push(`${d} 天`)
  if (h) parts.push(`${h} 小时`)
  if (min || parts.length === 0) parts.push(`${min} 分钟`)
  return parts.join(' ')
})

function fmtTime(s?: string): string {
  return (s || '').replace('T', ' ')
}

/** 展示状态按当前时间实时计算（与后端口径一致） */
function statusOf(a: SeckillActivity): string {
  if (a.status === 'CANCELLED') return 'CANCELLED'
  const now = Date.now()
  if (now < parseTime(a.startTime)) return 'PENDING'
  if (now >= parseTime(a.endTime)) return 'ENDED'
  return 'ACTIVE'
}

function parseTime(s: string): number {
  return new Date(s.includes('T') ? s : s.replace(' ', 'T')).getTime()
}

const STATUS_TEXT: Record<string, string> = {
  PENDING: '未开始',
  ACTIVE: '抢购中',
  ENDED: '已结束',
  CANCELLED: '已作废',
}

async function loadAll() {
  loading.value = true
  try {
    const [acts, prods] = await Promise.all([api.seckillAdminList(), api.adminProducts()])
    activities.value = acts
    products.value = prods.filter((p) => p.status === 'active')
    const first = products.value[0]
    if (form.value.productId === 0 && first) {
      form.value.productId = first.id
    }
  } catch (e) {
    message.value = (e as Error).message
    messageType.value = 'err'
  } finally {
    loading.value = false
  }
}

async function create() {
  if (submitting.value) return
  if (!form.value.productId) {
    message.value = '请选择商品'
    messageType.value = 'err'
    return
  }
  if (form.value.seckillPrice <= 0 || form.value.totalStock <= 0) {
    message.value = '秒杀价与库存必须大于 0'
    messageType.value = 'err'
    return
  }
  submitting.value = true
  message.value = ''
  try {
    // 开始时间已过（填表期间时间流逝）：自动改为立即开始，避免时间窗错位
    let startStr = form.value.startTime.replace('T', ' ')
    if (new Date(form.value.startTime).getTime() < Date.now()) {
      startStr = new Date().toLocaleString('sv-SE', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }).replace(' ', ' ')
    }
    await api.seckillAdminCreate({
      productId: form.value.productId,
      seckillPrice: form.value.seckillPrice,
      totalStock: form.value.totalStock,
      startTime: startStr,
      endTime: form.value.endTime.replace('T', ' '),
    })
    message.value = `活动已创建：${startStr} 至 ${form.value.endTime.replace('T', ' ')}（时长 ${durationText.value}），库存已预热进 Redis`
    messageType.value = 'ok'
    endTouched.value = false
    form.value.seckillPrice = 0
    form.value.totalStock = 10
    form.value.startTime = nowInput(5)
    form.value.endTime = nowInput(24 * 60)
    await loadAll()
  } catch (e) {
    message.value = (e as Error).message
    messageType.value = 'err'
  } finally {
    submitting.value = false
  }
}

async function offline(a: SeckillActivity) {
  if (!confirm(`确认下线活动「${a.productName}」？下线后立即停止售卖，已抢到订单仍可正常支付`)) return
  try {
    await api.seckillAdminCancel(a.id)
    message.value = '活动已下线'
    messageType.value = 'ok'
    await loadAll()
  } catch (e) {
    message.value = (e as Error).message
    messageType.value = 'err'
  }
}

onMounted(() => {
  form.value.startTime = nowInput(5)
  form.value.endTime = nowInput(24 * 60)
  loadAll()
})
</script>

<template>
  <div class="admin-page">
    <AdminNavbar />
    <h1>秒杀活动管理</h1>

    <div v-if="message" :class="['msg', messageType]">{{ message }}</div>

    <!-- 创建活动 -->
    <div class="panel">
      <h2>创建秒杀活动</h2>
      <div class="form-grid">
        <label>
          商品
          <select v-model.number="form.productId">
            <option v-for="p in products" :key="p.id" :value="p.id">
              {{ p.name }}（现价 ¥{{ p.price }}）
            </option>
          </select>
        </label>
        <label>
          秒杀价（元）
          <input v-model.number="form.seckillPrice" type="number" min="0.01" step="0.01" />
        </label>
        <label>
          秒杀库存（件）
          <input v-model.number="form.totalStock" type="number" min="1" step="1" />
        </label>
        <label>
          开始时间
          <input v-model="form.startTime" type="datetime-local" />
        </label>
        <label>
          结束时间
          <input v-model="form.endTime" type="datetime-local" @change="endTouched = true" />
        </label>
        <button class="btn-create" :disabled="submitting" @click="create">
          {{ submitting ? '创建中…' : '创建活动' }}
        </button>
      </div>
      <p class="tip">活动时长：{{ durationText }}。改开始时间时结束时间自动跟随（开始 + 24 小时）；手动改过结束时间后不再联动。开始时间已过则立即开始。</p>
    </div>

    <!-- 活动列表 -->
    <div class="panel">
      <h2>活动列表</h2>
      <div v-if="loading" class="empty">加载中…</div>
      <table v-else-if="activities.length" class="act-table">
        <thead>
          <tr>
            <th>商品</th>
            <th>秒杀价</th>
            <th>原价</th>
            <th>库存</th>
            <th>开始时间</th>
            <th>结束时间</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in activities" :key="a.id">
            <td class="p-cell">
              <img :src="a.productImage || `/images/products/${a.productId}.jpg`" :alt="a.productName" />
              <span>{{ a.productName }}</span>
            </td>
            <td class="price">¥{{ a.seckillPrice }}</td>
            <td>¥{{ a.originalPrice }}</td>
            <td>{{ a.availableStock }}/{{ a.totalStock }}</td>
            <td>{{ fmtTime(a.startTime) }}</td>
            <td>{{ fmtTime(a.endTime) }}</td>
            <td>
              <span :class="['badge', statusOf(a).toLowerCase()]">{{ STATUS_TEXT[statusOf(a)] }}</span>
            </td>
            <td>
              <button
                v-if="statusOf(a) === 'PENDING' || statusOf(a) === 'ACTIVE'"
                class="btn-cancel"
                @click="offline(a)"
              >下线</button>
              <span v-else class="noop">—</span>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty">暂无秒杀活动</div>
    </div>
  </div>
</template>

<style scoped>
.admin-page {
  max-width: 1100px;
  margin: 0 auto;
  padding: 2rem 1rem;
}

.admin-page h1 {
  font-size: 1.6rem;
  margin-bottom: 1.25rem;
}

.msg {
  padding: 0.65rem 1rem;
  border-radius: 10px;
  margin-bottom: 1rem;
  font-size: 0.9rem;
}

.msg.ok { background: #e8f6f3; color: #1d7a6f; }
.msg.err { background: #fdeaea; color: #c0392b; }

.panel {
  background: #fff;
  border-radius: 14px;
  padding: 1.25rem 1.5rem;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
  margin-bottom: 1.5rem;
}

.panel h2 {
  font-size: 1.1rem;
  margin: 0 0 1rem;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 0.9rem 1rem;
  align-items: end;
}

.form-grid label {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  font-size: 0.85rem;
  color: #5a6069;
}

.form-grid select,
.form-grid input {
  padding: 0.5rem 0.65rem;
  border: 1px solid #d3d7dc;
  border-radius: 8px;
  font-size: 0.9rem;
}

.btn-create {
  padding: 0.55rem 1.2rem;
  border: none;
  border-radius: 8px;
  background: #2c3e50;
  color: #fff;
  cursor: pointer;
}

.btn-create:disabled { opacity: 0.6; cursor: not-allowed; }

.tip {
  font-size: 0.78rem;
  color: #9aa0a6;
  margin: 0.75rem 0 0;
}

.act-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
}

.act-table th,
.act-table td {
  padding: 0.6rem 0.5rem;
  border-bottom: 1px solid #eef0f2;
  text-align: left;
  white-space: nowrap;
}

.p-cell {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.p-cell img {
  width: 40px;
  height: 40px;
  border-radius: 6px;
  object-fit: cover;
  background: #f5f6f7;
}

.price {
  color: #e63946;
  font-weight: 700;
}

.badge {
  padding: 0.2rem 0.6rem;
  border-radius: 999px;
  font-size: 0.75rem;
  color: #fff;
}

.badge.active { background: #e63946; }
.badge.pending { background: #e8a33d; }
.badge.ended, .badge.cancelled { background: #9aa0a6; }

.btn-cancel {
  padding: 0.3rem 0.8rem;
  border: 1px solid #e0e3e7;
  border-radius: 6px;
  background: #fff;
  color: #c0392b;
  cursor: pointer;
}

.btn-cancel:hover { background: #fdeaea; }

.empty {
  text-align: center;
  color: #8a8f98;
  padding: 1.5rem 0;
}
</style>
