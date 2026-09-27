<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/services/api'
import { useUserStore } from '@/stores/user'
import type { User, Order, UserAddress } from '@/types'

const router = useRouter()
const userStore = useUserStore()

const currentUser = ref<User | null>(null)
const nickname = ref('')

const orderHistory = ref<Order[]>([])
// 个人中心仅展示最近 5 单（按时间倒序），完整列表走「全部订单」
const recentOrders = computed(() =>
  [...orderHistory.value]
    .sort((a, b) => {
      const ta = a.createdAt ? new Date(a.createdAt).getTime() : 0
      const tb = b.createdAt ? new Date(b.createdAt).getTime() : 0
      return tb - ta || b.id - a.id
    })
    .slice(0, 5)
)
const addresses = ref<UserAddress[]>([])
const isEditing = ref(false)
const loading = ref(true)
const uploading = ref(false)
const avatarFailed = ref(false)
const showAddressModal = ref(false)
const editingAddress = ref<UserAddress | null>(null)

const newAddress = reactive({
  receiverName: '',
  receiverPhone: '',
  address: '',
  isDefault: false,
})

const formatCurrency = (amount: number): string => {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY'
  }).format(amount)
}

const formatDate = (dateString: string): string => {
  return new Date(dateString).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 头像首字母兜底
const avatarInitial = computed(() =>
  currentUser.value?.nickname?.slice(0, 1) || currentUser.value?.username?.slice(0, 1) || '陶'
)

// 会员天数
const memberDays = computed(() => {
  const raw = currentUser.value?.createdAt
  if (!raw) return 0
  const created = new Date(raw).getTime()
  if (isNaN(created)) return 0
  return Math.max(0, Math.floor((Date.now() - created) / 86400000))
})

// 订单状态 → 文案 + 样式类（后端返回大写状态，统一按大写匹配）
const statusMeta = (status: string): { label: string; cls: string } => {
  const s = (status || '').toUpperCase()
  const map: Record<string, { label: string; cls: string }> = {
    PENDING_PAY: { label: '待付款', cls: 'pending' },
    PAID: { label: '已付款', cls: 'paid' },
    SHIPPED: { label: '已发货', cls: 'shipped' },
    DELIVERED: { label: '已送达', cls: 'delivered' },
    COMPLETED: { label: '已完成', cls: 'delivered' },
    CANCELLED: { label: '已取消', cls: 'cancelled' },
    CANCELED: { label: '已取消', cls: 'cancelled' },
  }
  return map[s] || { label: status, cls: 'pending' }
}

const isPendingPay = (status: string): boolean => (status || '').toUpperCase() === 'PENDING_PAY'
const isShipped = (status: string): boolean => (status || '').toUpperCase() === 'SHIPPED'

const getUserInfo = async () => {
  loading.value = true
  try {
    const user = await api.getProfile()
    currentUser.value = user
    avatarFailed.value = false
    nickname.value = user.nickname || ''
    userStore.setUser(user)
  } catch (error) {
    console.error('获取用户信息失败:', error)
  } finally {
    loading.value = false
  }
}

const getOrders = async () => {
  try {
    orderHistory.value = await api.orders()
  } catch (error) {
    console.error('获取订单失败:', error)
  }
}

const getAddresses = async () => {
  try {
    addresses.value = await api.addresses()
  } catch (error) {
    console.error('获取地址失败:', error)
    addresses.value = []
  }
}

const toggleEdit = () => {
  isEditing.value = !isEditing.value
}

const saveProfile = async () => {
  if (!currentUser.value) return

  try {
    const updated = await api.updateProfile({
      nickname: nickname.value,
      email: currentUser.value.email,
      phone: currentUser.value.phone,
    })
    currentUser.value = updated
    userStore.setUser(updated)

    alert('个人信息已保存！')
    isEditing.value = false
  } catch (error: any) {
    console.error('保存失败:', error)
    alert(error.message || '保存失败')
  }
}

const cancelEdit = () => {
  isEditing.value = false
}

const handleAvatarClick = () => {
  const fileInput = document.getElementById('avatar-input') as HTMLInputElement
  fileInput.click()
}

const handleAvatarChange = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]

  if (!file) return

  const allowedTypes = ['image/jpeg', 'image/png', 'image/gif']
  if (!allowedTypes.includes(file.type)) {
    alert('请上传图片文件（JPEG、PNG、GIF）')
    return
  }

  if (file.size > 5 * 1024 * 1024) {
    alert('图片大小不能超过5MB')
    return
  }

  uploading.value = true

  try {
    const avatarUrl = await api.uploadAvatar(file)
    if (currentUser.value) {
      const updated = await api.updateProfile({ avatar: avatarUrl })
      currentUser.value = updated
      avatarFailed.value = false
      userStore.setUser(updated)
    }
    alert('头像上传成功！')
  } catch (error: any) {
    console.error('头像上传失败:', error)
    alert(error.message || '头像上传失败')
  } finally {
    uploading.value = false
    target.value = ''
  }
}

const viewOrderDetail = (orderId: number) => {
  // 进入订单详情页：物流信息、收货人信息、修改地址
  router.push(`/orders/${orderId}`)
}

const trackOrder = (orderId: number) => {
  // 物流信息在订单详情页展示
  router.push(`/orders/${orderId}`)
}

const openAddAddress = () => {
  editingAddress.value = null
  newAddress.receiverName = ''
  newAddress.receiverPhone = ''
  newAddress.address = ''
  newAddress.isDefault = addresses.value.length === 0
  showAddressModal.value = true
}

const openEditAddress = (address: UserAddress) => {
  editingAddress.value = address
  newAddress.receiverName = address.receiverName
  newAddress.receiverPhone = address.receiverPhone
  newAddress.address = address.address
  newAddress.isDefault = address.isDefault
  showAddressModal.value = true
}

const saveAddress = async () => {
  if (!newAddress.receiverName || !newAddress.receiverPhone || !newAddress.address) {
    alert('请填写完整的地址信息')
    return
  }

  try {
    if (editingAddress.value) {
      await api.updateAddress(editingAddress.value.id, {
        receiverName: newAddress.receiverName,
        receiverPhone: newAddress.receiverPhone,
        address: newAddress.address,
        isDefault: newAddress.isDefault
      })
      alert('地址修改成功！')
    } else {
      await api.addAddress({
        receiverName: newAddress.receiverName,
        receiverPhone: newAddress.receiverPhone,
        address: newAddress.address,
        isDefault: newAddress.isDefault
      })
      alert('地址添加成功！')
    }

    showAddressModal.value = false
    await getAddresses()
  } catch (error: any) {
    console.error('保存地址失败:', error)
    alert(error.message || '保存地址失败')
  }
}

const deleteAddress = async (id: number) => {
  if (!confirm('确定要删除这个地址吗？')) return

  try {
    await api.deleteAddress(id)
    await getAddresses()
    alert('地址已删除')
  } catch (error: any) {
    console.error('删除地址失败:', error)
    alert(error.message || '删除地址失败')
  }
}

const setDefaultAddress = async (id: number) => {
  try {
    await api.setDefaultAddress(id)
    await getAddresses()
    alert('已设为默认地址')
  } catch (error: any) {
    console.error('设置默认地址失败:', error)
    alert(error.message || '设置失败')
  }
}

const closeModal = () => {
  showAddressModal.value = false
  editingAddress.value = null
}

onMounted(async () => {
  if (!userStore.isLoggedIn) {
    alert('请先登录')
    router.push('/login')
    return
  }
  await getUserInfo()
  getOrders()
  getAddresses()
})
</script>

<template>
  <div class="profile-page">
    <div class="container">
      <div v-if="loading" class="loading">加载中...</div>

      <template v-else>
        <!-- ===== 顶部资料横幅 ===== -->
        <section class="profile-hero">
          <div class="hero-bg"></div>
          <div class="hero-body">
            <div
              class="hero-avatar"
              :class="{ uploading }"
              @click="handleAvatarClick"
              title="点击更换头像"
            >
              <img
                v-if="!avatarFailed"
                :src="currentUser?.avatar || '/avatar-default.svg'"
                alt="头像"
                class="avatar-img"
                @error="avatarFailed = true"
              />
              <div v-else class="avatar-fallback">{{ avatarInitial }}</div>
              <span class="avatar-cam" aria-hidden="true">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
                  <path d="M9 3l-1.8 2H4a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-3.2L15 3H9zm3 5a5 5 0 1 1 0 10 5 5 0 0 1 0-10zm0 2a3 3 0 1 0 0 6 3 3 0 0 0 0-6z"/>
                </svg>
              </span>
              <div v-if="uploading" class="avatar-loading">上传中…</div>
            </div>

            <input
              type="file"
              id="avatar-input"
              class="avatar-input"
              accept="image/jpeg,image/png,image/gif,image/webp"
              @change="handleAvatarChange"
            />

            <div class="hero-main">
              <h1 class="hero-name">{{ currentUser?.nickname || currentUser?.username || '陶瓷用户' }}</h1>
              <p class="hero-sub">
                <span>@{{ currentUser?.username }}</span>
                <span class="dot">·</span>
                <span>{{ currentUser?.email || '未绑定邮箱' }}</span>
              </p>
              <div class="hero-stats">
                <div class="stat">
                  <span class="stat-num">{{ orderHistory.length }}</span>
                  <span class="stat-label">订单</span>
                </div>
                <div class="stat">
                  <span class="stat-num">{{ addresses.length }}</span>
                  <span class="stat-label">收货地址</span>
                </div>
                <div class="stat">
                  <span class="stat-num">{{ memberDays }}</span>
                  <span class="stat-label">加入天数</span>
                </div>
              </div>
            </div>

            <button v-if="!isEditing" class="hero-edit-btn" @click="toggleEdit">编辑资料</button>
          </div>
        </section>

        <div class="profile-container">
          <!-- ===== 左：账户资料 ===== -->
          <div class="panel account-panel">
            <div class="panel-header">
              <h2 class="ui-section-title">账户资料</h2>
              <div v-if="isEditing" class="edit-actions">
                <button class="btn btn-secondary btn-small" @click="cancelEdit">取消</button>
                <button class="btn btn-primary btn-small" @click="saveProfile">保存</button>
              </div>
            </div>

            <form v-if="isEditing" class="profile-form">
              <div class="form-group">
                <label>用户名</label>
                <input type="text" v-model="currentUser!.username" placeholder="请输入用户名">
              </div>
              <div class="form-group">
                <label>姓名</label>
                <input type="text" v-model="nickname" placeholder="请输入姓名">
              </div>
              <div class="form-group">
                <label>邮箱</label>
                <input type="email" v-model="currentUser!.email" placeholder="请输入邮箱">
              </div>
              <div class="form-group">
                <label>手机号</label>
                <input type="tel" v-model="currentUser!.phone" placeholder="请输入手机号">
              </div>
            </form>

            <div v-else class="info-list">
              <div class="info-row">
                <span class="info-label">用户名</span>
                <span class="info-value">{{ currentUser?.username }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">姓名</span>
                <span class="info-value">{{ currentUser?.nickname || '未设置' }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">邮箱</span>
                <span class="info-value">{{ currentUser?.email || '未设置' }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">手机号</span>
                <span class="info-value">{{ currentUser?.phone || '未设置' }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">注册时间</span>
                <span class="info-value">{{ formatDate(currentUser?.createdAt || '') }}</span>
              </div>
            </div>
          </div>

          <!-- ===== 右：订单 + 地址 ===== -->
          <div class="right-col">
            <div class="panel">
              <div class="panel-header">
                <h2 class="ui-section-title">订单历史</h2>
                <button class="link-btn" @click="router.push('/orders')">全部订单 →</button>
              </div>

              <div v-if="orderHistory.length === 0" class="empty-state">
                <span class="empty-icon">🧾</span>
                <p>暂无订单记录</p>
                <button class="btn btn-primary btn-small" @click="router.push('/products')">去逛逛</button>
              </div>

              <div v-else class="orders-list">
                <div v-for="order in recentOrders" :key="order.id" class="order-item">
                  <div class="order-top">
                    <span class="order-id">订单 #{{ order.id }}</span>
                    <span class="order-status" :class="statusMeta(order.status).cls">
                      {{ statusMeta(order.status).label }}
                    </span>
                  </div>
                  <div class="order-mid">
                    <span class="order-amount">{{ formatCurrency(order.totalAmount) }}</span>
                    <span class="order-date">{{ formatDate(order.createdAt) }}</span>
                  </div>
                  <div class="order-actions">
                    <button class="btn btn-small btn-outline" @click="viewOrderDetail(order.id)">查看订单</button>
                    <button
                      v-if="isPendingPay(order.status)"
                      class="btn btn-small btn-primary"
                      @click="router.push('/checkout')"
                    >
                      立即支付
                    </button>
                    <button
                      v-else-if="isShipped(order.status)"
                      class="btn btn-small btn-secondary"
                      @click="trackOrder(order.id)"
                    >
                      物流跟踪
                    </button>
                  </div>
                </div>
              </div>
            </div>

            <div class="panel">
              <div class="panel-header">
                <h2 class="ui-section-title">收货地址</h2>
                <button class="btn btn-secondary btn-small" @click="openAddAddress">＋ 新增地址</button>
              </div>

              <div v-if="addresses.length === 0" class="empty-state">
                <span class="empty-icon">📍</span>
                <p>暂无收货地址</p>
                <button class="btn btn-primary btn-small" @click="openAddAddress">添加地址</button>
              </div>

              <div v-else class="addresses-list">
                <div
                  v-for="addr in addresses"
                  :key="addr.id"
                  class="address-item"
                  :class="{ 'default': addr.isDefault }"
                >
                  <div class="address-info">
                    <div class="address-header">
                      <span class="address-name">{{ addr.receiverName }}</span>
                      <span class="address-phone">{{ addr.receiverPhone }}</span>
                      <span v-if="addr.isDefault" class="address-tag">默认</span>
                    </div>
                    <div class="address-text">{{ addr.address }}</div>
                  </div>
                  <div class="address-actions">
                    <button
                      v-if="!addr.isDefault"
                      class="link-btn"
                      @click="setDefaultAddress(addr.id)"
                    >
                      设为默认
                    </button>
                    <button class="link-btn" @click="openEditAddress(addr)">编辑</button>
                    <button
                      v-if="addresses.length > 1"
                      class="link-btn danger"
                      @click="deleteAddress(addr.id)"
                    >
                      删除
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- ===== 地址弹窗 ===== -->
    <div v-if="showAddressModal" class="modal-overlay" @click.self="closeModal">
      <div class="modal">
        <div class="modal-header">
          <h3>{{ editingAddress ? '编辑地址' : '新增地址' }}</h3>
          <button class="modal-close" @click="closeModal">×</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>收货人</label>
            <input type="text" v-model="newAddress.receiverName" placeholder="请输入收货人姓名">
          </div>
          <div class="form-group">
            <label>手机号</label>
            <input type="tel" v-model="newAddress.receiverPhone" placeholder="请输入手机号">
          </div>
          <div class="form-group">
            <label>详细地址</label>
            <textarea v-model="newAddress.address" placeholder="请输入详细地址" rows="3"></textarea>
          </div>
          <div class="form-group">
            <label class="checkbox-label">
              <input type="checkbox" v-model="newAddress.isDefault">
              设为默认地址
            </label>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-secondary" @click="closeModal">取消</button>
          <button class="btn btn-primary" @click="saveAddress">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.profile-page {
  padding: var(--sp-5) 0 var(--sp-6);
  min-height: calc(100vh - 120px);
  background: var(--c-bg);
}

.container {
  max-width: 1120px;
  margin: 0 auto;
  padding: 0 var(--sp-4);
}

.loading {
  text-align: center;
  padding: var(--sp-6);
  color: var(--c-text-muted);
}

/* ===== 顶部横幅 ===== */
.profile-hero {
  position: relative;
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-lg);
  margin-bottom: var(--sp-5);
  background: var(--c-surface);
}

.hero-bg {
  height: 96px;
  background: var(--c-primary-soft);
  border-bottom: 1px solid var(--c-border);
}

.hero-body {
  display: flex;
  align-items: flex-end;
  gap: var(--sp-4);
  padding: 0 var(--sp-5) var(--sp-5);
  margin-top: -48px;
  flex-wrap: wrap;
}

.hero-avatar {
  position: relative;
  width: 104px;
  height: 104px;
  border-radius: 50%;
  cursor: pointer;
  flex-shrink: 0;
  border: 4px solid var(--c-surface);
  background: var(--c-primary-soft);
  box-shadow: var(--shadow);
  overflow: hidden;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.avatar-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 2.4rem;
  font-weight: 700;
  color: var(--c-primary-dark);
  background: var(--c-primary-soft);
}

.avatar-cam {
  position: absolute;
  right: 4px;
  bottom: 4px;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--c-primary);
  color: #fff;
  border: 2px solid var(--c-surface);
  opacity: 0;
  transition: opacity 0.2s;
}

.hero-avatar:hover .avatar-cam {
  opacity: 1;
}

.avatar-loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.8rem;
  color: #fff;
  background: rgba(0, 0, 0, 0.45);
}

.avatar-input {
  display: none;
}

.hero-main {
  flex: 1;
  min-width: 0;
  padding-bottom: var(--sp-1);
}

.hero-name {
  margin: 0 0 var(--sp-1);
  font-size: 1.55rem;
  color: var(--c-text);
  line-height: 1.2;
}

.hero-sub {
  margin: 0 0 var(--sp-3);
  color: var(--c-text-muted);
  font-size: 0.9rem;
  display: flex;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

.hero-sub .dot {
  opacity: 0.5;
}

.hero-stats {
  display: flex;
  gap: var(--sp-5);
}

.stat {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.stat-num {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--c-primary-dark);
}

.stat-label {
  font-size: 0.78rem;
  color: var(--c-text-muted);
}

.hero-edit-btn {
  align-self: center;
  padding: var(--sp-2) var(--sp-4);
  border: 1px solid var(--c-primary);
  border-radius: 999px;
  background: transparent;
  color: var(--c-primary-dark);
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.hero-edit-btn:hover {
  background: var(--c-primary);
  color: #fff;
}

/* ===== 主体两栏 ===== */
.profile-container {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: var(--sp-5);
  align-items: start;
}

.right-col {
  display: flex;
  flex-direction: column;
  gap: var(--sp-5);
  min-width: 0;
}

.panel {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  padding: var(--sp-5);
  box-shadow: var(--shadow);
}

.account-panel {
  position: sticky;
  top: var(--sp-5);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--sp-3);
  margin-bottom: var(--sp-4);
  padding-bottom: var(--sp-3);
  border-bottom: 1px solid var(--c-border);
}

.panel-header h2 {
  margin: 0;
  font-size: 1.1rem;
}

.edit-actions {
  display: flex;
  gap: var(--sp-2);
}

/* ===== 账户信息列表 ===== */
.info-list {
  display: flex;
  flex-direction: column;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3) 0;
  border-bottom: 1px dashed var(--c-border);
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  color: var(--c-text-muted);
  font-size: 0.9rem;
  flex-shrink: 0;
}

.info-value {
  color: var(--c-text);
  font-weight: 600;
  text-align: right;
  word-break: break-all;
}

/* ===== 表单 ===== */
.form-group {
  margin-bottom: var(--sp-4);
}

.form-group label {
  display: block;
  margin-bottom: var(--sp-1);
  font-weight: 600;
  color: var(--c-text);
  font-size: 0.9rem;
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: var(--sp-2) var(--sp-3);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-sm);
  font-size: 1rem;
  color: var(--c-text);
  background: var(--c-surface);
  box-sizing: border-box;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.form-group input:focus,
.form-group textarea:focus {
  outline: none;
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px var(--c-primary-soft);
}

.form-group textarea {
  resize: vertical;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  cursor: pointer;
  font-weight: 600;
}

/* ===== 订单 ===== */
.orders-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.order-item {
  padding: var(--sp-4);
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  background: var(--c-bg);
  transition: box-shadow 0.2s, transform 0.2s;
}

.order-item:hover {
  box-shadow: var(--shadow);
  transform: translateY(-2px);
}

.order-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--sp-2);
}

.order-id {
  font-weight: 700;
  color: var(--c-text);
}

.order-status {
  padding: 2px var(--sp-2);
  border-radius: 999px;
  font-size: 0.78rem;
  font-weight: 600;
}

.order-status.pending {
  background: #fdf0e9;
  color: var(--c-accent-dark);
}

.order-status.paid,
.order-status.shipped {
  background: var(--c-primary-soft);
  color: var(--c-primary-dark);
}

.order-status.delivered {
  background: var(--c-primary);
  color: #fff;
}

.order-status.cancelled {
  background: #eee;
  color: var(--c-text-muted);
}

.order-mid {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: var(--sp-3);
}

.order-amount {
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--c-accent);
}

.order-date {
  color: var(--c-text-muted);
  font-size: 0.85rem;
}

.order-actions {
  display: flex;
  gap: var(--sp-2);
  justify-content: flex-end;
}

/* ===== 地址 ===== */
.addresses-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.address-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--sp-3);
  padding: var(--sp-4);
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  transition: border-color 0.2s;
}

.address-item.default {
  border-color: var(--c-primary);
  background: var(--c-primary-soft);
}

.address-header {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-bottom: var(--sp-1);
  flex-wrap: wrap;
}

.address-name {
  font-weight: 700;
  color: var(--c-text);
}

.address-phone {
  color: var(--c-text-muted);
  font-size: 0.9rem;
}

.address-tag {
  background: var(--c-primary);
  color: #fff;
  padding: 1px var(--sp-2);
  border-radius: var(--radius-sm);
  font-size: 0.72rem;
}

.address-text {
  color: var(--c-text);
  font-size: 0.9rem;
  line-height: 1.5;
}

.address-info {
  flex: 1;
  min-width: 0;
}

.address-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--sp-1);
  flex-shrink: 0;
}

/* ===== 空状态 ===== */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-2);
  padding: var(--sp-6) var(--sp-4);
  color: var(--c-text-muted);
}

.empty-icon {
  font-size: 2rem;
}

.empty-state p {
  margin: 0;
}

/* ===== 按钮 ===== */
.btn {
  padding: var(--sp-2) var(--sp-4);
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  text-align: center;
  font-size: 0.9rem;
  transition: background-color 0.2s, color 0.2s, border-color 0.2s;
}

.btn-small {
  padding: var(--sp-1) var(--sp-3);
  font-size: 0.82rem;
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
  background: #dcefe6;
}

.btn-outline {
  background: transparent;
  border: 1px solid var(--c-border);
  color: var(--c-text);
}

.btn-outline:hover {
  background: var(--c-primary);
  border-color: var(--c-primary);
  color: #fff;
}

/* 文字链接按钮（地址/订单区轻量操作） */
.link-btn {
  background: none;
  border: none;
  padding: 2px 4px;
  cursor: pointer;
  font-size: 0.83rem;
  color: var(--c-primary-dark);
  transition: color 0.2s;
}

.link-btn:hover {
  color: var(--c-primary);
  text-decoration: underline;
}

.link-btn.danger {
  color: var(--c-accent);
}

.link-btn.danger:hover {
  color: var(--c-accent-dark);
}

/* ===== 弹窗 ===== */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal {
  background: var(--c-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  width: 90%;
  max-width: 480px;
  max-height: 90vh;
  overflow-y: auto;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--sp-4) var(--sp-5);
  border-bottom: 1px solid var(--c-border);
}

.modal-header h3 {
  margin: 0;
  color: var(--c-text);
}

.modal-close {
  background: none;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  color: var(--c-text-muted);
  line-height: 1;
}

.modal-body {
  padding: var(--sp-5);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--sp-2);
  padding: var(--sp-4) var(--sp-5);
  border-top: 1px solid var(--c-border);
}

/* ===== 响应式 ===== */
@media (max-width: 900px) {
  .profile-container {
    grid-template-columns: 1fr;
  }

  .account-panel {
    position: static;
  }
}

@media (max-width: 620px) {
  .hero-body {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }

  .hero-sub {
    justify-content: center;
  }

  .hero-stats {
    justify-content: center;
  }

  .hero-edit-btn {
    align-self: stretch;
  }

  .order-actions {
    flex-wrap: wrap;
  }

  .address-item {
    flex-direction: column;
  }

  .address-actions {
    flex-direction: row;
    align-self: flex-end;
  }
}
</style>
