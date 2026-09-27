<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useCartStore } from '@/stores/cart'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const cartStore = useCartStore()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)

// 计算单个购物车项的可用性问题（空字符串表示正常）
const itemIssue = (item: { productStatus?: string; stock?: number; quantity: number }): string => {
  if (item.productStatus && item.productStatus !== 'active') return '已下架'
  if (item.stock != null && item.stock <= 0) return '已售罄'
  if (item.stock != null && item.stock < item.quantity) return `库存不足，仅剩 ${item.stock} 件`
  return ''
}

// 是否存在下架/缺货商品，用于阻止结算
const hasBlockingItem = computed(() => cartStore.items.some(i => itemIssue(i) !== ''))

const optionLabels: Record<string, Record<string, string>> = {
  baseMaterial: {
    kaolin: '高岭土',
    porcelain_clay: '瓷土',
    stoneware_clay: '陶土'
  },
  glazeType: {
    celadon: '青釉',
    white_glaze: '白釉',
    blue_and_white: '青花',
    crystalline_glaze: '结晶釉'
  },
  size: {
    small: '小号',
    medium: '中号',
    large: '大号'
  }
}

const formatCustomOptions = (options: any): string => {
  if (!options) return ''
  let parsed = options
  if (typeof options === 'string') {
    try {
      parsed = JSON.parse(options)
    } catch {
      return options
    }
  }
  
  const displayItems: string[] = []
  
  if (parsed.baseMaterial) {
    const baseMaterialLabel = optionLabels.baseMaterial?.[parsed.baseMaterial]
    displayItems.push(`材质: ${baseMaterialLabel || parsed.baseMaterial}`)
  }
  if (parsed.glazeType) {
    const glazeTypeLabel = optionLabels.glazeType?.[parsed.glazeType]
    displayItems.push(`釉色: ${glazeTypeLabel || parsed.glazeType}`)
  }
  if (parsed.size) {
    const sizeLabel = optionLabels.size?.[parsed.size]
    displayItems.push(`尺寸: ${sizeLabel || parsed.size}`)
  }
  if (parsed.customText) {
    displayItems.push(`刻字: ${parsed.customText}`)
  }
  if (parsed.customPattern) {
    displayItems.push(`图案: ${parsed.customPattern}`)
  }
  if (parsed.additionalNotes) {
    displayItems.push(`备注: ${parsed.additionalNotes}`)
  }
  
  return displayItems.join(' | ')
}

// 结账
const checkout = () => {
  if (!userStore.isLoggedIn) {
    alert('请先登录')
    router.push('/login')
    return
  }
  if (cartStore.totalCount === 0) {
    alert('购物车为空，请先添加商品')
    return
  }
  if (cartStore.selectedCount === 0) {
    alert('请先勾选要结算的商品')
    return
  }
  if (cartStore.selectedItems.some(i => itemIssue(i) !== '')) {
    alert('勾选的商品中有已下架或库存不足的，请调整后再结算')
    return
  }
  router.push('/checkout')
}

// 格式化货币
const formatCurrency = (amount: number): string => {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY'
  }).format(amount)
}

const loadCartItems = async () => {
  loading.value = true
  await cartStore.loadCart()
  loading.value = false
}

onMounted(() => {
  loadCartItems()
})
</script>

<template>
  <div class="cart-page">
    <div class="container">
      <h1>购物车</h1>
      
      <div v-if="loading" class="loading">加载中...</div>
      
      <div v-else-if="cartStore.items.length === 0" class="empty-cart">
        <div class="empty-icon">🛒</div>
        <h2>购物车为空</h2>
        <p>您还没有添加任何商品到购物车</p>
        <router-link to="/products" class="btn btn-primary">去逛逛</router-link>
      </div>
      
      <div v-else class="cart-content">
        <div class="cart-items">
          <div
            v-for="item in cartStore.items"
            :key="item.id || item.productId"
            class="cart-item"
            :class="{ 'item-unavailable': itemIssue(item) }"
          >
            <label class="item-check">
              <input
                type="checkbox"
                :checked="cartStore.isSelected(item.id)"
                :disabled="!!itemIssue(item)"
                @change="cartStore.toggleSelect(item.id)"
              />
            </label>
            <img
              :src="item.imageUrl || `/images/products/${item.productId}.jpg`"
              :alt="item.productName"
              class="item-image"
            />

            <div class="item-details">
              <h3 class="item-name">{{ item.productName }}<span v-if="itemIssue(item)" class="issue-badge">{{ itemIssue(item) }}</span></h3>
              
              <div class="item-specs" v-if="item.selectedOptions">
                <strong>定制信息:</strong> {{ formatCustomOptions(item.selectedOptions) }}
              </div>
              
              <div class="item-price">
                {{ formatCurrency(item.price || 0) }} × {{ item.quantity }}
              </div>
              
              <div class="item-actions">
                <div class="quantity-control">
                  <button 
                    @click="cartStore.updateQuantity(item.productId, item.quantity - 1)"
                    :disabled="item.quantity <= 1"
                  >
                    -
                  </button>
                  <span>{{ item.quantity }}</span>
                  <button 
                    @click="cartStore.updateQuantity(item.productId, item.quantity + 1)"
                  >
                    +
                  </button>
                </div>
                
                <button 
                  @click="cartStore.removeFromCart(item.productId)"
                  class="remove-btn"
                >
                  删除
                </button>
              </div>
            </div>
            
            <div class="item-total">
              {{ formatCurrency((item.price || 0) * item.quantity) }}
            </div>
          </div>
        </div>
        
        <div class="cart-summary">
          <h2>订单摘要</h2>

          <label class="select-all">
            <input
              type="checkbox"
              :checked="cartStore.isAllSelected"
              @change="cartStore.toggleAll"
            />
            全选
          </label>

          <div class="summary-item">
            <span>已选 {{ cartStore.selectedCount }} 件</span>
            <span>{{ formatCurrency(cartStore.selectedAmount) }}</span>
          </div>

          <div class="summary-item">
            <span>运费</span>
            <span>包邮</span>
          </div>

          <div class="summary-divider"></div>

          <div class="summary-item total">
            <span>总计</span>
            <span class="total-amount">{{ formatCurrency(cartStore.selectedAmount) }}</span>
          </div>

          <button @click="checkout" class="btn btn-primary checkout-btn" :disabled="cartStore.selectedCount === 0">
            去结算（{{ cartStore.selectedCount }}）
          </button>
          <p v-if="hasBlockingItem" class="checkout-warning">存在已下架或库存不足的商品，请先删除或调整</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cart-page {
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
  text-align: center;
  margin-bottom: var(--sp-6);
  color: var(--c-text);
  letter-spacing: 0.02em;
}

.loading {
  text-align: center;
  padding: var(--sp-6);
  color: var(--c-text-muted);
}

.empty-cart {
  text-align: center;
  padding: var(--sp-6) 0;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow);
}

.empty-icon {
  font-size: 4rem;
  margin-bottom: var(--sp-4);
}

.empty-cart h2 {
  margin: var(--sp-4) 0;
  color: var(--c-text);
}

.empty-cart p {
  color: var(--c-text-muted);
  margin-bottom: var(--sp-5);
}

.cart-content {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: var(--sp-5);
  align-items: start;
}

.cart-items {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  padding: var(--sp-5);
  box-shadow: var(--shadow);
}

.cart-item {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  padding: var(--sp-4) 0;
  border-bottom: 1px solid var(--c-border);
}

.item-check {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.item-check input {
  width: 18px;
  height: 18px;
  cursor: pointer;
  accent-color: var(--c-primary, #b45f3c);
}

.select-all {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.9rem;
  color: var(--c-text-secondary, #666);
  cursor: pointer;
  margin-bottom: var(--sp-3);
}

.select-all input {
  width: 16px;
  height: 16px;
  cursor: pointer;
  accent-color: var(--c-primary, #b45f3c);
}

.cart-item:first-child {
  padding-top: 0;
}

.cart-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.item-image {
  width: 88px;
  height: 88px;
  object-fit: cover;
  border-radius: var(--radius);
  border: 1px solid var(--c-border);
  flex-shrink: 0;
}

.item-details {
  flex: 1;
  min-width: 0;
}

.item-name {
  margin: 0 0 var(--sp-2) 0;
  color: var(--c-text);
  font-size: 1.05rem;
}

.item-specs {
  font-size: 0.8rem;
  color: var(--c-primary-dark);
  margin-bottom: var(--sp-2);
  background: var(--c-primary-soft);
  padding: var(--sp-1) var(--sp-2);
  border-radius: var(--radius-sm);
  display: inline-block;
}

.item-price {
  color: var(--c-text-muted);
  font-weight: 600;
  margin-bottom: var(--sp-3);
  font-size: 0.95rem;
}

.item-actions {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
}

.quantity-control {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--c-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--c-surface);
}

.quantity-control button {
  width: 32px;
  height: 32px;
  border: none;
  background: var(--c-surface);
  color: var(--c-primary);
  font-size: 1.1rem;
  line-height: 1;
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s;
}

.quantity-control button:hover:not(:disabled) {
  background: var(--c-primary);
  color: #fff;
}

.quantity-control button:disabled {
  color: var(--c-text-muted);
  cursor: not-allowed;
  opacity: 0.6;
}

.quantity-control span {
  min-width: 40px;
  text-align: center;
  font-weight: 600;
  color: var(--c-text);
  border-left: 1px solid var(--c-border);
  border-right: 1px solid var(--c-border);
  padding: 0 var(--sp-2);
  line-height: 32px;
}

.remove-btn {
  background: transparent;
  color: var(--c-accent);
  border: 1px solid var(--c-border);
  padding: var(--sp-1) var(--sp-3);
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 0.8rem;
  transition: background-color 0.15s, color 0.15s, border-color 0.15s;
}

.remove-btn:hover {
  background: var(--c-accent);
  border-color: var(--c-accent);
  color: #fff;
}

.item-total {
  font-weight: 700;
  color: var(--c-text);
  font-size: 1.15rem;
  margin-left: var(--sp-4);
  flex-shrink: 0;
}

.cart-summary {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  padding: var(--sp-5);
  box-shadow: var(--shadow);
  height: fit-content;
  position: sticky;
  top: var(--sp-5);
}

.cart-summary h2 {
  margin: 0 0 var(--sp-5) 0;
  color: var(--c-text);
  font-size: 1.25rem;
}

.summary-item {
  display: flex;
  justify-content: space-between;
  padding: var(--sp-2) 0;
  color: var(--c-text-muted);
}

.summary-item.total {
  font-weight: 700;
  font-size: 1.1rem;
  color: var(--c-text);
  padding-top: var(--sp-3);
}

.summary-divider {
  height: 1px;
  background: var(--c-border);
  margin: var(--sp-3) 0;
}

.total-amount {
  color: var(--c-accent);
  font-size: 1.5rem;
  font-weight: 800;
}

.checkout-btn {
  width: 100%;
  padding: var(--sp-4);
  background: var(--c-primary);
  color: #fff;
  border: none;
  border-radius: var(--radius);
  font-size: 1.05rem;
  font-weight: 600;
  cursor: pointer;
  margin-top: var(--sp-5);
  transition: background-color 0.2s, box-shadow 0.2s;
}

.checkout-btn:hover:not(:disabled) {
  background: var(--c-primary-dark);
  box-shadow: var(--shadow);
}

.checkout-btn:disabled {
  background: var(--c-border);
  color: var(--c-text-muted);
  cursor: not-allowed;
}

.checkout-warning {
  margin-top: var(--sp-3);
  color: var(--c-accent-dark);
  font-size: 0.85rem;
  text-align: center;
}

.item-unavailable {
  opacity: 0.6;
}

.issue-badge {
  display: inline-block;
  margin-left: var(--sp-2);
  padding: 2px var(--sp-2);
  background: #fbe9d6;
  color: #d9822b;
  border: 1px solid #f0c48a;
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 600;
  vertical-align: middle;
}

.btn {
  padding: var(--sp-3) var(--sp-5);
  border: none;
  border-radius: var(--radius);
  cursor: pointer;
  text-decoration: none;
  display: inline-block;
  text-align: center;
  font-size: 1rem;
  font-weight: 600;
  transition: background-color 0.2s, box-shadow 0.2s;
}

.btn-primary {
  background: var(--c-primary);
  color: #fff;
}

.btn-primary:hover {
  background: var(--c-primary-dark);
  box-shadow: var(--shadow);
}

@media (max-width: 768px) {
  .cart-content {
    grid-template-columns: 1fr;
  }

  .cart-item {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--sp-3);
  }

  .item-total {
    margin-left: 0;
    align-self: flex-end;
  }

  .cart-summary {
    position: static;
  }
}
</style>
