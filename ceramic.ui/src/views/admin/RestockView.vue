<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '@/services/api'
import type { RestockSuggestion } from '@/types'
import AdminNavbar from '@/components/admin/AdminNavbar.vue'

const list = ref<RestockSuggestion[]>([])
const loading = ref(true)
const filter = ref<'all' | 'OUT' | 'URGENT' | 'WARNING'>('all')

const levelLabel: Record<string, string> = { OUT: '缺货', URGENT: '紧急', WARNING: '偏低' }

const counts = computed(() => ({
  OUT: list.value.filter(i => i.level === 'OUT').length,
  URGENT: list.value.filter(i => i.level === 'URGENT').length,
  WARNING: list.value.filter(i => i.level === 'WARNING').length,
}))
const visible = computed(() => filter.value === 'all' ? list.value : list.value.filter(i => i.level === filter.value))

const load = async () => {
  loading.value = true
  try { list.value = await api.restockSuggestions() } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <div class="admin-page">
    <AdminNavbar />
    <main>
      <header>
        <div>
          <h1>备货建议</h1>
          <p>根据近 30 天真实销量，提示需要补货的在售商品</p>
        </div>
        <button @click="load">刷新</button>
      </header>

      <div class="tabs">
        <button :class="{ active: filter === 'all' }" @click="filter = 'all'">全部 {{ list.length }}</button>
        <button :class="{ active: filter === 'OUT' }" @click="filter = 'OUT'">缺货 {{ counts.OUT }}</button>
        <button :class="{ active: filter === 'URGENT' }" @click="filter = 'URGENT'">紧急 {{ counts.URGENT }}</button>
        <button :class="{ active: filter === 'WARNING' }" @click="filter = 'WARNING'">偏低 {{ counts.WARNING }}</button>
      </div>

      <p v-if="loading">加载中…</p>
      <div v-else-if="!list.length" class="ok-box">✅ 库存充足，暂无需要补货的商品</div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>商品</th>
              <th>当前库存</th>
              <th>近30天销量</th>
              <th>预计可售</th>
              <th>建议补货</th>
              <th>状态</th>
              <th>说明</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in visible" :key="item.productId">
              <td class="product-cell">
                <img v-if="item.imageUrl" :src="item.imageUrl" alt="" />
                <span>{{ item.name }}</span>
              </td>
              <td>{{ item.stock }}</td>
              <td>{{ item.sold30 }}</td>
              <td>{{ item.daysLeft === null ? '—' : item.daysLeft + ' 天' }}</td>
              <td><strong>{{ item.suggestedRestock > 0 ? '+' + item.suggestedRestock : '—' }}</strong></td>
              <td><span class="tag" :class="item.level.toLowerCase()">{{ levelLabel[item.level] }}</span></td>
              <td class="reason">{{ item.reason }}</td>
            </tr>
            <tr v-if="!visible.length"><td colspan="7" class="empty">该分类下暂无商品</td></tr>
          </tbody>
        </table>
      </div>
    </main>
  </div>
</template>

<style scoped>
main { max-width: 1240px; margin: 0 auto; padding: 28px }
.admin-page { min-height: 100vh; background: #f6f8f7 }
header { display: flex; justify-content: space-between; align-items: center }
h1 { margin: 0; color: #243b53 }
p { color: #718096 }
button { border: 0; border-radius: 6px; padding: 9px 14px; background: #3d9b78; color: #fff; cursor: pointer }
.tabs { display: flex; gap: 8px; flex-wrap: wrap; margin: 22px 0 }
.tabs button { background: #e7efeb; color: #426254 }
.tabs button.active { background: #3d9b78; color: #fff }
.ok-box { background: #fff; border-radius: 12px; padding: 40px; text-align: center; color: #52616b; box-shadow: 0 2px 10px #2233 }
.table-wrap { background: #fff; border-radius: 12px; overflow: auto; box-shadow: 0 2px 10px #2233 }
table { border-collapse: collapse; width: 100%; min-width: 850px }
th, td { padding: 14px 15px; text-align: left; border-bottom: 1px solid #edf1ef }
th { background: #f8fbf9; color: #52616b }
.product-cell { display: flex; align-items: center; gap: 10px }
.product-cell img { width: 40px; height: 40px; border-radius: 6px; object-fit: cover }
.reason { color: #718096; font-size: 13px }
.empty { text-align: center; color: #718096 }
.tag { padding: 3px 10px; border-radius: 12px; font-size: 12px; font-weight: bold }
.tag.out { background: #fde2e1; color: #c0392b }
.tag.urgent { background: #ffe8cc; color: #d35400 }
.tag.warning { background: #fff3cd; color: #8a6d3b }
</style>
