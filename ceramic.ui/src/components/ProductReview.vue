<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '@/services/api'
import type { Review } from '@/types'

const props = defineProps<{
  productId: number
}>()

const reviews = ref<Review[]>([])
const loading = ref(true)
const error = ref('')

const newReview = ref({
  rating: 5,
  content: '',
})

const submitting = ref(false)
const submitSuccess = ref(false)

const ratingOptions = [1, 2, 3, 4, 5]

onMounted(async () => {
  await loadReviews()
})

const loadReviews = async () => {
  loading.value = true
  try {
    reviews.value = await api.reviews(props.productId)
  } catch (e: any) {
    error.value = e.message || '加载评价失败'
  } finally {
    loading.value = false
  }
}

const submitReview = async () => {
  if (!newReview.value.content.trim()) {
    error.value = '请填写评价内容'
    return
  }
  submitting.value = true
  try {
    await api.createReview({
      productId: props.productId,
      rating: newReview.value.rating,
      content: newReview.value.content,
    })
    submitSuccess.value = true
    newReview.value = { rating: 5, content: '' }
    setTimeout(() => {
      submitSuccess.value = false
    }, 3000)
    await loadReviews()
  } catch (e: any) {
    error.value = e.message || '提交评价失败'
  } finally {
    submitting.value = false
  }
}

const getStarRating = (rating: number) => {
  return '★'.repeat(rating) + '☆'.repeat(5 - rating)
}

const formatDate = (dateStr?: string) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('zh-CN')
}
</script>

<template>
  <section class="product-review">
    <h2 class="section-title">
      <span class="icon">⭐</span>
      <span>商品评价</span>
      <span class="review-count">({{ reviews.length }})</span>
    </h2>

    <div v-if="error" class="error-message">{{ error }}</div>
    <div v-if="submitSuccess" class="success-message">评价提交成功！</div>

    <div class="review-form">
      <h3>发表评价</h3>
      <div class="rating-selector">
        <span class="label">评分：</span>
        <label 
          v-for="rating in ratingOptions" 
          :key="rating"
          class="rating-option"
          :class="{ active: newReview.rating === rating }"
        >
          <input type="radio" v-model="newReview.rating" :value="rating" />
          <span>{{ getStarRating(rating) }}</span>
        </label>
      </div>
      <div class="form-group">
        <label>评价内容</label>
        <textarea 
          v-model="newReview.content" 
          placeholder="请输入您的评价..." 
          rows="4"
        ></textarea>
      </div>
      <button class="submit-btn" @click="submitReview" :disabled="submitting">
        <span v-if="submitting">提交中...</span>
        <span v-else>提交评价</span>
      </button>
    </div>

    <div v-if="loading" class="loading">加载评价中...</div>

    <div v-else-if="reviews.length === 0" class="empty">
      <p>暂无评价，快来发表第一条评价吧！</p>
    </div>

    <div v-else class="review-list">
      <article v-for="review in reviews" :key="review.id" class="review-item">
        <div class="review-header">
          <span class="reviewer-name">{{ review.username || `用户${review.userId}` }}</span>
          <span class="review-rating">{{ getStarRating(review.rating) }}</span>
        </div>
        <p class="review-content">{{ review.content }}</p>
        <span class="review-date">{{ formatDate(review.createdAt) }}</span>
      </article>
    </div>
  </section>
</template>

<style scoped>
.product-review {
  background: white;
  border-radius: 12px;
  padding: 24px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 1.3rem;
  margin: 0 0 24px;
  color: #333;
}

.icon {
  font-size: 1.4rem;
}

.review-count {
  font-size: 0.9rem;
  font-weight: normal;
  color: #999;
}

.error-message {
  background: #fef2f2;
  color: #dc2626;
  padding: 12px;
  border-radius: 6px;
  margin-bottom: 16px;
}

.success-message {
  background: #f0fdf4;
  color: #16a34a;
  padding: 12px;
  border-radius: 6px;
  margin-bottom: 16px;
}

.review-form {
  background: #f9fafb;
  border-radius: 10px;
  padding: 20px;
  margin-bottom: 24px;
}

.review-form h3 {
  margin: 0 0 16px;
  font-size: 1rem;
  color: #333;
}

.rating-selector {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.rating-selector .label {
  font-size: 0.9rem;
  color: #666;
}

.rating-option {
  cursor: pointer;
  font-size: 1.2rem;
  color: #ddd;
  transition: color 0.2s;
}

.rating-option input {
  display: none;
}

.rating-option.active,
.rating-option:hover {
  color: #f39c12;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  font-size: 0.9rem;
  color: #666;
  margin-bottom: 8px;
}

.form-group textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 0.95rem;
  resize: vertical;
  box-sizing: border-box;
}

.form-group textarea:focus {
  outline: none;
  border-color: #42b883;
}

.submit-btn {
  background: #42b883;
  color: white;
  border: none;
  border-radius: 6px;
  padding: 10px 24px;
  font-size: 0.95rem;
  cursor: pointer;
  transition: background 0.2s;
}

.submit-btn:hover:not(:disabled) {
  background: #369870;
}

.submit-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.loading {
  text-align: center;
  padding: 30px;
  color: #999;
}

.empty {
  text-align: center;
  padding: 40px;
  color: #999;
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.review-item {
  padding-bottom: 20px;
  border-bottom: 1px solid #f0f0f0;
}

.review-item:last-child {
  border-bottom: none;
}

.review-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.reviewer-name {
  font-weight: 500;
  color: #333;
}

.review-rating {
  font-size: 1rem;
  color: #f39c12;
}

.review-content {
  margin: 0 0 10px;
  color: #555;
  line-height: 1.6;
}

.review-date {
  font-size: 0.85rem;
  color: #999;
}
</style>