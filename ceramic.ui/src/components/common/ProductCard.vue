<!-- src/components/common/ProductCard.vue -->
<script setup lang="ts">
import type { Product } from '@/types'
import { RouterLink } from 'vue-router'

interface Props {
  product: Product
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'add-to-cart': [product: Product]
  'customize': [product: Product]
}>()

const formatPrice = (price: number): string => {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY'
  }).format(price)
}
</script>

<template>
  <div class="product-card">
    <div class="product-image">
      <img 
        v-if="product.imageUrl" 
        :src="product.imageUrl" 
        :alt="product.name"
        @error="(e: Event) => (e.target as HTMLImageElement).src='/placeholder.png'"
      />
      <div v-else class="no-image">暂无图片</div>
      
      <span 
        v-if="product.customizable" 
        class="customizable-badge"
      >
        可定制
      </span>
    </div>
    
    <div class="product-info">
      <h3 class="product-name">{{ product.name }}</h3>
      <p class="product-description">
        {{ product.description?.substring(0, 100) }}...
      </p>
      
      <div class="product-specs">
        <span v-if="product.material" class="spec-item">材质: {{ product.material }}</span>
        <span v-if="product.size" class="spec-item">尺寸: {{ product.size }}</span>
        <span v-if="product.glazeColor" class="spec-item">釉色: {{ product.glazeColor }}</span>
      </div>
      
      <div class="product-footer">
        <span class="product-price">{{ formatPrice(product.price) }}</span>
        <span class="product-stock" :class="{ low: product.stock < 10 }">
          库存: {{ product.stock }}
        </span>
      </div>
      
      <div class="product-actions">
        <RouterLink 
          :to="`/products/${product.id}`" 
          class="btn btn-outline"
        >
          查看详情
        </RouterLink>
        
        <button 
          v-if="product.customizable"
          class="btn btn-primary"
          @click="emit('customize', product)"
        >
          定制
        </button>
        
        <button 
          v-else
          class="btn btn-secondary"
          @click="emit('add-to-cart', product)"
        >
          加入购物车
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.product-card {
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--c-surface);
  transition: transform 0.2s, box-shadow 0.2s, border-color 0.2s;
}

.product-card:hover {
  transform: translateY(-3px);
  border-color: var(--c-gold);
  box-shadow: var(--shadow-lg);
}

.product-image {
  position: relative;
  height: 200px;
  overflow: hidden;
  background: var(--c-primary-soft);
}

.product-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.no-image {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--c-primary-soft);
  color: var(--c-text-muted);
}

.customizable-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  background: var(--c-celadon);
  color: var(--c-primary-deep);
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  font-size: 0.8rem;
  font-weight: bold;
  /* 描金细边 */
  border: 1px solid var(--c-gold);
}

.product-info {
  padding: 1rem;
}

.product-name {
  margin: 0 0 0.5rem 0;
  font-size: 1.1rem;
  font-weight: 600;
  font-family: var(--font-serif);
  color: var(--c-text);
}

.product-description {
  margin: 0 0 1rem 0;
  color: var(--c-text-muted);
  font-size: 0.9rem;
  line-height: 1.4;
}

.product-specs {
  margin-bottom: 1rem;
}

.spec-item {
  display: inline-block;
  background: var(--c-primary-soft);
  color: var(--c-primary-dark);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  font-size: 0.8rem;
  margin-right: 4px;
  margin-bottom: 4px;
}

.product-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.product-price {
  font-size: 1.2rem;
  font-weight: bold;
  color: var(--c-accent);
}

.product-stock {
  font-size: 0.9rem;
  color: var(--c-primary);
}

.product-stock.low {
  color: var(--c-accent);
}

.product-actions {
  display: flex;
  gap: 0.5rem;
}

.btn {
  flex: 1;
  padding: 0.5rem 1rem;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  text-decoration: none;
  display: inline-block;
  text-align: center;
  font-size: 0.9rem;
  transition: background-color 0.2s, color 0.2s, border-color 0.2s;
}

.btn-outline {
  background: transparent;
  border: 1px solid var(--c-primary);
  color: var(--c-primary-dark);
  text-align: center;
}

.btn-outline:hover {
  background: var(--c-primary);
  color: #fff;
}

.btn-primary {
  background: var(--c-primary);
  color: #fff;
}

.btn-primary:hover {
  background: var(--c-primary-dark);
}

.btn-secondary {
  background: var(--c-accent);
  color: #fff;
}

.btn-secondary:hover {
  background: var(--c-accent-dark);
}
</style>