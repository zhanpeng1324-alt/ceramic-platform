<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { api } from '@/services/api'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import type { Product } from '@/types'

const router = useRouter()
const route = useRoute()
const cartStore = useCartStore()
const userStore = useUserStore()
const products = ref<Product[]>([])
const loading = ref(true)
const error = ref('')
// 用字符串承载分类，便于与下拉框及 URL query 同步（'' = 全部分类）
const category = ref('')
const searchKeyword = ref('')
const priceMin = ref<number>()
const priceMax = ref<number>()
const sortBy = ref('')
const page = ref(1)
const pageSize = 20
const total = ref(0)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

const loadProducts = async () => {
  loading.value = true
  error.value = ''
  try {
    const res = await api.productsPage({
      categoryId: category.value ? parseInt(category.value) : undefined,
      keyword: searchKeyword.value || undefined,
      priceMin: priceMin.value,
      priceMax: priceMax.value,
      sort: sortBy.value || undefined,
      page: page.value,
      size: pageSize,
    })
    products.value = res.items
    total.value = res.total
  } catch (err) {
    error.value = err instanceof Error ? err.message : '后端服务暂时不可用'
  } finally {
    loading.value = false
  }
}

const goPage = (p: number) => {
  if (p < 1 || p > totalPages.value) return
  page.value = p
  loadProducts()
}

// 筛选条件变化时回到第一页
const resetAndLoad = () => {
  page.value = 1
  loadProducts()
}

const handleAddToCart = async (product: Product) => {
  if (!userStore.isLoggedIn) {
    alert('请先登录')
    router.push('/login')
    return
  }
  try {
    const success = await cartStore.addToCart(product, 1)
    if (success) {
      alert(`已添加 ${product.name} 到购物车`)
    } else {
      alert('添加购物车失败，请重试')
    }
  } catch {
    alert('添加购物车失败，请重试')
  }
}

const handleBuyNow = async (product: Product) => {
  if (!userStore.isLoggedIn) {
    alert('请先登录')
    router.push('/login')
    return
  }
  try {
    const success = await cartStore.addToCart(product, 1)
    if (success) {
      router.push('/checkout')
    } else {
      alert('添加购物车失败，请重试')
    }
  } catch {
    alert('添加购物车失败，请重试')
  }
}

const handleCustomize = (product: Product) => {
  router.push('/customize')
}

onMounted(() => {
  // 支持从首页分类卡片深链进入：/products?category=1
  const c = route.query.category
  if (typeof c === 'string' && c) {
    category.value = c
  }
  loadProducts()
})
</script>

<template>
  <div class="products-page">
    <div class="container">
      <h1>陶瓷产品</h1>
      <p class="page-description">精选优质陶瓷制品，传承千年工艺</p>
      
      <div class="filters">
        <select class="filter-select" v-model="category" @change="resetAndLoad">
          <option value="">全部分类</option>
          <option value="1">茶器</option>
          <option value="2">餐器</option>
          <option value="3">摆件</option>
          <option value="4">企业定制</option>
        </select>

        <select class="filter-select" v-model="sortBy" @change="resetAndLoad">
          <option value="">默认排序</option>
          <option value="sales">销量优先</option>
          <option value="price_asc">价格从低到高</option>
          <option value="price_desc">价格从高到低</option>
        </select>

        <div class="price-range">
          <input type="number" min="0" class="price-input" v-model.number="priceMin" placeholder="最低价" @keyup.enter="resetAndLoad" />
          <span class="price-sep">-</span>
          <input type="number" min="0" class="price-input" v-model.number="priceMax" placeholder="最高价" @keyup.enter="resetAndLoad" />
        </div>

        <input
          type="text"
          class="search-input"
          v-model="searchKeyword"
          placeholder="搜索商品..."
          @keyup.enter="resetAndLoad"
        />

        <button class="search-btn" @click="resetAndLoad">搜索</button>
      </div>
      
      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else class="products-grid">
        <div 
          v-for="product in products"
          :key="product.id"
          class="product-card"
          @click="router.push(`/products/${product.id}`)"
        >
          <div class="card-image">
            <img :src="product.imageUrl || `/images/products/${product.id}.jpg`" :alt="product.name" />
          </div>
          <div class="card-body">
            <h3>{{ product.name }}</h3>
            <p class="subtitle">{{ product.subtitle }}</p>
            <div class="card-footer">
              <div class="price-wrap">
                <span class="price">¥{{ product.price }}</span>
                <span class="sales-tag" v-if="product.sales != null">已售 {{ product.sales }}</span>
              </div>
              <div class="actions">
                <button 
                  @click.stop="handleAddToCart(product)" 
                  class="btn btn-small btn-secondary"
                >
                  加入购物车
                </button>
                <button 
                  @click.stop="handleBuyNow(product)" 
                  class="btn btn-small btn-primary"
                >
                  立即购买
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <div v-if="!loading && !error && products.length === 0" class="empty">
        暂无商品
      </div>

      <div v-if="!loading && !error && total > 0" class="pagination">
        <span class="page-info">共 {{ total }} 件 · 第 {{ page }}/{{ totalPages }} 页</span>
        <div class="page-controls">
          <button class="page-btn" :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
          <button class="page-btn" :disabled="page >= totalPages" @click="goPage(page + 1)">下一页</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: var(--sp-5);
}
.page-info {
  color: var(--c-text-secondary, #666);
  font-size: 14px;
}
.page-controls {
  display: flex;
  gap: var(--sp-2);
}
.page-btn {
  padding: 6px 14px;
  border: 1px solid var(--c-border, #ddd);
  background: #fff;
  border-radius: 6px;
  cursor: pointer;
}
.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.products-page {
  padding: var(--sp-6) 0;
  min-height: calc(100vh - 120px);
  background: var(--c-bg);
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 var(--sp-4);
}

h1 {
  text-align: left;
  margin-bottom: var(--sp-1);
  color: var(--c-text);
  font-size: 1.9rem;
}

.page-description {
  text-align: left;
  color: var(--c-text-muted);
  margin-bottom: var(--sp-5);
}

.filters {
  display: flex;
  gap: var(--sp-3);
  margin-bottom: var(--sp-6);
  justify-content: flex-start;
  align-items: center;
  flex-wrap: wrap;
  padding: var(--sp-3) var(--sp-4);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

.filter-select,
.search-input,
.price-input {
  padding: var(--sp-2) var(--sp-3);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-sm);
  font-size: 0.95rem;
  color: var(--c-text);
  background: var(--c-surface);
  transition: border-color 0.15s, box-shadow 0.15s;
}

.filter-select:focus,
.search-input:focus,
.price-input:focus {
  outline: none;
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px var(--c-primary-soft);
}

.search-input {
  min-width: 220px;
  flex: 1 1 220px;
  max-width: 320px;
}

.price-range {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.price-input {
  width: 90px;
}

.price-sep {
  color: var(--c-text-muted);
}

.search-btn {
  padding: var(--sp-2) var(--sp-5);
  border: none;
  border-radius: var(--radius-sm);
  background: var(--c-primary);
  color: #fff;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.15s;
}

.search-btn:hover {
  background: var(--c-primary-dark);
}

.products-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--sp-5);
}

.product-card {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.product-card:hover {
  transform: translateY(-4px);
  border-color: var(--c-gold);
  box-shadow: var(--shadow-lg);
}

.card-image {
  width: 100%;
  height: 200px;
  overflow: hidden;
  background: var(--c-primary-soft);
}

.card-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s;
}

.product-card:hover .card-image img {
  transform: scale(1.04);
}

.card-body {
  padding: var(--sp-4);
}

.card-body h3 {
  margin: 0 0 var(--sp-1) 0;
  color: var(--c-text);
  font-family: var(--font-serif);
  font-size: 1.1rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.subtitle {
  margin: 0 0 var(--sp-4) 0;
  color: var(--c-text-muted);
  font-size: 0.9rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--sp-3);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--c-border);
}

.price {
  font-size: 1.3rem;
  font-weight: 700;
  color: var(--c-accent);
  line-height: 1;
}

.price-wrap {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sales-tag {
  font-size: 0.78rem;
  color: var(--c-text-muted, #999);
  line-height: 1;
}

.actions {
  display: flex;
  gap: var(--sp-2);
}

.btn {
  padding: var(--sp-2) var(--sp-4);
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 0.9rem;
  transition: background-color 0.15s;
}

.btn-small {
  padding: var(--sp-1) var(--sp-3);
  font-size: 0.85rem;
}

.btn-primary {
  background: var(--c-primary);
  color: #fff;
}

.btn-primary:hover {
  background: var(--c-primary-dark);
}

.btn-secondary {
  background: var(--c-primary-soft);
  color: var(--c-primary-dark);
}

.btn-secondary:hover {
  background: var(--c-celadon);
}

.loading, .empty {
  text-align: center;
  padding: var(--sp-6);
  color: var(--c-text-muted);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

.error {
  text-align: center;
  padding: var(--sp-6);
  color: var(--c-accent-dark);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

@media (max-width: 768px) {
  .filters {
    flex-direction: column;
    align-items: stretch;
  }

  .search-input {
    width: 100%;
    max-width: none;
  }

  .actions {
    flex-direction: column;
    gap: var(--sp-1);
  }
}
</style>
