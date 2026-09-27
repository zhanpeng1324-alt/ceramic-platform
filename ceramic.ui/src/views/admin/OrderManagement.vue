<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '@/services/api'
import type { Order } from '@/types'
import AdminNavbar from '@/components/admin/AdminNavbar.vue'

const orders = ref<Order[]>([])
const filter = ref('all')
const loading = ref(true)
const statusOptions = ['PENDING_PAY', 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELLED']
const statusLabel: Record<string, string> = { PENDING_PAY: '待付款', PAID: '已付款', SHIPPED: '已发货', COMPLETED: '已完成', CANCELLED: '已取消' }
const visibleOrders = computed(() => filter.value === 'all' ? orders.value : orders.value.filter(item => item.status === filter.value))
const load = async () => { loading.value = true; try { orders.value = await api.orders() } finally { loading.value = false } }
const updateStatus = async (order: Order, event: Event) => {
  const status = (event.target as HTMLSelectElement).value
  await api.updateOrderStatus(order.id, status)
  order.status = status as Order['status']
}
const shipping = ref<Order | null>(null)
const shipForm = ref({ shippingCompany: '', trackingNo: '' })
const openShip = (order: Order) => { shipping.value = order; shipForm.value = { shippingCompany: '', trackingNo: '' } }
const closeShip = () => { shipping.value = null }
const submitShip = async () => {
  if (!shipping.value) return
  const company = shipForm.value.shippingCompany.trim()
  const trackingNo = shipForm.value.trackingNo.trim()
  if (!company || !trackingNo) { alert('请填写快递公司和快递单号'); return }
  await api.shipOrder(shipping.value.id, company, trackingNo)
  closeShip()
  await load()
}
onMounted(load)
</script>

<template><div class="admin-page"><AdminNavbar /><main><header><div><h1>订单管理</h1><p>处理付款、发货与完成状态</p></div><button @click="load">刷新</button></header><div class="tabs"><button :class="{active:filter==='all'}" @click="filter='all'">全部 {{ orders.length }}</button><button v-for="status in statusOptions" :key="status" :class="{active:filter===status}" @click="filter=status">{{ statusLabel[status] }}</button></div><p v-if="loading">加载中…</p><div v-else class="table-wrap"><table><thead><tr><th>订单号</th><th>用户</th><th>金额</th><th>收货信息</th><th>创建时间</th><th>状态</th><th>物流/发货</th></tr></thead><tbody><tr v-for="order in visibleOrders" :key="order.id"><td>{{ order.orderNo || '#' + order.id }}</td><td>#{{ order.userId }}</td><td>¥{{ order.totalAmount }}</td><td>{{ order.receiverName }}<br><small>{{ order.receiverPhone }} · {{ order.receiverAddress }}</small></td><td>{{ order.createdAt }}</td><td><select :value="order.status" @change="updateStatus(order, $event)"><option v-for="status in statusOptions" :key="status" :value="status" :disabled="status==='SHIPPED'">{{ statusLabel[status] }}</option></select></td><td><button v-if="order.status==='PAID'" class="ship-btn" @click="openShip(order)">发货</button><template v-else-if="order.trackingNo"><strong>{{ order.shippingCompany }}</strong><br><small>{{ order.trackingNo }}</small></template><span v-else class="muted">—</span></td></tr><tr v-if="!visibleOrders.length"><td colspan="7" class="empty">暂无订单</td></tr></tbody></table></div><div v-if="shipping" class="modal-mask" @click.self="closeShip"><div class="modal"><h3>订单发货</h3><p class="modal-sub">{{ shipping.orderNo || '#' + shipping.id }} · {{ shipping.receiverName }}</p><label>快递公司<input v-model="shipForm.shippingCompany" placeholder="如：顺丰速运 / 中通快递" /></label><label>快递单号<input v-model="shipForm.trackingNo" placeholder="请输入运单号" /></label><div class="modal-actions"><button class="ghost" @click="closeShip">取消</button><button @click="submitShip">确认发货</button></div></div></div></main></div></template>
<style scoped>main{max-width:1240px;margin:0 auto;padding:28px}.admin-page{min-height:100vh;background:#f6f8f7}header{display:flex;justify-content:space-between;align-items:center}h1{margin:0;color:#243b53}p{color:#718096}button{border:0;border-radius:6px;padding:9px 14px;background:#3d9b78;color:#fff;cursor:pointer}.tabs{display:flex;gap:8px;flex-wrap:wrap;margin:22px 0}.tabs button{background:#e7efeb;color:#426254}.tabs button.active{background:#3d9b78;color:#fff}.table-wrap{background:#fff;border-radius:12px;overflow:auto;box-shadow:0 2px 10px #2233}table{border-collapse:collapse;width:100%;min-width:850px}th,td{padding:15px;text-align:left;border-bottom:1px solid #edf1ef}th{background:#f8fbf9;color:#52616b}small{color:#718096}select{padding:7px;border:1px solid #ccd7d1;border-radius:6px}.empty{text-align:center;color:#718096}.ship-btn{background:#2f855a;padding:6px 12px}.muted{color:#a0aec0}.modal-mask{position:fixed;inset:0;background:rgba(0,0,0,.45);display:flex;align-items:center;justify-content:center;z-index:50}.modal{background:#fff;border-radius:12px;padding:24px;width:360px;max-width:90vw}.modal h3{margin:0 0 4px;color:#243b53}.modal-sub{margin:0 0 16px;color:#718096;font-size:13px}.modal label{display:block;margin-bottom:12px;color:#52616b;font-size:14px}.modal input{display:block;width:100%;margin-top:6px;padding:9px;border:1px solid #ccd7d1;border-radius:6px;box-sizing:border-box}.modal-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:8px}.modal-actions .ghost{background:#e7efeb;color:#426254}</style>
