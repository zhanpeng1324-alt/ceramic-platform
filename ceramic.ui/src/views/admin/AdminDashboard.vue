<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import type { ECharts } from 'echarts'
import { useUserStore } from '@/stores/user'
import AdminNavbar from '@/components/admin/AdminNavbar.vue'
import { api } from '@/services/api'
import type { AdminStats } from '@/types'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const stats = ref<AdminStats | null>(null)

const orderLineEl = ref<HTMLDivElement | null>(null)
const salesBarEl = ref<HTMLDivElement | null>(null)
const statusPieEl = ref<HTMLDivElement | null>(null)
const topBarEl = ref<HTMLDivElement | null>(null)

const charts: (ECharts | null)[] = []
let echartsModule: typeof import('echarts') | null = null

const orderStatusLabel: Record<string, string> = {
  PENDING_PAY: '待支付',
  PAID: '已支付',
  SHIPPED: '已发货',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

const last7dOrderTotal = computed(() =>
  stats.value?.orderCount7d.reduce((s, p) => s + p.value, 0) ?? 0
)
const last7dSalesTotal = computed(() =>
  stats.value?.salesAmount7d.reduce((s, p) => s + p.value, 0) ?? 0
)

// 「今日」= 近 7 天序列的最后一天（后端已补齐含当天），无需额外接口
const todayOrders = computed(() => {
  const a = stats.value?.orderCount7d
  return a && a.length ? (a[a.length - 1]?.value ?? 0) : 0
})
const todaySales = computed(() => {
  const a = stats.value?.salesAmount7d
  return a && a.length ? (a[a.length - 1]?.value ?? 0) : 0
})
// 近 7 天客单价 = 销售额 / 订单量
const avgOrderValue7d = computed(() =>
  last7dOrderTotal.value > 0 ? last7dSalesTotal.value / last7dOrderTotal.value : 0
)

const overviewCards = computed(() => [
  { icon: '💴', label: '今日销售额', value: '¥' + todaySales.value.toFixed(2), highlight: true },
  { icon: '🧾', label: '今日订单', value: todayOrders.value, highlight: true },
  { icon: '💰', label: '近7天销售额', value: '¥' + last7dSalesTotal.value.toFixed(2), highlight: false },
  { icon: '🛒', label: '近7天订单', value: last7dOrderTotal.value, highlight: false },
  { icon: '📊', label: '近7天客单价', value: '¥' + avgOrderValue7d.value.toFixed(2), highlight: false }
])

const summaryCards = computed(() => {
  if (!stats.value) return [] as { icon: string; label: string; value: string | number; path: string }[]
  return [
    { icon: '👥', label: '总用户数', value: stats.value.totalUsers, path: '/admin/users' },
    { icon: '🏺', label: '总商品数', value: stats.value.totalProducts, path: '/admin/products' },
    { icon: '⏳', label: '待支付订单', value: stats.value.pendingPayOrders, path: '/admin/orders' },
    { icon: '🔧', label: '待处理售后', value: stats.value.pendingReturns, path: '/admin/support' },
    { icon: '🎨', label: '待审核定制', value: stats.value.pendingCustomizations, path: '/admin/customizations' },
    { icon: '📦', label: '低库存商品', value: stats.value.lowStockProducts, path: '/admin/restock' }
  ]
})

const pendingItems = computed(() => {
  if (!stats.value) return [] as { label: string; count: number; path: string }[]
  return [
    { label: '待支付订单', count: stats.value.pendingPayOrders, path: '/admin/orders' },
    { label: '待处理售后', count: stats.value.pendingReturns, path: '/admin/support' },
    { label: '待审核定制', count: stats.value.pendingCustomizations, path: '/admin/customizations' },
    { label: '低库存商品', count: stats.value.lowStockProducts, path: '/admin/restock' }
  ]
})

const shortDate = (d: string) => d.slice(5)

const renderCharts = async () => {
  if (!stats.value) return
  echartsModule ??= await import('echarts')
  const echarts = echartsModule

  const orderLine = echarts.init(orderLineEl.value!)
  orderLine.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: stats.value.orderCount7d.map(p => shortDate(p.date)) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      name: '订单量', type: 'line', smooth: true,
      data: stats.value.orderCount7d.map(p => p.value),
      itemStyle: { color: '#42b883' },
      areaStyle: { opacity: 0.15 }
    }]
  })
  charts.push(orderLine)

  const salesBar = echarts.init(salesBarEl.value!)
  salesBar.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params: { axisValue: string; data: number }[]) => {
        const item = params[0]
        if (!item) return ''
        return `${item.axisValue}<br/>销售额：¥${item.data.toFixed(2)}`
      }
    },
    grid: { left: 55, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: stats.value.salesAmount7d.map(p => shortDate(p.date)) },
    yAxis: { type: 'value' },
    series: [{
      name: '销售额', type: 'bar',
      data: stats.value.salesAmount7d.map(p => p.value),
      itemStyle: { color: '#3498db' },
      barMaxWidth: 36
    }]
  })
  charts.push(salesBar)

  const statusPie = echarts.init(statusPieEl.value!)
  const pieData = stats.value.orderStatusDistribution.map(s => ({
    name: orderStatusLabel[s.name] || s.name,
    value: s.value
  }))
  statusPie.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      type: 'pie',
      radius: ['40%', '65%'],
      center: ['50%', '45%'],
      data: pieData,
      label: { formatter: '{b}\n{d}%' }
    }]
  })
  charts.push(statusPie)

  const topBar = echarts.init(topBarEl.value!)
  const top = stats.value.topProducts
  topBar.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 110, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: {
      type: 'category',
      data: top.map(p => p.name).reverse(),
      axisLabel: { width: 100, overflow: 'truncate' }
    },
    series: [{
      name: '销量', type: 'bar',
      data: top.map(p => p.value).reverse(),
      itemStyle: { color: '#e67e22' },
      barMaxWidth: 24
    }]
  })
  charts.push(topBar)
}

const handleResize = () => charts.forEach(c => c?.resize())

const load = async () => {
  loading.value = true
  try {
    charts.forEach(c => c?.dispose())
    charts.length = 0
    stats.value = await api.adminStats()
    await nextTick()
    await renderCharts()
  } catch (e) {
    console.error('加载看板数据失败', e)
  } finally {
    loading.value = false
  }
}

const go = (path: string) => router.push(path)

onMounted(() => {
  if (userStore.currentUser?.role !== 'admin') {
    router.push('/')
    return
  }
  load()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  charts.forEach(c => c?.dispose())
})
</script>

<template>
  <div class="admin-dashboard">
    <AdminNavbar />

    <div class="dashboard-content">
      <header class="page-header">
        <div>
          <h1>数据看板</h1>
          <p>欢迎回来，管理员</p>
        </div>
        <button class="btn-refresh" :disabled="loading" @click="load">
          {{ loading ? '加载中…' : '刷新' }}
        </button>
      </header>

      <!-- 经营概览（今日 / 近7天） -->
      <section class="overview-grid">
        <div
          v-for="card in overviewCards"
          :key="card.label"
          class="overview-card"
          :class="{ highlight: card.highlight }"
        >
          <div class="overview-icon">{{ card.icon }}</div>
          <div class="overview-body">
            <div class="overview-value">{{ card.value }}</div>
            <div class="overview-label">{{ card.label }}</div>
          </div>
        </div>
      </section>

      <!-- 概览卡片 -->
      <section class="stat-grid">
        <div
          v-for="card in summaryCards"
          :key="card.label"
          class="stat-card"
          @click="go(card.path)"
        >
          <div class="stat-icon">{{ card.icon }}</div>
          <div class="stat-body">
            <div class="stat-value">{{ card.value }}</div>
            <div class="stat-label">{{ card.label }}</div>
          </div>
        </div>
      </section>

      <!-- 待处理事项 -->
      <section class="panel">
        <div class="panel-header">
          <h2>待处理事项</h2>
          <span class="panel-tip">点击跳转到对应管理页面</span>
        </div>
        <div class="pending-grid">
          <button
            v-for="item in pendingItems"
            :key="item.label"
            class="pending-item"
            :class="{ urgent: item.count > 0 }"
            @click="go(item.path)"
          >
            <span class="pending-label">{{ item.label }}</span>
            <span class="pending-count">{{ item.count }}</span>
          </button>
        </div>
      </section>

      <!-- 图表区 -->
      <section class="charts-grid">
        <div class="chart-card">
          <h3>近 7 天订单数量</h3>
          <div ref="orderLineEl" class="chart-box"></div>
        </div>
        <div class="chart-card">
          <h3>近 7 天销售额</h3>
          <div ref="salesBarEl" class="chart-box"></div>
        </div>
        <div class="chart-card">
          <h3>订单状态占比</h3>
          <div ref="statusPieEl" class="chart-box"></div>
        </div>
        <div class="chart-card">
          <h3>热销商品 Top 5</h3>
          <div ref="topBarEl" class="chart-box"></div>
        </div>
      </section>

      <!-- 低库存商品列表 -->
      <section class="panel">
        <div class="panel-header">
          <h2>低库存商品</h2>
          <button class="btn-link" @click="go('/admin/products')">前往商品管理 →</button>
        </div>
        <div v-if="stats && stats.lowStockList.length" class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>商品ID</th>
                <th>商品名称</th>
                <th>分类ID</th>
                <th>价格</th>
                <th>库存</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in stats.lowStockList" :key="p.id">
                <td>{{ p.id }}</td>
                <td>{{ p.name }}</td>
                <td>{{ p.categoryId }}</td>
                <td>¥{{ p.price }}</td>
                <td><span class="stock-tag">{{ p.stock }}</span></td>
                <td><button class="btn-link" @click="go('/admin/products')">补货</button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-else class="empty-tip">暂无低库存商品</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.admin-dashboard {
  min-height: 100vh;
  background: #f5f7fa;
}

.dashboard-content {
  padding: 1.5rem 2rem 2rem;
  max-width: 1280px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.page-header h1 {
  font-size: 1.6rem;
  color: #2c3e50;
  margin: 0 0 0.25rem;
}

.page-header p {
  color: #7f8c8d;
  margin: 0;
}

.btn-refresh {
  background: #42b883;
  color: #fff;
  border: none;
  padding: 0.55rem 1.2rem;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.9rem;
}

.btn-refresh:disabled {
  background: #bdc3c7;
  cursor: not-allowed;
}

/* 经营概览 */
.overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.overview-card {
  background: #fff;
  border-radius: 8px;
  padding: 1.2rem 1.3rem;
  display: flex;
  align-items: center;
  gap: 0.9rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.overview-card.highlight {
  background: linear-gradient(135deg, #42b883, #2d9d6f);
  color: #fff;
  box-shadow: 0 4px 14px rgba(66, 184, 131, 0.35);
}

.overview-icon {
  font-size: 1.9rem;
  width: 54px;
  height: 54px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.05);
  border-radius: 10px;
}

.overview-card.highlight .overview-icon {
  background: rgba(255, 255, 255, 0.2);
}

.overview-value {
  font-size: 1.55rem;
  font-weight: bold;
  color: #2c3e50;
}

.overview-card.highlight .overview-value {
  color: #fff;
}

.overview-label {
  font-size: 0.85rem;
  color: #7f8c8d;
  margin-top: 0.2rem;
}

.overview-card.highlight .overview-label {
  color: rgba(255, 255, 255, 0.9);
}

/* 概览卡片 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 1.1rem 1.2rem;
  display: flex;
  align-items: center;
  gap: 0.9rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.12);
}

.stat-icon {
  font-size: 1.8rem;
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8f9fa;
  border-radius: 8px;
}

.stat-value {
  font-size: 1.5rem;
  font-weight: bold;
  color: #2c3e50;
}

.stat-label {
  font-size: 0.85rem;
  color: #7f8c8d;
  margin-top: 0.15rem;
}

/* 面板 */
.panel {
  background: #fff;
  border-radius: 8px;
  padding: 1.2rem 1.4rem;
  margin-bottom: 1.5rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.panel-header h2 {
  font-size: 1.15rem;
  color: #2c3e50;
  margin: 0;
}

.panel-tip {
  font-size: 0.85rem;
  color: #95a5a6;
}

.btn-link {
  background: none;
  border: none;
  color: #3498db;
  cursor: pointer;
  font-size: 0.9rem;
  padding: 0;
}

.btn-link:hover {
  text-decoration: underline;
}

/* 待处理事项 */
.pending-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 0.9rem;
}

.pending-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.9rem 1.1rem;
  border: 1px solid #e9ecef;
  border-radius: 8px;
  background: #fafbfc;
  cursor: pointer;
  transition: all 0.2s;
}

.pending-item:hover {
  border-color: #42b883;
  background: #fff;
}

.pending-item.urgent {
  border-color: #f39c12;
  background: #fff8e1;
}

.pending-label {
  color: #2c3e50;
  font-size: 0.95rem;
}

.pending-count {
  font-size: 1.3rem;
  font-weight: bold;
  color: #2c3e50;
}

.pending-item.urgent .pending-count {
  color: #e67e22;
}

/* 图表 */
.charts-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.chart-card {
  background: #fff;
  border-radius: 8px;
  padding: 1.1rem 1.2rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.chart-card h3 {
  font-size: 1rem;
  color: #2c3e50;
  margin: 0 0 0.5rem;
}

.chart-box {
  width: 100%;
  height: 280px;
}

/* 表格 */
.table-wrap {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

.data-table th,
.data-table td {
  padding: 0.6rem 0.8rem;
  text-align: left;
  border-bottom: 1px solid #eee;
}

.data-table th {
  background: #f8f9fa;
  color: #2c3e50;
  font-weight: 600;
}

.stock-tag {
  display: inline-block;
  background: #fdecea;
  color: #e74c3c;
  padding: 0.15rem 0.5rem;
  border-radius: 4px;
  font-weight: bold;
}

.empty-tip {
  text-align: center;
  color: #95a5a6;
  padding: 1.5rem;
}

@media (max-width: 900px) {
  .charts-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .dashboard-content {
    padding: 1rem;
  }

  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
