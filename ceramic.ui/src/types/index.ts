export interface ApiResult<T = any> {
  code: number;
  message: string;
  msg?: string;
  data: T;
  success: boolean;
}

export interface User {
  id: number;
  username: string;
  password?: string;
  nickname?: string;
  email: string;
  phone?: string;
  address?: string;
  avatar?: string;
  role: 'customer' | 'service' | 'admin';
  status?: string;
  createdAt: string;
  updatedAt: string;
}

export interface UserAddress {
  id: number;
  userId?: number;
  receiverName: string;
  receiverPhone: string;
  address: string;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UserProfile {
  firstName: string;
  lastName: string;
  gender: 'male' | 'female' | 'other';
  birthDate?: string;
  preferences: {
    newsletter: boolean;
    notifications: boolean;
    language: string;
  };
}

export interface Category {
  id: number;
  name: string;
  description?: string;
  sortOrder?: number;
  createdAt: string;
  updatedAt: string;
}

export interface Product {
  id: number;
  name: string;
  subtitle?: string;
  description?: string;
  categoryId: number;
  price: number;
  stock: number;
  imageUrl: string;
  /** 商品图册：JSON 数组字符串（多张图片 URL），首图为封面。用 parseImages 解析 */
  images?: string;
  glazeColor?: string;
  material?: string;
  size?: string;
  customizable: boolean;
  status: 'active' | 'inactive';
  createdAt: string;
  updatedAt: string;
  /** 已售件数：列表接口按有效订单实时聚合返回；详情接口用 salesCount */
  sales?: number;
}

export interface ProductDetailResponse {
  product: Product;
  salesCount: number;
  reviews: Review[];
  averageRating: number;
  reviewCount: number;
  canReview: boolean;
}

export interface CartItem {
  id: number;
  userId?: number;
  productId: number;
  quantity: number;
  productName?: string;
  price?: number;
  imageUrl?: string;
  selectedOptions?: any;
  productStatus?: string;
  stock?: number;
  createdAt: string;
  updatedAt: string;
}

export interface Customization {
  id: number;
  userId?: number;
  productId: number;
  productName?: string;
  designSpecifications: string;
  designImageUrl?: string;
  finishedProductUrl?: string;
  price: number;
  quotedPrice?: number;
  depositAmount?: number;
  depositPaid?: boolean;
  finalAmount?: number;
  finalPaid?: boolean;
  finalPayTime?: string;
  shape: string;
  glazeColor: string;
  pattern: string;
  inscription: string;
  engravingText?: string;
  material?: string;
  size: string;
  quantity: number;
  budget: number;
  contactName: string;
  contactPhone: string;
  shippingAddress?: string;
  requirement: string;
  status: 'pending' | 'approved' | 'rejected' | 'in_progress' | 'completed' | 'cancelled' | 'PENDING' | 'QUOTED' | 'CONFIRMED' | 'IN_PROGRESS' | 'QUALITY_CHECK' | 'COMPLETED' | 'CANCELLED' | 'REJECTED' | 'QUOTE_CONFIRMED';
  notes?: string;
  timelineNote?: string;
  expectedCompleteDate?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CustomizationProgress {
  id: number;
  customizationId: number;
  stage: string;
  description: string;
  imageUrl?: string;
  operatorId?: number;
  operatorRole?: string;
  fromStatus?: string;
  toStatus?: string;
  createdAt: string;
}

export interface CustomOrder {
  id: number;
  userId: number;
  productId: number;
  designSpecifications: any;
  price: number;
  status: 'pending' | 'in_progress' | 'completed' | 'cancelled';
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Order {
  id: number;
  orderNo?: string;
  userId?: number;
  totalAmount: number;
  status: 'PENDING_PAY' | 'paid' | 'shipped' | 'delivered' | 'cancelled' | 'PAID' | 'SHIPPED' | 'COMPLETED' | 'CANCELLED';
  payType?: string;
  receiverName?: string;
  receiverPhone?: string;
  receiverAddress?: string;
  remark?: string;
  createdAt: string;
  payTime?: string;
  shippingCompany?: string;
  trackingNo?: string;
  shipTime?: string;
  updatedAt: string;
}

export interface OrderItem {
  id: number;
  orderId: number;
  productId: number;
  productName: string;
  imageUrl?: string;
  unitPrice: number;
  quantity: number;
  subtotal: number;
  createdAt: string;
}

export interface Payment {
  id: number;
  paymentNo: string;
  bizType: 'ORDER' | 'CUSTOM_DEPOSIT' | 'CUSTOM_BALANCE';
  bizId: number;
  userId?: number;
  amount: number;
  channel?: 'alipay' | 'wechat' | 'bank';
  status: 'PENDING' | 'SUCCESS' | 'FAILED' | 'REFUNDED';
  transactionId?: string;
  refundNo?: string;
  refundAmount?: number;
  createdAt?: string;
  paidAt?: string;
  refundedAt?: string;
}

export interface Notification {
  id: number;
  userId?: number;
  type: 'ORDER' | 'CUSTOMIZATION' | 'RETURN' | 'SYSTEM';
  title: string;
  content?: string;
  bizType?: string;
  bizId?: number;
  isRead?: boolean;
  createdAt: string;
  readAt?: string;
}

export interface Review {
  id: number;
  productId: number;
  productName?: string;
  userId?: number;
  username?: string;
  rating: number;
  title: string;
  content: string;
  images?: string[];
  helpfulCount: number;
  reportedCount: number;
  status: 'pending' | 'approved' | 'rejected' | 'hidden' | 'VISIBLE' | 'HIDDEN' | 'REJECTED';
  reply?: string;
  replyTime?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ReviewReply {
  id: number;
  reviewId: number;
  userId: number;
  content: string;
  isSellerReply: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ReviewHelpfulness {
  reviewId: number;
  userId: number;
  isHelpful: boolean;
  createdAt: string;
}

export interface ChatMessage {
  id: number;
  conversationId: number;
  senderId: number;
  senderRole: 'customer' | 'service' | 'admin' | 'ai' | 'system';
  message: string;
  type: 'text' | 'image' | 'file' | 'system';
  timestamp: string;
  createdAt?: string;
  readBySupport?: boolean;
  readByCustomer?: boolean;
}

export interface ChatConversation {
  id: number;
  customerId: number;
  customerName?: string;
  supportAgentId?: number;
  status: 'active' | 'resolved' | 'open' | 'closed' | 'archived' | 'pending_human';
  subject?: string;
  priority: 'low' | 'medium' | 'high' | 'urgent';
  unreadCount: number;
  lastMessage?: string;
  lastMessageTime?: string;
  createdAt: string;
  updatedAt: string;
  transferredToHuman?: boolean;
  aiSummary?: string;
}

export interface AiChatResponse {
  userMessage: ChatMessage;
  assistantMessage: ChatMessage | null;
  transferredToHuman: boolean;
  suggestTransfer?: boolean;
}

export interface SupportTicket {
  id: number;
  userId?: number;
  customerName?: string;
  orderId?: number;
  type?: string;
  title: string;
  content: string;
  contactName?: string;
  contactPhone?: string;
  priority: 'low' | 'medium' | 'high';
  status: 'open' | 'replied' | 'in_progress' | 'resolved' | 'closed' | 'pending';
  reply?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ReturnRequest {
  id: number;
  orderId: number;
  orderItemId?: number;
  userId?: number;
  productId: number;
  productName: string;
  imageUrl?: string;
  unitPrice: number;
  quantity: number;
  type?: 'refund' | 'return' | 'refund_only' | 'refund_and_return' | 'exchange';
  returnType: 'refund_only' | 'refund_and_return' | 'exchange';
  reason: string;
  description?: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'RETURNING' | 'RECEIVED' | 'REFUNDING' | 'REFUNDED' | 'CLOSED' | 'pending' | 'approved' | 'rejected' | 'returning' | 'completed' | 'refunded' | 'shipped_back' | 'received';
  rejectReason?: string;
  refundAmount?: number;
  amount?: number;
  trackingNo?: string;
  evidenceImages?: string;
  returnAddress?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ReturnRequestLog {
  id: number;
  returnRequestId: number;
  fromStatus?: string;
  toStatus: string;
  operatorId?: number;
  operatorRole?: string;
  note?: string;
  createdAt: string;
}

export interface RecommendationRule {
  id: number;
  name: string;
  type: 'popularity' | 'similarity' | 'category' | 'personalized' | 'trending';
  criteria: {
    categoryIds?: number[];
    minRating?: number;
    minSales?: number;
    timeRange?: string;
    excludeProductIds?: number[];
  };
  weight: number;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UserBehavior {
  id: number;
  userId: number;
  productId: number;
  action: 'view' | 'add_to_cart' | 'purchase' | 'favorite' | 'review';
  timestamp: string;
  sessionDuration?: number;
  referrer?: string;
}

export interface UserPreference {
  id: number;
  userId: number;
  categoryId?: number;
  preferredPriceMin?: number;
  preferredPriceMax?: number;
  preferredMaterial?: string;
  preferredGlazeColor?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Recommendation {
  id: number;
  userId: number;
  productId: number;
  reason: 'popular' | 'similar' | 'category_match' | 'personalized' | 'trending';
  score: number;
  createdAt: string;
  updatedAt: string;
}

export interface DatePoint {
  date: string;
  value: number;
}

export interface NameValue {
  name: string;
  value: number;
}

export interface RestockSuggestion {
  productId: number;
  name: string;
  imageUrl?: string;
  stock: number;
  sold30: number;
  suggestedRestock: number;
  daysLeft: number | null;
  level: 'OUT' | 'URGENT' | 'WARNING';
  reason: string;
}

export interface AdminStats {
  orderCount7d: DatePoint[];
  salesAmount7d: DatePoint[];
  totalUsers: number;
  totalProducts: number;
  pendingPayOrders: number;
  pendingReturns: number;
  pendingCustomizations: number;
  lowStockProducts: number;
  orderStatusDistribution: NameValue[];
  topProducts: NameValue[];
  lowStockList: Product[];
}

export interface ShopSettings {
  id?: number;
  shopName?: string;
  contactName?: string;
  contactPhone?: string;
  address?: string;
  updatedAt?: string;
}

export interface LogisticsTrace {
  time: string;
  status: string;
  description: string;
}

export interface SeckillActivity {
  id: number;
  productId: number;
  seckillPrice: number;
  totalStock: number;
  availableStock: number;
  startTime: string;
  endTime: string;
  status: string;
  createdAt?: string;
  productName?: string;
  productImage?: string;
  originalPrice?: number;
}

export interface SeckillOrder {
  id: number;
  activityId: number;
  userId: number;
  productId: number;
  quantity: number;
  seckillPrice: number;
  payAmount: number;
  status: string;
  orderNo: string;
  createdAt?: string;
  productName?: string;
  productImage?: string;
}
