<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '@/services/api'
import type { SupportTicket } from '@/types'
import AdminNavbar from '@/components/admin/AdminNavbar.vue'

const tickets = ref<SupportTicket[]>([])
const loading = ref(true)
const selectedStatus = ref('ALL')
const replyDraft = ref<Record<number, string>>({})
const statuses = ['OPEN', 'IN_PROGRESS', 'REPLIED', 'RESOLVED', 'CLOSED']
const labels: Record<string, string> = { OPEN: '待处理', IN_PROGRESS: '处理中', REPLIED: '已回复', RESOLVED: '已解决', CLOSED: '已关闭' }
const visible = computed(() => selectedStatus.value === 'ALL' ? tickets.value : tickets.value.filter(item => item.status.toUpperCase() === selectedStatus.value))
const load = async () => { loading.value = true; try { tickets.value = await api.supportTickets() } finally { loading.value = false } }
const updateStatus = async (ticket: SupportTicket, event: Event) => { const status = (event.target as HTMLSelectElement).value; await api.updateTicketStatus(ticket.id, status); ticket.status = status as SupportTicket['status'] }
const updatePriority = async (ticket: SupportTicket, event: Event) => { const priority = (event.target as HTMLSelectElement).value; await api.updateTicketPriority(ticket.id, priority); ticket.priority = priority as SupportTicket['priority'] }
const reply = async (ticket: SupportTicket) => { const content = replyDraft.value[ticket.id]?.trim(); if (!content) return; await api.replyTicket(ticket.id, { reply: content, status: 'replied' }); ticket.reply = content; ticket.status = 'replied'; replyDraft.value[ticket.id] = '' }
onMounted(load)
</script>
<template><div class="admin-page"><AdminNavbar/><main><header><div><h1>售后工单管理</h1><p>集中处理客户咨询、投诉与售后需求</p></div><button @click="load">刷新</button></header><div class="tabs"><button :class="{active:selectedStatus==='ALL'}" @click="selectedStatus='ALL'">全部</button><button v-for="status in statuses" :key="status" :class="{active:selectedStatus===status}" @click="selectedStatus=status">{{ labels[status] }}</button></div><p v-if="loading">加载中…</p><div v-else class="grid"><article v-for="ticket in visible" :key="ticket.id"><div class="top"><strong>#{{ ticket.id }} {{ ticket.title }}</strong><select :value="ticket.status.toUpperCase()" @change="updateStatus(ticket,$event)"><option v-for="status in statuses" :key="status" :value="status">{{ labels[status] }}</option></select></div><p class="meta">{{ ticket.customerName || ticket.contactName || '客户' }} · {{ ticket.contactPhone || '未留电话' }} · {{ ticket.createdAt }}</p><p>{{ ticket.content }}</p><p v-if="ticket.reply" class="reply"><b>已回复：</b>{{ ticket.reply }}</p><div class="controls"><select :value="ticket.priority" @change="updatePriority(ticket,$event)"><option value="low">低优先级</option><option value="medium">中优先级</option><option value="high">高优先级</option></select><input v-model="replyDraft[ticket.id]" placeholder="输入处理回复"/><button @click="reply(ticket)">发送回复</button></div></article><p v-if="!visible.length" class="empty">暂无工单</p></div></main></div></template>
<style scoped>.admin-page{min-height:100vh;background:#f6f8f7}main{max-width:1100px;margin:auto;padding:28px}header,.top{display:flex;justify-content:space-between;align-items:center;gap:12px}h1{margin:0;color:#243b53}p{color:#536572;line-height:1.6}.tabs{display:flex;gap:8px;flex-wrap:wrap;margin:20px 0}button{border:0;border-radius:6px;background:#3d9b78;color:#fff;padding:8px 12px;cursor:pointer}.tabs button{background:#e5eee9;color:#426254}.tabs .active{background:#3d9b78;color:#fff}.grid{display:grid;gap:14px}article{background:#fff;padding:18px;border-radius:10px;box-shadow:0 2px 10px #2232}.meta{font-size:13px;color:#718096}.reply{background:#edf8f2;padding:10px;border-radius:6px}.controls{display:grid;grid-template-columns:110px 1fr auto;gap:8px;margin-top:14px}select,input{border:1px solid #ccd7d1;border-radius:6px;padding:8px}.empty{text-align:center;padding:48px}</style>
