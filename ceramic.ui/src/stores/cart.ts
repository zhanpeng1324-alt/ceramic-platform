// src/stores/cart.ts
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api } from '@/services/api'
import { recommendationService } from '@/services/recommendationService'
import type { Product, CartItem } from '@/types'

export interface CartItemLocal {
  id?: number
  productId: number
  product?: Product
  quantity: number
  price?: number
  productName?: string
  imageUrl?: string
  selectedOptions?: any
  productStatus?: string
  stock?: number
}

export const useCartStore = defineStore('cart', () => {
  const items = ref<CartItemLocal[]>([])
  // 勾选结算：选中的购物车项 id 集合；loadCart 后默认全选
  const selectedIds = ref<Set<number>>(new Set())

  const isSelected = (id?: number) => id != null && selectedIds.value.has(id)
  const toggleSelect = (id?: number) => {
    if (id == null) return
    const next = new Set(selectedIds.value)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    selectedIds.value = next
  }
  const isAllSelected = computed(() =>
    items.value.length > 0 && items.value.every(i => i.id != null && selectedIds.value.has(i.id)),
  )
  const toggleAll = () => {
    selectedIds.value = isAllSelected.value
      ? new Set()
      : new Set(items.value.map(i => i.id).filter((x): x is number => x != null))
  }

  const selectedItems = computed(() =>
    items.value.filter(i => i.id != null && selectedIds.value.has(i.id)),
  )
  const selectedCount = computed(() => selectedItems.value.reduce((t, i) => t + i.quantity, 0))
  const selectedAmount = computed(() =>
    selectedItems.value.reduce((t, i) => t + (i.price || 0) * i.quantity, 0),
  )

  const totalCount = computed(() => {
    return items.value.reduce((total, item) => total + item.quantity, 0)
  })

  const totalAmount = computed(() => {
    return items.value.reduce(
      (total, item) => total + (item.price || 0) * item.quantity,
      0
    )
  })

  const loadCart = async () => {
    try {
      const cartItems = await api.cart()
      items.value = cartItems.map(item => ({
        id: item.id,
        productId: item.productId,
        quantity: item.quantity,
        price: item.price,
        productName: item.productName,
        imageUrl: item.imageUrl,
        selectedOptions: item.selectedOptions,
        // 透传商品状态与库存，供购物车做「已下架 / 缺货」拦截
        productStatus: item.productStatus,
        stock: item.stock
      }))
      // 默认全选；已失效（下架/售罄）项不自动勾选
      selectedIds.value = new Set(
        items.value
          .filter(i => i.id != null && !(i.productStatus && i.productStatus !== 'active') && (i.stock == null || i.stock > 0))
          .map(i => i.id as number),
      )
    } catch {
      items.value = []
    }
  }

  const addToCart = async (product: Product, quantity = 1, selectedOptions?: any) => {
    try {
      await api.addCart({
        productId: product.id,
        quantity,
        productName: product.name,
        price: product.price,
        imageUrl: product.imageUrl,
        selectedOptions
      } as CartItem)
      await loadCart()
      recommendationService.recordBehavior(product.id, 'add_to_cart')
      return true
    } catch (error) {
      console.error('Cart store addToCart error:', error)
      return false
    }
  }

  const removeFromCart = async (productId: number) => {
    const item = items.value.find(i => i.productId === productId)
    if (item?.id) {
      try {
        await api.removeCart(item.id)
        await loadCart()
      } catch {
        items.value = items.value.filter(i => i.productId !== productId)
      }
    } else {
      items.value = items.value.filter(i => i.productId !== productId)
    }
  }

  const updateQuantity = async (productId: number, quantity: number) => {
    if (quantity <= 0) {
      await removeFromCart(productId)
      return
    }

    const item = items.value.find(i => i.productId === productId)
    if (item?.id) {
      try {
        await api.updateCart(item.id, { quantity })
        await loadCart()
      } catch {
        item.quantity = quantity
      }
    }
  }

  const clearCart = async () => {
    items.value = []
  }

  const clear = () => {
    items.value = []
  }

  const setItems = (newItems: CartItemLocal[]) => {
    items.value = newItems
  }

  return {
    items,
    selectedIds,
    isSelected,
    toggleSelect,
    isAllSelected,
    toggleAll,
    selectedItems,
    selectedCount,
    selectedAmount,
    totalCount,
    totalAmount,
    loadCart,
    addToCart,
    removeFromCart,
    updateQuantity,
    clearCart,
    clear,
    setItems
  }
})
