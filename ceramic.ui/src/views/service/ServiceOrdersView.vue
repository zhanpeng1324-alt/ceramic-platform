<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/services/api'
import type { Order } from '@/types'
import ServiceNavbar from '@/components/service/ServiceNavbar.vue'

const router = useRouter()
const orders = ref<Order[]>([])
const loading = ref(true)
const searchKeyword = ref('')

const formatDate = (dateStr: string | undefined): string => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

const getStatusLabel = (status: string) => {
  const labels: Record<string, string> = {
    PENDING_PAY: '待付款',
    PAID: '已付款',
    SHIPPED: '已发货',
    COMPLETED: '已完成',
    CANCELLED: '已取消'
  }
  return labels[status] || status
}

const getStatusClass = (status: string) => {
  return `status-${status}`
}

const canShip = (order: Order): boolean => {
  return order.status === 'paid'
}

const handleShip = async (order: Order) => {
  if (!confirm(`确定要发货订单 ${order.orderNo} 吗？`)) return
  
  try {
    await api.updateOrderStatus(order.id, 'shipped')
    alert('发货成功')
    await loadOrders()
  } catch (error: any) {
    console.error('发货失败:', error)
    alert(error.message || '发货失败')
  }
}

const loadOrders = async () => {
  try {
    orders.value = await api.orders()
  } catch (err) {
    console.error('加载订单失败:', err)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadOrders()
})
</script>

<template>
  <div class="service-orders">
    <ServiceNavbar />
    
    <div class="search-box">
        <input 
          v-model="searchKeyword" 
          type="text" 
          placeholder="搜索订单号或用户名..."
          class="search-input"
        />
        <button class="btn btn-search">搜索</button>
      </div>

    <main class="orders-content">
      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="orders.length === 0" class="empty">暂无订单</div>
      <div v-else class="orders-table">
        <table>
          <thead>
            <tr>
              <th>订单号</th>
              <th>用户</th>
              <th>金额</th>
              <th>状态</th>
              <th>收货人</th>
              <th>收货地址</th>
              <th>下单时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in orders" :key="order.id">
              <td>#{{ order.id }}</td>
              <td>{{ order.userId }}</td>
              <td>{{ order.totalAmount }}</td>
              <td>
                <span class="status" :class="getStatusClass(order.status)">
                  {{ getStatusLabel(order.status) }}
                </span>
              </td>
              <td>{{ order.receiverName }}</td>
              <td>{{ order.receiverAddress }}</td>
              <td>{{ formatDate(order.createdAt) }}</td>
              <td>
                <button class="btn btn-view">查看详情</button>
                <button v-if="canShip(order)" class="btn btn-ship" @click="handleShip(order)">确认发货</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </main>
  </div>
</template>

<style scoped>
.service-orders {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.orders-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 2rem;
  background: white;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.header-left h1 {
  margin: 0;
  color: #2c3e50;
}

.btn-back {
  background: #95a5a6;
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  cursor: pointer;
  margin-left: 1rem;
}

.search-box {
  display: flex;
  gap: 0.5rem;
}

.search-input {
  padding: 0.5rem;
  border: 1px solid #ddd;
  border-radius: 4px;
}

.btn-search {
  background: #42b883;
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  cursor: pointer;
}

.orders-content {
  flex: 1;
  padding: 1.5rem;
}

.loading, .empty {
  text-align: center;
  padding: 2rem;
  color: #666;
}

.orders-table {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  overflow: hidden;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th, td {
  padding: 0.75rem 1rem;
  text-align: left;
  border-bottom: 1px solid #eee;
}

th {
  background: #f8f9fa;
  font-weight: bold;
  color: #2c3e50;
}

.status {
  padding: 0.2rem 0.5rem;
  border-radius: 3px;
  font-size: 0.75rem;
}

.status-pending_pay {
  background: #e74c3c;
  color: white;
}

.status-paid {
  background: #f39c12;
  color: white;
}

.status-shipped {
  background: #3498db;
  color: white;
}

.status-delivered {
  background: #27ae60;
  color: white;
}

.status-completed {
  background: #95a5a6;
  color: white;
}

.status-cancelled {
  background: #7f8c8d;
  color: white;
}

.btn-view {
  background: #42b883;
  color: white;
  border: none;
  padding: 0.4rem 0.8rem;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
  margin-right: 0.5rem;
}

.btn-ship {
  background: #3498db;
  color: white;
  border: none;
  padding: 0.4rem 0.8rem;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
}

.btn-ship:hover {
  background: #2980b9;
}

@media (max-width: 768px) {
  .orders-header {
    flex-direction: column;
    gap: 1rem;
  }
  
  .orders-table {
    overflow-x: auto;
  }
}
</style>