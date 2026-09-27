<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  imageUrl?: string
  alt?: string
  size?: 'small' | 'medium' | 'large'
}>()

const sizeClasses = computed(() => {
  switch (props.size) {
    case 'small': return 'w-20 h-20'
    case 'large': return 'w-full h-400'
    default: return 'w-full h-200'
  }
})

const imagePath = computed(() => {
  if (!props.imageUrl) return ''
  return props.imageUrl
})

const handleImageError = (event: Event) => {
  const target = event.target as HTMLImageElement
  if (target) {
    target.style.display = 'none'
    const sibling = target.nextElementSibling as HTMLElement
    if (sibling) {
      sibling.style.display = 'flex'
    }
  }
}
</script>

<template>
  <div class="product-image-container" :class="sizeClasses">
    <img 
      v-if="imagePath" 
      :src="imagePath" 
      :alt="alt || '商品图片'"
      class="product-image"
      @error="handleImageError"
    />
    <div class="placeholder" :style="imagePath ? 'display:none' : ''">
      <span class="placeholder-icon">🏺</span>
      <span class="placeholder-text">{{ alt || '暂无图片' }}</span>
    </div>
  </div>
</template>

<style scoped>
.product-image-container {
  position: relative;
  overflow: hidden;
  border-radius: 8px;
  background: #f8f9fa;
}

.product-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.placeholder {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #f8f9fa;
  border: 2px dashed #ddd;
  border-radius: 8px;
}

.placeholder-icon {
  font-size: 3rem;
  margin-bottom: 0.5rem;
}

.placeholder-text {
  color: #999;
  font-size: 0.9rem;
}

.w-20 { width: 5rem; }
.h-20 { height: 5rem; }
.w-full { width: 100%; }
.h-200 { height: 200px; }
.h-400 { height: 400px; }
</style>