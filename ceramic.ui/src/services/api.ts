import type {
  AdminStats,
  AiChatResponse,
  ApiResult,
  CartItem,
  Category,
  ChatConversation,
  ChatMessage,
  Customization,
  CustomizationProgress,
  LogisticsTrace,
  Notification,
  Order,
  OrderItem,
  Payment,
  Product,
  ProductDetailResponse,
  Recommendation,
  RecommendationRule,
  RestockSuggestion,
  Review,
  ReturnRequest,
  ReturnRequestLog,
  SeckillActivity,
  SeckillOrder,
  ShopSettings,
  SupportTicket,
  User,
  UserAddress,
  UserBehavior,
  UserPreference,
} from '@/types'

function getToken(): string | null {
  return localStorage.getItem('token')
}

function setToken(token: string): void {
  localStorage.setItem('token', token)
}

function clearToken(): void {
  localStorage.removeItem('token')
}

async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  const token = getToken()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json;charset=UTF-8',
    'Accept': 'application/json;charset=UTF-8',
    ...(options.headers as Record<string, string>),
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  const response = await fetch(url, {
    headers,
    ...options,
  })

  if (response.status === 401) {
    clearToken()
    localStorage.removeItem('user')
    window.location.href = '/login'
    throw new Error('登录已过期,请重新登录')
  }

  if (!response.ok) {
    throw new Error(`请求失败：${response.status}`)
  }

  const result = (await response.json()) as ApiResult<T>
  if (result.code !== 200) {
    throw new Error(result.msg || '服务异常')
  }
  return result.data
}

const jsonBody = (data: unknown) => JSON.stringify(data)

export interface AuthResponse {
  token: string
  user: User
}

export const api = {
  getToken,
  setToken,
  clearToken,
  categories: () => request<Category[]>('/api/categories'),
  products: (params: { categoryId?: number; keyword?: string; priceMin?: number; priceMax?: number; sort?: string } = {}) => {
    const query = new URLSearchParams()
    if (params.categoryId) query.set('categoryId', String(params.categoryId))
    if (params.keyword) query.set('keyword', params.keyword)
    if (params.priceMin != null) query.set('priceMin', String(params.priceMin))
    if (params.priceMax != null) query.set('priceMax', String(params.priceMax))
    if (params.sort) query.set('sort', params.sort)
    const suffix = query.toString() ? `?${query.toString()}` : ''
    return request<Product[]>(`/api/products${suffix}`)
  },
  // 分页版商品列表：返回 {items,total,page,size}，避免大表全量传输
  productsPage: (params: { categoryId?: number; keyword?: string; priceMin?: number; priceMax?: number; sort?: string; page: number; size: number }) => {
    const query = new URLSearchParams()
    if (params.categoryId) query.set('categoryId', String(params.categoryId))
    if (params.keyword) query.set('keyword', params.keyword)
    if (params.priceMin != null) query.set('priceMin', String(params.priceMin))
    if (params.priceMax != null) query.set('priceMax', String(params.priceMax))
    if (params.sort) query.set('sort', params.sort)
    query.set('page', String(params.page))
    query.set('size', String(params.size))
    return request<{ items: Product[]; total: number; page: number; size: number }>(`/api/products?${query.toString()}`)
  },
  product: (id: number) => request<ProductDetailResponse>(`/api/products/${id}`),
  // 管理端列表：含已下架商品（消费者端 products() 只返回上架）
  adminProducts: (params: { categoryId?: number; keyword?: string } = {}) => {
    const query = new URLSearchParams()
    if (params.categoryId) query.set('categoryId', String(params.categoryId))
    if (params.keyword) query.set('keyword', params.keyword)
    const suffix = query.toString() ? `?${query.toString()}` : ''
    return request<Product[]>(`/api/products/admin${suffix}`)
  },
  addCart: (item: CartItem) => request<CartItem>('/api/cart', { method: 'POST', body: jsonBody(item) }),
  cart: () => request<CartItem[]>('/api/cart'),
  removeCart: (id: number) => request<void>(`/api/cart/${id}`, { method: 'DELETE' }),
  updateCart: (id: number, item: Partial<CartItem>) =>
    request<CartItem>(`/api/cart/${id}`, { method: 'PUT', body: jsonBody(item) }),
  createOrder: (payload: {
    receiverName: string
    receiverPhone: string
    receiverAddress: string
    remark?: string
    payType?: string
    cartItemIds?: number[]
  }) => request<Order>('/api/orders', { method: 'POST', body: jsonBody(payload) }),
  orders: () => request<Order[]>('/api/orders'),
  // 客服/管理员按客户查其订单（后端 OrderController.list 支持 ?userId=，仅 admin/service 放行）
  serviceUserOrders: (userId: number) => request<Order[]>(`/api/orders?userId=${userId}`),
  orderDetail: (id: number) => request<Map<string, any>>(`/api/orders/${id}`),
  orderItems: (orderId: number) => request<OrderItem[]>(`/api/orders/${orderId}/items`),
  updateOrderStatus: (id: number, status: string) =>
    request<void>(`/api/orders/${id}/status`, { method: 'PATCH', body: jsonBody({ status }) }),
  shipOrder: (id: number, shippingCompany: string, trackingNo: string) =>
    request<void>(`/api/orders/${id}/ship`, { method: 'POST', body: jsonBody({ shippingCompany, trackingNo }) }),
  cancelOrder: (id: number) => request<void>(`/api/orders/${id}/cancel`, { method: 'POST' }),
  confirmOrder: (id: number) => request<void>(`/api/orders/${id}/confirm`, { method: 'POST' }),
  updateOrderAddress: (id: number, payload: { receiverName: string; receiverPhone: string; receiverAddress: string }) =>
    request<void>(`/api/orders/${id}/address`, { method: 'PUT', body: jsonBody(payload) }),
  payOrder: (id: number) => request<void>(`/api/orders/${id}/pay`, { method: 'POST' }),
  orderLogistics: (id: number) => request<LogisticsTrace[]>(`/api/orders/${id}/logistics`),
  // ===== 店铺设置（发货地/默认退货地址） =====
  shopSettings: () => request<ShopSettings>('/api/shop-settings'),
  updateShopSettings: (payload: ShopSettings) =>
    request<ShopSettings>('/api/shop-settings', { method: 'PUT', body: jsonBody(payload) }),
  // ===== 模拟支付网关 =====
  createPayment: (bizType: 'ORDER' | 'CUSTOM_DEPOSIT' | 'CUSTOM_BALANCE' | 'SECKILL', bizId: number, channel?: string) =>
    request<Payment>('/api/payments', { method: 'POST', body: jsonBody({ bizType, bizId, channel }) }),
  /** 收银台「确认支付」：服务端确认入账；顾客无法直接触发网关回调（callback 仅 admin 可重推）。 */
  confirmPayment: (paymentNo: string) =>
    request<Payment>(`/api/payments/${paymentNo}/confirm`, { method: 'POST' }),
  /**
   * 模拟支付网关一键支付：发起支付流水（PENDING）→ 收银台确认入账（SUCCESS），
   * 返回最终成功的流水。订单、定制定金、定制尾款三类支付共用此入口。
   */
  payViaGateway: async (
    bizType: 'ORDER' | 'CUSTOM_DEPOSIT' | 'CUSTOM_BALANCE' | 'SECKILL',
    bizId: number,
    channel?: string,
  ) => {
    const pending = await request<Payment>('/api/payments', {
      method: 'POST',
      body: jsonBody({ bizType, bizId, channel }),
    })
    return request<Payment>(`/api/payments/${pending.paymentNo}/confirm`, { method: 'POST' })
  },
  // ===== 站内通知 =====
  notifications: () => request<Notification[]>('/api/notifications'),
  unreadNotificationCount: () => request<{ count: number }>('/api/notifications/unread-count'),
  markNotificationRead: (id: number) => request<void>(`/api/notifications/${id}/read`, { method: 'PUT' }),
  markAllNotificationsRead: () => request<void>('/api/notifications/read-all', { method: 'PUT' }),
  createCustomization: (payload: Partial<Customization>) =>
    request<Customization>('/api/customizations', { method: 'POST', body: jsonBody(payload) }),
  customizationDetail: (id: number) => request<Customization>(`/api/customizations/${id}`),
  customizations: (userId?: number) =>
    request<Customization[]>(`/api/customizations${userId ? `?userId=${userId}` : ''}`),
  transitionCustomizationStatus: (id: number, status: string, note?: string, imageUrl?: string) =>
    request<void>(`/api/customizations/${id}/status`, { method: 'PATCH', body: jsonBody({ status, note, imageUrl }) }),
  quoteCustomization: (id: number, quotedPrice: number, expectedCompleteDate: string, timelineNote: string) =>
    request<void>(`/api/customizations/${id}/quote`, { method: 'PATCH', body: jsonBody({ quotedPrice, expectedCompleteDate, timelineNote }) }),
  confirmCustomizationQuote: (id: number) =>
    request<void>(`/api/customizations/${id}/confirm-quote`, { method: 'POST' }),
  payCustomizationDeposit: (id: number) =>
    request<void>(`/api/customizations/${id}/pay-deposit`, { method: 'POST' }),
  acceptCustomization: (id: number) =>
    request<void>(`/api/customizations/${id}/accept`, { method: 'POST' }),
  rejectCustomizationQuote: (id: number, reason?: string) =>
    request<void>(`/api/customizations/${id}/reject-quote`, { method: 'POST', body: jsonBody({ reason }) }),
  cancelCustomization: (id: number, reason?: string) =>
    request<void>(`/api/customizations/${id}/cancel`, { method: 'POST', body: jsonBody({ reason }) }),
  customizationProgress: (id: number) => request<CustomizationProgress[]>(`/api/customizations/${id}/progress`),
  addCustomizationProgress: (id: number, stage: string, description: string, imageUrl?: string) =>
    request<void>(`/api/customizations/${id}/progress`, { method: 'POST', body: jsonBody({ stage, description, imageUrl }) }),
  reviews: (productId: number) => request<Review[]>(`/api/reviews?productId=${productId}`),
  myReviewedProductIds: () => request<number[]>('/api/reviews/mine/products'),
  createReview: (payload: Partial<Review>) => request<Review>('/api/reviews', { method: 'POST', body: jsonBody(payload) }),
  adminReviews: () => request<Review[]>('/api/reviews/admin'),
  updateReviewStatus: (id: number, status: 'VISIBLE' | 'HIDDEN' | 'REJECTED') =>
    request<void>(`/api/reviews/${id}/status`, { method: 'PATCH', body: jsonBody({ status }) }),
  replyReview: (id: number, reply: string) =>
    request<void>(`/api/reviews/${id}/reply`, { method: 'POST', body: jsonBody({ reply }) }),
  supportTickets: () => request<SupportTicket[]>('/api/support-tickets'),
  updateTicketPriority: (id: number, priority: string) =>
    request<void>(`/api/support-tickets/${id}/priority`, { method: 'PATCH', body: jsonBody({ priority }) }),
  updateTicketStatus: (id: number, status: string) =>
    request<void>(`/api/support-tickets/${id}/status`, { method: 'PATCH', body: jsonBody({ status }) }),
  createSupportTicket: (payload: SupportTicket) =>
    request<SupportTicket>('/api/support-tickets', { method: 'POST', body: jsonBody(payload) }),
  replyTicket: (id: number, payload: Partial<SupportTicket>) =>
    request<void>(`/api/support-tickets/${id}/reply`, { method: 'PATCH', body: jsonBody(payload) }),
  returnRequests: () => request<ReturnRequest[]>('/api/return-requests'),
  returnRequestDetail: (id: number) => request<ReturnRequest>(`/api/return-requests/${id}`),
  returnRequestLogs: (id: number) => request<ReturnRequestLog[]>(`/api/return-requests/${id}/logs`),
  returnRequestsByOrder: (orderId: number) => request<ReturnRequest[]>(`/api/return-requests/order/${orderId}`),
  createReturnRequest: (payload: Partial<ReturnRequest>) =>
    request<ReturnRequest>('/api/return-requests', { method: 'POST', body: jsonBody(payload) }),
  approveReturnRequest: (id: number, returnAddress?: string) =>
    request<void>(`/api/return-requests/${id}/approve`, { method: 'PUT', body: jsonBody({ returnAddress }) }),
  rejectReturnRequest: (id: number, reason?: string) =>
    request<void>(`/api/return-requests/${id}/reject`, { method: 'PUT', body: jsonBody({ reason }) }),
  fillReturnAddress: (id: number, returnAddress: string) =>
    request<void>(`/api/return-requests/${id}/return-address`, { method: 'PUT', body: jsonBody({ returnAddress }) }),
  shipBackReturn: (id: number, trackingNo: string) =>
    request<void>(`/api/return-requests/${id}/ship-back`, { method: 'PUT', body: jsonBody({ trackingNo }) }),
  receiveReturn: (id: number) =>
    request<void>(`/api/return-requests/${id}/receive`, { method: 'PUT' }),
  refundReturn: (id: number, refundAmount?: number) =>
    request<void>(`/api/return-requests/${id}/refund`, { method: 'PUT', body: jsonBody({ refundAmount }) }),
  closeReturn: (id: number, reason?: string) =>
    request<void>(`/api/return-requests/${id}/close`, { method: 'PUT', body: jsonBody({ reason }) }),
  chatConversations: () => request<ChatConversation[]>('/api/chat/conversations'),
  serviceChatConversations: (transferredOnly?: boolean) =>
    request<ChatConversation[]>(`/api/chat/service/conversations${transferredOnly ? '?transferredOnly=true' : ''}`),
  updateConversationStatus: (conversationId: number, status: string) =>
    request<void>(`/api/chat/service/conversations/${conversationId}/status`, { method: 'PUT', body: jsonBody({ status }) }),
  chatMessages: (conversationId: number) => request<ChatMessage[]>(`/api/chat/messages?conversationId=${conversationId}`),
  createChatConversation: () => request<ChatConversation>('/api/chat/conversations', { method: 'POST', body: jsonBody({}) }),
  sendChatMessage: (payload: { conversationId: number; message: string }) =>
    request<ChatMessage>('/api/chat/messages', { method: 'POST', body: jsonBody(payload) }),
  markAsRead: (conversationId: number) =>
    request<void>(`/api/chat/conversations/${conversationId}/read`, { method: 'POST' }),
  closeConversation: (conversationId: number) =>
    request<void>(`/api/chat/conversations/${conversationId}/close`, { method: 'POST' }),
  sendAiChatMessage: (payload: { conversationId: number; message: string }) =>
    request<AiChatResponse>('/api/chat/ai/messages', { method: 'POST', body: jsonBody(payload) }),
  transferToHuman: (conversationId: number, agentId?: number) =>
    request<void>(`/api/chat/conversations/${conversationId}/transfer`, { method: 'POST', body: jsonBody(agentId ? { agentId } : {}) }),
  register: (payload: { phone: string; password: string; code: string }) =>
    request<AuthResponse>('/api/users/register', { method: 'POST', body: jsonBody(payload) }),
  login: (username: string, password: string) =>
    request<AuthResponse>('/api/users/login', { method: 'POST', body: jsonBody({ username, password }) }),
  loginByPhone: (phone: string, password: string) =>
    request<AuthResponse>('/api/users/login', { method: 'POST', body: jsonBody({ username: phone, password }) }),
  loginService: (username: string, password: string) =>
    request<AuthResponse>('/api/users/service/login', { method: 'POST', body: jsonBody({ username, password }) }),
  sendSmsCode: (phone: string) =>
    request<{ demoMode: boolean; code?: string }>('/api/users/sms/send', { method: 'POST', body: jsonBody({ phone }) }),
  smsLogin: (phone: string, code: string) =>
    request<AuthResponse & { newUser?: boolean }>('/api/users/sms/login', { method: 'POST', body: jsonBody({ phone, code }) }),
  getProfile: () => request<User>('/api/users/profile'),
  updateProfile: (payload: Partial<User>) =>
    request<User>('/api/users/profile', { method: 'PUT', body: jsonBody(payload) }),
  adminUsers: () => request<User[]>('/api/users'),
  adminUpdateUserRole: (id: number, role: User['role']) =>
    request<User>(`/api/users/${id}/role`, { method: 'PUT', body: jsonBody({ role }) }),
  recommendations: (limit?: number) => request<Recommendation[]>(`/api/recommendations${limit ? `?limit=${limit}` : ''}`),
  generateRecommendations: (limit?: number) => request<Recommendation[]>(`/api/recommendations/generate${limit ? `?limit=${limit}` : ''}`),
  similarProducts: (productId: number, limit?: number) => request<Recommendation[]>(`/api/recommendations/similar/${productId}${limit ? `?limit=${limit}` : ''}`),
  recommendationRules: () => request<RecommendationRule[]>('/api/recommendations/rules'),
  createRecommendationRule: (payload: RecommendationRule) =>
    request<RecommendationRule>('/api/recommendations/rules', { method: 'POST', body: jsonBody(payload) }),
  updateRecommendationRule: (id: number, payload: RecommendationRule) =>
    request<RecommendationRule>(`/api/recommendations/rules/${id}`, { method: 'PUT', body: jsonBody(payload) }),
  toggleRecommendationRule: (id: number, isActive: number) =>
    request<void>(`/api/recommendations/rules/${id}/status?isActive=${isActive}`, { method: 'PATCH' }),
  recordBehavior: (payload: { productId: number; action: string; sessionDuration?: number; referrer?: string }) =>
    request<UserBehavior>('/api/user-behaviors', { method: 'POST', body: jsonBody(payload) }),
  userBehaviors: (productId?: number) => request<UserBehavior[]>(`/api/user-behaviors${productId ? `?productId=${productId}` : ''}`),
  userPreferences: () => request<UserPreference>('/api/user-behaviors/preferences'),
  customerList: () => request<User[]>('/api/users/customers'),
  serviceList: () => request<User[]>('/api/users/service'),
  user: (userId: number) => request<User>(`/api/users/${userId}`),
  addresses: () => request<UserAddress[]>('/api/addresses'),
  address: (id: number) => request<UserAddress>(`/api/addresses/${id}`),
  addAddress: (address: Omit<UserAddress, 'id' | 'createdAt' | 'updatedAt'>) =>
    request<UserAddress>('/api/addresses', { method: 'POST', body: jsonBody(address) }),
  updateAddress: (id: number, address: Partial<UserAddress>) =>
    request<UserAddress>(`/api/addresses/${id}`, { method: 'PUT', body: jsonBody(address) }),
  deleteAddress: (id: number) => request<void>(`/api/addresses/${id}`, { method: 'DELETE' }),
  setDefaultAddress: (id: number) =>
    request<UserAddress>(`/api/addresses/${id}/default`, { method: 'POST' }),
  getDefaultAddress: () => request<UserAddress>('/api/addresses/default'),
  uploadAvatar: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    const token = getToken()
    const headers: Record<string, string> = {}
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }
    return fetch('/api/files/upload', {
      method: 'POST',
      headers,
      body: formData
    }).then(async res => {
      if (!res.ok) throw new Error(`上传失败（${res.status}）`)
      return res.json()
    }).then(result => {
      if (result.code !== 200) throw new Error(result.msg || '上传失败')
      return result.data
    })
  },
  uploadImage: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    const token = getToken()
    const headers: Record<string, string> = {}
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }
    return fetch('/api/files/upload', {
      method: 'POST',
      headers,
      body: formData
    }).then(async res => {
      if (!res.ok) throw new Error(`上传失败（${res.status}）`)
      return res.json()
    }).then(result => {
      if (result.code !== 200) throw new Error(result.msg || '上传失败')
      return result.data as string
    })
  },
  adminProductCreate: (product: Partial<Product>) => request<Product>('/api/products', { method: 'POST', body: jsonBody(product) }),
  adminProductUpdate: (id: number, product: Partial<Product>) =>
    request<Product>(`/api/products/${id}`, { method: 'PUT', body: jsonBody(product) }),
  adminProductUpdateStatus: (id: number, status: string) =>
    request<void>(`/api/products/${id}/status`, { method: 'PATCH', body: jsonBody({ status }) }),
  adminProductDelete: (id: number) => request<void>(`/api/products/${id}`, { method: 'DELETE' }),
  adminDeleteUser: (id: number) => request<void>(`/api/users/${id}`, { method: 'DELETE' }),
  adminStats: () => request<AdminStats>('/api/admin/stats'),
  restockSuggestions: () => request<RestockSuggestion[]>('/api/admin/restock'),
  // ===== 秒杀 =====
  seckillActivities: () => request<SeckillActivity[]>('/api/seckill/activities'),
  seckillActivityDetail: (id: number) => request<SeckillActivity>(`/api/seckill/activities/${id}`),
  /** 抢购：命中 Redis Lua 原子扣减后立即返回 orderNo，订单经 MQ 异步落库，前端凭 orderNo 轮询 */
  seckillBuy: (activityId: number) =>
    request<{ orderNo: string }>(`/api/seckill/${activityId}/buy`, { method: 'POST', body: jsonBody({}) }),
  seckillMyOrders: () => request<SeckillOrder[]>('/api/seckill/orders'),
  seckillOrderDetail: (orderNo: string) => request<SeckillOrder>(`/api/seckill/orders/${orderNo}`),
  seckillAdminList: () => request<SeckillActivity[]>('/api/admin/seckill'),
  seckillAdminCreate: (payload: { productId: number; seckillPrice: number; totalStock: number; startTime: string; endTime: string }) =>
    request<SeckillActivity>('/api/admin/seckill', { method: 'POST', body: jsonBody(payload) }),
  seckillAdminCancel: (id: number) => request<void>(`/api/admin/seckill/${id}`, { method: 'DELETE' }),
}
