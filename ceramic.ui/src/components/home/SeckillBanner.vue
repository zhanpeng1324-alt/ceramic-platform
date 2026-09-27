<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/services/api'
import type { SeckillActivity } from '@/types'

const router = useRouter()
const now = ref(Date.now())
const items = ref<SeckillActivity[]>([])
let timer: number | undefined

function parseTime(s: string): number {
  return new Date(s.includes('T') ? s : s.replace(' ', 'T')).getTime()
}

/** 仅展示进行中 / 即将开始（24h 内开始）的活动，最多 3 个 */
const banners = computed(() =>
  items.value
    .filter((a) => {
      if (a.status === 'CANCELLED') return false
      const st = parseTime(a.startTime)
      const en = parseTime(a.endTime)
      return now.value < en && now.value >= st - 24 * 3600 * 1000
    })
    .slice(0, 3),
)

function statusOf(a: SeckillActivity): 'PENDING' | 'ACTIVE' {
  return now.value < parseTime(a.startTime) ? 'PENDING' : 'ACTIVE'
}

function countdownText(a: SeckillActivity): string {
  const target = statusOf(a) === 'PENDING' ? parseTime(a.startTime) : parseTime(a.endTime)
  let diff = Math.max(0, Math.floor((target - now.value) / 1000))
  const h = Math.floor(diff / 3600)
  diff %= 3600
  const m = Math.floor(diff / 60)
  const s = diff % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(h)}:${pad(m)}:${pad(s)}`
}

function soldOf(a: SeckillActivity): number {
  return Math.max(0, a.totalStock - a.availableStock)
}

function percentOf(a: SeckillActivity): number {
  return a.totalStock > 0 ? Math.min(100, Math.round((soldOf(a) / a.totalStock) * 100)) : 0
}

onMounted(async () => {
  timer = window.setInterval(() => { now.value = Date.now() }, 1000)
  try {
    items.value = await api.seckillActivities()
  } catch {
    /* 秒杀区块失败不影响首页 */
  }
})

onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

<template>
  <section v-if="banners.length" class="section seckill-section">
    <div class="seckill-head">
      <h2 class="ui-section-title">限时秒杀</h2>
      <button class="seckill-more" @click="router.push('/seckill')">进入秒杀专区 →</button>
    </div>
    <div class="seckill-grid">
      <div
        v-for="a in banners"
        :key="a.id"
        class="seckill-card"
        @click="router.push('/seckill')"
      >
        <div class="seckill-img">
          <img :src="a.productImage || `/images/products/${a.productId}.jpg`" :alt="a.productName" />
          <span :class="['seckill-tag', statusOf(a).toLowerCase()]">
            {{ statusOf(a) === 'PENDING' ? '即将开始' : '抢购中' }}
          </span>
        </div>
        <div class="seckill-body">
          <p class="seckill-name">{{ a.productName }}</p>
          <div class="seckill-price-row">
            <span class="seckill-price">¥{{ a.seckillPrice }}</span>
            <span class="seckill-origin">¥{{ a.originalPrice }}</span>
          </div>
          <div class="seckill-progress">
            <div class="seckill-bar"><i :style="{ width: percentOf(a) + '%' }"></i></div>
            <span>已抢 {{ soldOf(a) }}/{{ a.totalStock }}</span>
          </div>
          <p class="seckill-countdown">
            {{ statusOf(a) === 'PENDING' ? '距开始' : '距结束' }}
            <b>{{ countdownText(a) }}</b>
          </p>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.seckill-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.seckill-more {
  border: none;
  background: none;
  color: #e63946;
  cursor: pointer;
  font-size: 0.9rem;
}

.seckill-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 1rem;
  margin-top: 1rem;
}

.seckill-card {
  display: flex;
  gap: 0.75rem;
  background: #fff;
  border-radius: 12px;
  padding: 0.75rem;
  cursor: pointer;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
  transition: transform 0.2s, box-shadow 0.2s;
}

.seckill-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.1);
}

.seckill-img {
  position: relative;
  width: 96px;
  height: 96px;
  border-radius: 8px;
  overflow: hidden;
  flex-shrink: 0;
  background: #f5f6f7;
}

.seckill-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.seckill-tag {
  position: absolute;
  top: 4px;
  left: 4px;
  padding: 0.1rem 0.4rem;
  border-radius: 999px;
  font-size: 0.65rem;
  color: #fff;
}

.seckill-tag.active { background: #e63946; }
.seckill-tag.pending { background: #e8a33d; }

.seckill-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0.3rem;
}

.seckill-name {
  margin: 0;
  font-weight: 600;
  font-size: 0.9rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.seckill-price-row {
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
}

.seckill-price {
  color: #e63946;
  font-weight: 700;
  font-size: 1.15rem;
}

.seckill-origin {
  color: #b0b6bd;
  font-size: 0.75rem;
  text-decoration: line-through;
}

.seckill-progress {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.7rem;
  color: #8a8f98;
}

.seckill-bar {
  flex: 1;
  height: 6px;
  border-radius: 999px;
  background: #fdeeec;
  overflow: hidden;
}

.seckill-bar i {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #ff7b54, #e63946);
}

.seckill-countdown {
  margin: 0;
  font-size: 0.75rem;
  color: #8a8f98;
}

.seckill-countdown b {
  color: #e63946;
  font-variant-numeric: tabular-nums;
}
</style>
