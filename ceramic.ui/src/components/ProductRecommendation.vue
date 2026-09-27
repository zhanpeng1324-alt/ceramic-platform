<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/services/api'
import { recommendationService } from '@/services/recommendationService'
import type { Product, Recommendation } from '@/types'

const props = defineProps<{
  userId?: number
  currentProductId?: number
  categoryId?: number
  recommendationType?: 'popular' | 'similar' | 'personalized' | 'trending' | 'category' | 'comprehensive'
  limit?: number
}>()

const router = useRouter()

const recommendedProducts = ref<Product[]>([])
const isLoading = ref(true)
const error = ref<string | null>(null)

/** 新品窗口：上架 21 天内算新品 */
const NEW_WINDOW_MS = 21 * 24 * 60 * 60 * 1000

const isNewProduct = (p: Product): boolean => {
  if (!p.createdAt) return false
  // 兼容 "yyyy-MM-dd HH:mm:ss" 格式（Safari 不识别空格分隔）
  const t = new Date(String(p.createdAt).replace(' ', 'T')).getTime()
  return !Number.isNaN(t) && Date.now() - t <= NEW_WINDOW_MS
}

const formatCurrency = (amount: number): string => {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY'
  }).format(amount)
}

const initRecommendations = async () => {
  isLoading.value = true
  error.value = null
  try {
    const limit = props.limit || 4

    // 新品上市：只展示 15 天内上架的商品，按上架时间倒序；没有则前端空状态显示"暂无上新"
    if (props.recommendationType === 'trending') {
      const allProducts = await api.products()
      recommendedProducts.value = allProducts
        .filter(isNewProduct)
        .sort((a, b) => String(b.createdAt ?? '').localeCompare(String(a.createdAt ?? '')))
        .slice(0, limit)
      isLoading.value = false
      return
    }

    let recommendations: Recommendation[] = []

    switch (props.recommendationType) {
      case 'similar':
        if (props.currentProductId) {
          recommendations = await recommendationService.getSimilarProducts(props.currentProductId, limit)
        }
        break
      case 'personalized':
      case 'popular':
      case 'category':
      case 'comprehensive':
      default:
        recommendations = await recommendationService.getRecommendations(limit)
        break
    }

    const allProducts = await api.products()
    const productIds = recommendations.map(r => r.productId)
    recommendedProducts.value = productIds
      .map(id => allProducts.find(p => p.id === id))
      .filter((p): p is Product => p !== undefined)

    // Recommendations are optional. Keep the storefront useful if that service is unavailable.
    // Each recommendation type falls back to a DISTINCT slice (rotated by section) so that the
    // several carousels rendered on one page are not all showing the same first N products.
    if (recommendedProducts.value.length === 0 && allProducts.length > 0) {
      const sectionOrder: Record<string, number> = {
        comprehensive: 0,
        popular: 1,
        personalized: 2,
        trending: 3,
        category: 4,
        similar: 5,
      }
      const section = sectionOrder[props.recommendationType ?? 'comprehensive'] ?? 0
      const offset = (section * limit) % allProducts.length
      const rotated = [...allProducts.slice(offset), ...allProducts.slice(0, offset)]
      recommendedProducts.value = rotated.slice(0, limit)
    }

    isLoading.value = false
  } catch (err) {
    error.value = '加载推荐商品失败'
    isLoading.value = false
    console.error('Recommendation error:', err)
  }
}

onMounted(() => {
  initRecommendations()
})
</script>

<template>
  <div class="product-recommendation">
    <div class="recommendation-header">
      <h3>
        <template v-if="recommendationType === 'popular'">热门推荐</template>
        <template v-else-if="recommendationType === 'similar'">相关推荐</template>
        <template v-else-if="recommendationType === 'personalized'">为你推荐</template>
        <template v-else-if="recommendationType === 'trending'">新品推荐</template>
        <template v-else-if="recommendationType === 'category'">同类推荐</template>
        <template v-else>精选推荐</template>
      </h3>
      <p>根据您的喜好为您推荐</p>
    </div>
    
    <div v-if="isLoading" class="loading">
      正在加载推荐商品...
    </div>
    
    <div v-else-if="error" class="error">
      <p>{{ error }}</p>
      <button class="retry-button" @click="initRecommendations">重新加载</button>
    </div>
    
    <div v-else-if="recommendedProducts.length === 0" class="no-products">
      {{ recommendationType === 'trending' ? '暂无上新' : '暂无推荐商品' }}
    </div>
    
    <div v-else class="recommendation-grid">
      <div 
        v-for="product in recommendedProducts" 
        :key="product.id"
        class="product-card"
        @click="router.push(`/products/${product.id}`)"
      >
        <div class="product-image">
          <img :src="product.imageUrl || `/images/products/${product.id}.jpg`" :alt="product.name" />
          <div class="product-badge new-badge" v-if="isNewProduct(product)">新</div>
          <div class="product-badge" v-else-if="product.customizable">
            可定制
          </div>
        </div>
        
        <div class="product-info">
          <h4 class="product-name">{{ product.name }}</h4>
          
          <div class="product-price">
            <span class="current-price">{{ formatCurrency(product.price) }}</span>
          </div>
          
          <div class="product-meta">
            <span class="stock">库存: {{ product.stock }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.product-recommendation {
  margin: 2rem 0;
  padding: 0 1rem;
}

.recommendation-header {
  text-align: center;
  margin-bottom: 1.5rem;
}

.recommendation-header h3 {
  color: #2c3e50;
  margin-bottom: 0.5rem;
}

.recommendation-header p {
  color: #666;
  font-size: 0.9rem;
}

.loading,
.error,
.no-products {
  text-align: center;
  padding: 2rem;
  color: #666;
}

.retry-button {
  margin-top: 0.75rem;
  border: 1px solid #667eea;
  border-radius: 6px;
  background: white;
  color: #667eea;
  cursor: pointer;
  padding: 0.5rem 1rem;
}

.recommendation-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 1.5rem;
}

.product-card {
  background: white;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  transition: transform 0.2s, box-shadow 0.2s;
  cursor: pointer;
}

.product-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 16px rgba(0,0,0,0.15);
}

.product-image {
  position: relative;
  height: 180px;
  overflow: hidden;
}

.product-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.product-badge {
  position: absolute;
  top: 0.5rem;
  left: 0.5rem;
  background: #e74c3c;
  color: white;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.7rem;
  font-weight: bold;
}

/* 新品角标：右上角，与"可定制"（左上角）不冲突 */
.new-badge {
  left: auto;
  right: 0.5rem;
  background: #27ae60;
  font-size: 0.8rem;
}

.product-info {
  padding: 1rem;
}

.product-name {
  margin: 0 0 0.5rem 0;
  font-size: 1rem;
  color: #2c3e50;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-price {
  margin-bottom: 0.5rem;
}

.current-price {
  font-size: 1.1rem;
  font-weight: bold;
  color: #e74c3c;
}

.product-meta {
  display: flex;
  justify-content: space-between;
  font-size: 0.8rem;
  color: #666;
}

@media (max-width: 768px) {
  .recommendation-grid {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 1rem;
  }
  
  .product-image {
    height: 150px;
  }
  
  .product-info {
    padding: 0.75rem;
  }
  
  .product-name {
    font-size: 0.9rem;
  }
}
</style>
