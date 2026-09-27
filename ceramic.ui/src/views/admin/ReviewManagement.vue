<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '@/services/api'
import type { Review } from '@/types'
import AdminNavbar from '@/components/admin/AdminNavbar.vue'

const reviews = ref<Review[]>([])
const filter = ref('ALL')
const loading = ref(true)
const visible = computed(() => filter.value === 'ALL' ? reviews.value : reviews.value.filter(item => item.status === filter.value))
const labels: Record<string, string> = { VISIBLE: '已展示', HIDDEN: '已隐藏', REJECTED: '已拒绝' }
const load = async () => { loading.value = true; try { reviews.value = await api.adminReviews() } finally { loading.value = false } }
const setStatus = async (review: Review, status: 'VISIBLE' | 'HIDDEN' | 'REJECTED') => { await api.updateReviewStatus(review.id, status); review.status = status }
const replyDraft = ref<Record<number, string>>({})
const submitReply = async (review: Review) => {
  const text = (replyDraft.value[review.id] || '').trim()
  if (!text) return
  await api.replyReview(review.id, text)
  review.reply = text
  review.replyTime = new Date().toISOString()
  replyDraft.value[review.id] = ''
}
onMounted(load)
</script>
<template><div class="admin-page"><AdminNavbar/><main><header><div><h1>评价管理</h1><p>审核并维护商品评价内容</p></div><button @click="load">刷新</button></header><div class="tabs"><button v-for="item in ['ALL','VISIBLE','HIDDEN','REJECTED']" :key="item" :class="{active:filter===item}" @click="filter=item">{{ item === 'ALL' ? '全部' : labels[item] }}</button></div><p v-if="loading">加载中…</p><div v-else class="list"><article v-for="review in visible" :key="review.id"><div class="top"><strong>{{ review.productName || '商品 #' + review.productId }}</strong><span>⭐ {{ review.rating }}/5 · {{ review.username || '匿名用户' }}</span></div><h3>{{ review.title || '未填写标题' }}</h3><p>{{ review.content }}</p><div class="reply" v-if="review.reply"><strong>商家回复：</strong>{{ review.reply }}</div><div class="reply-box"><textarea v-model="replyDraft[review.id]" :placeholder="review.reply ? '修改回复…' : '输入商家回复…'" rows="2"></textarea><button @click="submitReply(review)">{{ review.reply ? '更新回复' : '回复' }}</button></div><footer><small>{{ review.createdAt }}</small><div><button @click="setStatus(review,'VISIBLE')">展示</button><button class="muted" @click="setStatus(review,'HIDDEN')">隐藏</button><button class="danger" @click="setStatus(review,'REJECTED')">拒绝</button></div></footer></article><p v-if="!visible.length" class="empty">暂无评价</p></div></main></div></template>
<style scoped>.admin-page{min-height:100vh;background:#f6f8f7}main{max-width:1050px;margin:auto;padding:28px}header,footer,.top{display:flex;justify-content:space-between;align-items:center;gap:12px}h1{margin:0;color:#243b53}p{color:#536572;line-height:1.6}button{border:0;border-radius:6px;background:#3d9b78;color:white;padding:8px 12px;cursor:pointer}.tabs{display:flex;gap:8px;margin:20px 0}.tabs button{background:#e5eee9;color:#426254}.tabs .active{background:#3d9b78;color:#fff}.list{display:grid;gap:12px}article{background:#fff;border-radius:10px;padding:18px;box-shadow:0 2px 10px #2232}h3{font-size:16px;margin:14px 0 0}.top span,small{color:#718096;font-size:13px}.muted{background:#718096;margin-left:8px}.danger{background:#c95353;margin-left:8px}.empty{text-align:center;padding:48px}.reply{background:#f0f7f4;border-left:3px solid #3d9b78;border-radius:6px;padding:10px 12px;margin-top:10px;color:#2f5546;font-size:14px}.reply-box{display:flex;gap:8px;margin-top:10px}.reply-box textarea{flex:1;border:1px solid #d5e0da;border-radius:6px;padding:8px;font-family:inherit;resize:vertical}.reply-box button{align-self:flex-start}</style>
