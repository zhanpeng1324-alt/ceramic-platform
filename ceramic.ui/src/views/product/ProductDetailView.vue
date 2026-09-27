<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/services/api'
import { recommendationService } from '@/services/recommendationService'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { productGallery } from '@/utils/images'
import type { Product, Review, ProductDetailResponse, SeckillActivity } from '@/types'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()
const userStore = useUserStore()

const product = ref<Product | null>(null)
const salesCount = ref(0)
const reviews = ref<Review[]>([])
const averageRating = ref(0)
const reviewCount = ref(0)
const canReview = ref(false)
const loading = ref(true)
const error = ref('')
const quantity = ref(1)
const currentImageIndex = ref(0)
const showReviewForm = ref(false)

const newReview = ref({
  title: '',
  content: '',
  rating: 5
})

const selectQuantity = (qty: number) => {
  if (product.value && qty > 0 && qty <= product.value.stock) {
    quantity.value = qty
  }
}

const addToCart = async () => {
  if (!userStore.isLoggedIn) {
    alert('请先登录')
    router.push('/login')
    return
  }
  if (!product.value) return
  if (product.value.stock <= 0) {
    alert('该商品已售罄')
    return
  }
  loading.value = true
  try {
    const success = await cartStore.addToCart(product.value, quantity.value)
    if (success) {
      alert(`已添加 ${quantity.value} 件 ${product.value.name} 到购物车`)
    } else {
      alert('添加购物车失败，请重试')
    }
  } catch {
    alert('添加购物车失败，请重试')
  } finally {
    loading.value = false
  }
}

const buyNow = async () => {
  if (!userStore.isLoggedIn) {
    alert('请先登录')
    router.push('/login')
    return
  }
  if (!product.value) return
  if (product.value.stock <= 0) {
    alert('该商品已售罄')
    return
  }
  loading.value = true
  try {
    const success = await cartStore.addToCart(product.value, quantity.value)
    if (success) {
      router.push('/cart')
    } else {
      alert('添加购物车失败，请重试')
    }
  } catch {
    alert('添加购物车失败，请重试')
  } finally {
    loading.value = false
  }
}

// ===== 秒杀抢购：该商品有进行中活动时展示秒杀入口 =====
const seckillAct = ref<SeckillActivity | null>(null)
const seckillBuying = ref(false)
const seckillCountdown = ref('')
let seckillTimer: number | undefined

function seckillState(a: SeckillActivity): 'PENDING' | 'ACTIVE' {
  const start = new Date(a.startTime.replace(' ', 'T')).getTime()
  return Date.now() < start ? 'PENDING' : 'ACTIVE'
}

/** 活动进行中：详情页作为独立抢购页，仅保留秒杀抢购入口，不提供常规加购/购买 */
const seckillActive = computed(() => !!seckillAct.value && seckillState(seckillAct.value) === 'ACTIVE')

function updateSeckillCountdown() {
  const a = seckillAct.value
  if (!a) return
  const target = new Date((seckillState(a) === 'PENDING' ? a.startTime : a.endTime).replace(' ', 'T')).getTime()
  let diff = Math.max(0, Math.floor((target - Date.now()) / 1000))
  const h = Math.floor(diff / 3600)
  diff %= 3600
  const m = Math.floor(diff / 60)
  const s = diff % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  seckillCountdown.value = `${pad(h)}:${pad(m)}:${pad(s)}`
}

async function loadSeckill() {
  try {
    const pid = Number(route.params.id)
    if (!pid) return
    const list = await api.seckillActivities()
    const hit = list.find((a) =>
      a.productId === pid && a.status !== 'CANCELLED'
      && Date.now() < new Date(a.endTime.replace(' ', 'T')).getTime(),
    )
    if (hit) {
      seckillAct.value = hit
      seckillTimer = window.setInterval(updateSeckillCountdown, 1000)
      updateSeckillCountdown()
    }
  } catch {
    /* 秒杀信息加载失败不影响详情页 */
  }
}

/** 抢购：Redis Lua 原子扣减 → MQ 异步落库 → 轮询确认后跳秒杀专区支付 */
const seckillBuy = async () => {
  if (!userStore.isLoggedIn) {
    alert('请先登录')
    router.push('/login')
    return
  }
  if (seckillBuying.value || !seckillAct.value) return
  seckillBuying.value = true
  try {
    const { orderNo } = await api.seckillBuy(seckillAct.value.id)
    let ok = false
    for (let i = 0; i < 10; i++) {
      try {
        await api.seckillOrderDetail(orderNo)
        ok = true
        break
      } catch {
        await new Promise((r) => setTimeout(r, 500))
      }
    }
    if (ok) {
      alert('抢购成功！请在秒杀专区的「我的秒杀单」中完成支付（30 分钟内），支付后自动生成正式订单')
      router.push('/seckill')
    } else {
      alert('订单确认中，请稍后到秒杀专区「我的秒杀单」查看并支付')
      router.push('/seckill')
    }
  } catch (e) {
    alert((e as Error).message)
  } finally {
    seckillBuying.value = false
  }
}

onMounted(loadSeckill)
onBeforeUnmount(() => { if (seckillTimer) clearInterval(seckillTimer) })

const submitReview = async () => {
  if (!userStore.isLoggedIn) {
    alert('请先登录')
    router.push('/login')
    return
  }
  if (!newReview.value.title) {
    alert('请输入评价标题')
    return
  }
  if (!newReview.value.content) {
    alert('请输入评价内容')
    return
  }
  if (!product.value) return

  try {
    await api.createReview({
      productId: product.value.id,
      rating: newReview.value.rating,
      title: newReview.value.title,
      content: newReview.value.content,
      status: 'VISIBLE'
    })
    alert('评价成功')
    showReviewForm.value = false
    newReview.value = { title: '', content: '', rating: 5 }
    await loadProduct()
  } catch {
    alert('提交评价失败，请重试')
  }
}

const formatCurrency = (amount: number): string => {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY'
  }).format(amount)
}

const changeImage = (index: number) => {
  currentImageIndex.value = index
}

// 商品图册：优先图册 images，回退封面/静态图
const gallery = computed(() => (product.value ? productGallery(product.value) : []))
const currentImage = computed(() => gallery.value[currentImageIndex.value] || gallery.value[0] || '')

// 库存状态：售罄 / 紧张 / 充足
const stockState = computed<'out' | 'low' | 'ok'>(() => {
  const s = product.value?.stock ?? 0
  if (s <= 0) return 'out'
  if (s <= 5) return 'low'
  return 'ok'
})

const categoryLabel = computed(() => {
  const id = product.value?.categoryId
  return id === 1 ? '茶器' : id === 2 ? '餐器' : id === 3 ? '摆件' : '企业定制'
})

const goCustomize = () => {
  if (!product.value) return
  router.push({ path: '/customize', query: { productId: product.value.id } })
}

const incrementQuantity = () => {
  if (product.value && quantity.value < product.value.stock) {
    quantity.value++
  }
}

const decrementQuantity = () => {
  if (quantity.value > 1) {
    quantity.value--
  }
}

const totalPrice = computed(() => {
  return product.value ? product.value.price * quantity.value : 0
})

const loadProduct = async () => {
  const id = parseInt(route.params.id as string)
  if (isNaN(id)) {
    error.value = '无效的商品ID'
    loading.value = false
    return
  }
  try {
    const response = await api.product(id)
    product.value = response.product
    salesCount.value = response.salesCount || 0
    reviews.value = response.reviews || []
    averageRating.value = response.averageRating || 0
    reviewCount.value = response.reviewCount || 0
    canReview.value = response.canReview || false
    quantity.value = 1
    currentImageIndex.value = 0
  } catch (err) {
    error.value = err instanceof Error ? err.message : '商品不存在'
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadProduct()
  if (product.value && userStore.isLoggedIn) {
    recommendationService.recordBehavior(product.value.id, 'view')
  }
  // 从「我的订单 · 待评价」点「去评价」跳转过来：自动展开评价表单并滚动到评价区
  if (route.query.review === '1' && canReview.value) {
    showReviewForm.value = true
    await nextTick()
    document.getElementById('reviews')?.scrollIntoView({ behavior: 'smooth' })
  }
})
</script>

<template>
  <div class="product-detail-page">
    <div class="container">
      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else-if="product" class="product-detail">
        <nav class="breadcrumb">
          <router-link to="/">首页</router-link>
          <span class="sep">/</span>
          <router-link to="/products">商品</router-link>
          <span class="sep">/</span>
          <span class="current">{{ product.name }}</span>
        </nav>
        <div class="product-images">
          <div class="main-image">
            <img :src="currentImage" :alt="product.name" />
            <span v-if="stockState === 'out'" class="img-badge out">已售罄</span>
          </div>

          <div v-if="gallery.length > 1" class="thumbnail-images">
            <img
              v-for="(img, idx) in gallery"
              :key="img + idx"
              :src="img"
              :alt="`${product.name} ${idx + 1}`"
              :class="{ active: currentImageIndex === idx }"
              @click="changeImage(idx)"
            />
          </div>
        </div>

        <div class="product-info">
          <h1>{{ product.name }}</h1>
          <p v-if="product.subtitle" class="product-subtitle">{{ product.subtitle }}</p>

          <div class="product-price">
            <template v-if="seckillAct">
              <span class="seckill-price">¥{{ seckillAct.seckillPrice }}</span>
              <span class="seckill-origin">{{ formatCurrency(product.price) }}</span>
              <span class="seckill-info">
                ⚡限时秒杀 · {{ seckillState(seckillAct) === 'PENDING' ? '距开始' : '距结束' }}
                <b>{{ seckillCountdown }}</b> · 仅剩 {{ seckillAct.availableStock }} 件
              </span>
            </template>
            <template v-else>
              <span class="current-price">{{ formatCurrency(product.price) }}</span>
              <span v-if="product.customizable" class="customizable-tag">支持定制</span>
            </template>
          </div>
          
          <div class="product-meta">
            <div class="meta-item">
              <span class="label">评分:</span>
              <span class="value rating">
                <span class="stars-inline">
                  <span v-for="i in 5" :key="i" class="star" :class="{ filled: i <= Math.round(averageRating) }">★</span>
                </span>
                {{ averageRating || '暂无' }}
              </span>
            </div>
            <div class="meta-item">
              <span class="label">已售:</span>
              <span class="value">{{ salesCount }} 件</span>
            </div>
            <div class="meta-item">
              <span class="label">库存:</span>
              <span class="value" :class="{ 'stock-low': stockState === 'low', 'stock-out': stockState === 'out' }">
                {{ stockState === 'out' ? '已售罄' : stockState === 'low' ? `仅剩 ${product.stock} 件` : `${product.stock} 件` }}
              </span>
            </div>
            <div class="meta-item">
              <span class="label">分类:</span>
              <span class="value">{{ categoryLabel }}</span>
            </div>
          </div>
          
          <div class="product-description">
            <h3>产品介绍</h3>
            <p>{{ product.description }}</p>
          </div>
          
          <div v-if="product.glazeColor || product.material || product.size" class="product-specifications">
            <h3>规格参数</h3>
            <table>
              <tr v-if="product.glazeColor">
                <td class="spec-key">釉色</td>
                <td class="spec-value">{{ product.glazeColor }}</td>
              </tr>
              <tr v-if="product.material">
                <td class="spec-key">材质</td>
                <td class="spec-value">{{ product.material }}</td>
              </tr>
              <tr v-if="product.size">
                <td class="spec-key">尺寸</td>
                <td class="spec-value">{{ product.size }}</td>
              </tr>
            </table>
          </div>
          
          <div class="product-actions">
            <div v-if="!seckillActive" class="quantity-selector">
              <label>数量:</label>
              <div class="quantity-controls">
                <button @click="decrementQuantity" :disabled="quantity <= 1 || stockState === 'out'">-</button>
                <input
                  type="number"
                  v-model.number="quantity"
                  min="1"
                  :max="product.stock"
                  :disabled="stockState === 'out'"
                  @change="selectQuantity(quantity)"
                />
                <button @click="incrementQuantity" :disabled="quantity >= product.stock || stockState === 'out'">+</button>
              </div>
              <span class="stock-info">剩余 {{ product.stock }} 件</span>
            </div>
            <p v-else class="seckill-limit">⚡ 秒杀商品每人限购 1 件，抢购后 30 分钟内完成支付</p>

            <div class="action-buttons">
              <template v-if="seckillActive">
                <button
                  class="btn btn-seckill"
                  :disabled="seckillBuying || seckillAct!.availableStock <= 0"
                  @click="seckillBuy"
                >
                  {{ seckillAct!.availableStock <= 0 ? '已抢光' : (seckillBuying ? '抢购中…' : '⚡立即抢购') }}
                </button>
              </template>
              <template v-else>
                <button @click="addToCart" class="btn btn-secondary" :disabled="stockState === 'out'">
                  加入购物车
                </button>
                <button @click="buyNow" class="btn btn-primary" :disabled="stockState === 'out'">
                  {{ stockState === 'out' ? '已售罄' : '立即购买' }}
                </button>
              </template>
            </div>

            <button v-if="product.customizable" @click="goCustomize" class="btn btn-customize">
              我想定制这款商品 →
            </button>

            <div v-if="!seckillActive" class="total-price">
              总计: <span class="price">{{ formatCurrency(totalPrice) }}</span>
            </div>
          </div>
        </div>
      </div>
      
      <div v-if="product" id="reviews" class="product-reviews">
        <div class="reviews-header">
          <h2 class="ui-section-title">用户评价</h2>
          <div class="reviews-summary">
            <div class="rating-display">
              <span class="rating-value">{{ averageRating }}</span>
              <div class="stars">
                <span v-for="i in 5" :key="i" class="star" :class="{ filled: i <= Math.round(averageRating) }">★</span>
              </div>
            </div>
            <span class="review-count">共 {{ reviewCount }} 条评价</span>
            <template v-if="canReview">
              <button @click="showReviewForm = !showReviewForm" class="btn btn-primary btn-sm">
                {{ showReviewForm ? '收起评价' : '写评价' }}
              </button>
            </template>
            <span v-else-if="userStore.isLoggedIn" class="review-tip">购买后才能评价</span>
            <span v-else class="review-tip">登录后查看评价</span>
          </div>
        </div>
        
        <div v-if="showReviewForm" class="review-form">
          <h3>发表评价</h3>
          <div class="form-group">
            <label>评分</label>
            <div class="star-rating">
              <span v-for="i in 5" :key="i" class="star" :class="{ filled: i <= newReview.rating }" @click="newReview.rating = i">★</span>
            </div>
          </div>
          <div class="form-group">
            <label>评价标题</label>
            <input v-model="newReview.title" type="text" placeholder="请输入评价标题" class="form-input" />
          </div>
          <div class="form-group">
            <label>评价内容</label>
            <textarea v-model="newReview.content" rows="4" placeholder="请详细描述您对商品的评价..." class="form-textarea"></textarea>
          </div>
          <button @click="submitReview" class="btn btn-primary">提交评价</button>
        </div>
        
        <div v-if="reviews.length > 0" class="reviews-list">
          <div v-for="review in reviews" :key="review.id" class="review-item">
            <div class="review-header">
              <div class="reviewer-info">
                <span class="reviewer-name">{{ review.username || '匿名用户' }}</span>
                <span class="review-date">{{ review.createdAt ? new Date(review.createdAt).toLocaleDateString() : '' }}</span>
              </div>
              <div class="review-rating">
                <span v-for="i in 5" :key="i" class="star" :class="{ filled: i <= review.rating }">★</span>
              </div>
            </div>
            <div class="review-title">{{ review.title }}</div>
            <div class="review-content">{{ review.content }}</div>
            <div v-if="review.reply" class="review-reply">
              <span class="reply-label">商家回复</span>
              <span class="reply-text">{{ review.reply }}</span>
            </div>
          </div>
        </div>
        
        <div v-else class="no-reviews">
          <p>暂无评价，快来发表第一条评价吧！</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.product-detail-page {
  padding: var(--sp-6) 0;
  min-height: calc(100vh - 120px);
  background: var(--c-bg);
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 var(--sp-4);
}

.product-detail {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-6);
  margin-bottom: var(--sp-6);
  align-items: start;
}

.breadcrumb {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: 0.9rem;
  color: var(--c-text-muted);
  margin-bottom: calc(var(--sp-2) * -1);
}

.breadcrumb a {
  color: var(--c-text-muted);
  text-decoration: none;
}

.breadcrumb a:hover {
  color: var(--c-primary);
}

.breadcrumb .sep {
  color: var(--c-border);
}

.breadcrumb .current {
  color: var(--c-text);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 260px;
}

.main-image {
  position: relative;
}

.img-badge {
  position: absolute;
  top: var(--sp-3);
  left: var(--sp-3);
  padding: 4px 12px;
  border-radius: var(--radius-sm);
  font-size: 0.85rem;
  font-weight: 700;
  color: #fff;
}

.img-badge.out {
  background: rgba(107, 114, 128, 0.9);
}

.product-subtitle {
  margin: calc(var(--sp-3) * -1) 0 var(--sp-4);
  color: var(--c-text-muted);
  font-size: 1rem;
  line-height: 1.5;
}

.customizable-tag {
  align-self: center;
  padding: 3px 10px;
  border-radius: var(--radius-sm);
  background: var(--c-primary-soft);
  color: var(--c-primary-dark);
  font-size: 0.8rem;
  font-weight: 700;
}

.stars-inline {
  display: inline-flex;
  gap: 1px;
  margin-right: 4px;
}

.stars-inline .star {
  font-size: 0.85rem;
  color: var(--c-border);
}

.stars-inline .star.filled {
  color: var(--c-accent);
}

.meta-item .value.rating {
  display: inline-flex;
  align-items: center;
}

.value.stock-low {
  color: var(--c-accent);
}

.value.stock-out {
  color: var(--c-text-muted);
}

.btn-customize {
  width: 100%;
  margin-bottom: var(--sp-4);
  background: transparent;
  color: var(--c-primary-dark);
  border: 1px solid var(--c-primary);
}

.btn-customize:hover {
  background: var(--c-primary-soft);
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.product-images {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  padding: var(--sp-5);
  box-shadow: var(--shadow);
}

.main-image img {
  width: 100%;
  height: 420px;
  object-fit: contain;
  border-radius: var(--radius);
  background: var(--c-primary-soft);
}

.thumbnail-images {
  display: flex;
  gap: var(--sp-2);
  margin-top: var(--sp-4);
}

.thumbnail-images img {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: var(--radius-sm);
  cursor: pointer;
  border: 2px solid var(--c-border);
  transition: border-color 0.2s;
}

.thumbnail-images img.active {
  border-color: var(--c-primary);
}

.product-info {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  padding: var(--sp-5);
  box-shadow: var(--shadow);
}

.product-info h1 {
  margin: 0 0 var(--sp-4) 0;
  color: var(--c-text);
  font-size: 1.7rem;
  line-height: 1.3;
}

.product-price {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.5rem;
}

.seckill-price {
  color: #e63946;
  font-size: 1.8rem;
  font-weight: 800;
}

.seckill-origin {
  color: #b0b6bd;
  text-decoration: line-through;
  font-size: 1rem;
}

.seckill-info {
  width: 100%;
  font-size: 0.85rem;
  color: #e63946;
  background: #fdeeec;
  border-radius: 8px;
  padding: 0.35rem 0.7rem;
}

.seckill-info b {
  font-variant-numeric: tabular-nums;
}

.seckill-limit {
  margin: 0 0 0.75rem;
  font-size: 0.85rem;
  color: #e63946;
  background: #fdeeec;
  border-radius: 8px;
  padding: 0.45rem 0.7rem;
}

.btn-seckill {
  background: linear-gradient(90deg, #ff7b54, #e63946);
  color: #fff;
  border: none;
}

.btn-seckill:hover:not(:disabled) {
  filter: brightness(1.05);
}

.current-price {
  font-size: 2.25rem;
  font-weight: 800;
  color: var(--c-accent);
  letter-spacing: -0.5px;
}

.product-meta {
  display: flex;
  gap: var(--sp-6);
  margin-bottom: var(--sp-5);
  padding-bottom: var(--sp-4);
  border-bottom: 1px solid var(--c-border);
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
}

.meta-item .label {
  font-size: 0.8rem;
  color: var(--c-text-muted);
}

.meta-item .value {
  font-weight: 700;
  color: var(--c-text);
}

.product-description h3,
.product-specifications h3 {
  margin: var(--sp-5) 0 var(--sp-3) 0;
  color: var(--c-text);
  font-size: 1.05rem;
}

.product-description p {
  color: var(--c-text-muted);
  line-height: 1.7;
  margin: 0;
}

.product-specifications table {
  width: 100%;
  border-collapse: collapse;
}

.product-specifications tr {
  border-bottom: 1px solid var(--c-border);
}

.product-specifications td {
  padding: var(--sp-2) 0;
}

.spec-key {
  width: 30%;
  color: var(--c-text-muted);
  font-weight: 700;
}

.spec-value {
  color: var(--c-text);
}

.product-actions {
  margin-top: var(--sp-5);
  padding-top: var(--sp-4);
  border-top: 1px solid var(--c-border);
}

.quantity-selector {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  margin-bottom: var(--sp-4);
  flex-wrap: wrap;
}

.quantity-selector label {
  font-weight: 700;
  color: var(--c-text);
}

.quantity-controls {
  display: flex;
  align-items: center;
  border: 1px solid var(--c-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.quantity-controls button {
  width: 40px;
  height: 40px;
  border: none;
  background: var(--c-primary-soft);
  color: var(--c-primary-dark);
  cursor: pointer;
  font-size: 1.2rem;
  transition: background-color 0.2s;
}

.quantity-controls button:hover:not(:disabled) {
  background: var(--c-primary);
  color: #fff;
}

.quantity-controls button:disabled {
  background: var(--c-bg);
  cursor: not-allowed;
  color: #ccc;
}

.quantity-controls input {
  width: 60px;
  height: 40px;
  border: none;
  text-align: center;
  border-left: 1px solid var(--c-border);
  border-right: 1px solid var(--c-border);
  color: var(--c-text);
}

.stock-info {
  color: var(--c-text-muted);
  font-size: 0.9rem;
}

.action-buttons {
  display: flex;
  gap: var(--sp-4);
  margin-bottom: var(--sp-4);
}

.total-price {
  font-size: 1.2rem;
  font-weight: 700;
  color: var(--c-text);
}

.total-price .price {
  font-size: 1.5rem;
  color: var(--c-accent);
}

.btn {
  padding: var(--sp-3) var(--sp-5);
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 1rem;
  font-weight: 600;
  transition: background-color 0.2s, transform 0.05s;
}

.btn:active {
  transform: translateY(1px);
}

.btn-sm {
  padding: var(--sp-2) var(--sp-4);
  font-size: 0.9rem;
}

.btn-secondary {
  background: var(--c-primary-soft);
  color: var(--c-primary-dark);
  flex: 1;
}

.btn-secondary:hover {
  background: var(--c-border);
}

.btn-primary {
  background: var(--c-primary);
  color: white;
  flex: 1;
}

.btn-primary:hover {
  background: var(--c-primary-dark);
}

.loading, .empty, .no-reviews {
  text-align: center;
  padding: var(--sp-6);
  color: var(--c-text-muted);
}

.error {
  text-align: center;
  padding: var(--sp-6);
  color: var(--c-accent);
}

.product-reviews {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  padding: var(--sp-5);
  box-shadow: var(--shadow);
}

.reviews-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--sp-5);
  padding-bottom: var(--sp-4);
  border-bottom: 1px solid var(--c-border);
}

.reviews-header h2 {
  margin: 0;
  color: var(--c-text);
}

.reviews-summary {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
}

.rating-display {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.rating-value {
  font-size: 1.5rem;
  font-weight: 800;
  color: var(--c-accent);
}

.stars {
  display: flex;
  gap: 0.1rem;
}

.stars .star {
  font-size: 1rem;
  color: var(--c-border);
}

.stars .star.filled {
  color: var(--c-accent);
}

.review-count {
  color: var(--c-text-muted);
  font-size: 0.9rem;
}

.review-tip {
  color: var(--c-text-muted);
  font-size: 0.9rem;
}

.review-form {
  background: var(--c-primary-soft);
  padding: var(--sp-4);
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  margin-bottom: var(--sp-5);
}

.review-form h3 {
  margin: 0 0 var(--sp-4) 0;
  color: var(--c-text);
}

.form-group {
  margin-bottom: var(--sp-4);
}

.form-group label {
  display: block;
  margin-bottom: var(--sp-2);
  font-weight: 700;
  color: var(--c-text);
}

.form-input, .form-textarea {
  width: 100%;
  padding: var(--sp-3);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-sm);
  font-size: 1rem;
  background: var(--c-surface);
  color: var(--c-text);
}

.form-input:focus, .form-textarea:focus {
  outline: none;
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px var(--c-primary-soft);
}

.form-textarea {
  resize: vertical;
}

.star-rating .star {
  font-size: 1.5rem;
  color: var(--c-border);
  cursor: pointer;
  transition: color 0.2s;
}

.star-rating .star.filled {
  color: var(--c-accent);
}

.reviews-list {
  margin-top: var(--sp-4);
}

.review-item {
  padding: var(--sp-4);
  border-bottom: 1px solid var(--c-border);
}

.review-item:last-child {
  border-bottom: none;
}

.review-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--sp-2);
}

.reviewer-info {
  display: flex;
  gap: var(--sp-4);
}

.reviewer-name {
  font-weight: 700;
  color: var(--c-text);
}

.review-date {
  color: var(--c-text-muted);
  font-size: 0.9rem;
}

.review-title {
  font-weight: 700;
  color: var(--c-text);
  margin-bottom: var(--sp-2);
}

.review-content {
  color: var(--c-text-muted);
  line-height: 1.7;
}

.review-reply {
  margin-top: var(--sp-3);
  padding: var(--sp-3);
  background: var(--c-primary-soft);
  border-left: 3px solid var(--c-primary);
  border-radius: var(--radius-sm);
  line-height: 1.7;
}

.review-reply .reply-label {
  color: var(--c-primary-dark);
  font-weight: 700;
  margin-right: 6px;
}

.review-reply .reply-text {
  color: var(--c-text);
}

@media (max-width: 768px) {
  .product-detail {
    grid-template-columns: 1fr;
  }

  .main-image img {
    height: 300px;
  }

  .product-meta {
    flex-direction: column;
    gap: var(--sp-4);
  }

  .action-buttons {
    flex-direction: column;
  }

  .quantity-selector {
    flex-direction: column;
    align-items: flex-start;
  }

  .reviews-header {
    flex-direction: column;
    gap: var(--sp-4);
    align-items: flex-start;
  }

  .reviews-summary {
    flex-wrap: wrap;
  }
}
</style>