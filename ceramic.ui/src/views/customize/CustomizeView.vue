<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { api } from '@/services/api'
import type { Product, Customization } from '@/types'

const cartStore = useCartStore()
const userStore = useUserStore()
const router = useRouter()
const route = useRoute()

// 从商品详情页「我想定制」跳转带入的商品 ID（可选），提交时关联回源商品
const sourceProductId = ref<number | undefined>(
  route.query.productId ? Number(route.query.productId) || undefined : undefined
)

const customizationOptions = {
  shapeType: [
    { value: 'teacup', label: '茶杯', description: '小巧精致，适合品茗', basePrice: 199 },
    { value: 'vase', label: '花瓶', description: '优雅造型，插花佳品', basePrice: 399 },
    { value: 'bowl', label: '碗', description: '日用实用，质朴温润', basePrice: 159 },
    { value: 'plate', label: '盘', description: '器型多样，餐桌搭配', basePrice: 179 },
    { value: 'tableware_set', label: '餐具套装', description: '成套搭配，宴客首选', basePrice: 599 },
    { value: 'ornament', label: '摆件', description: '艺术装饰，彰显品味', basePrice: 299 }
  ],
  baseMaterial: [
    { value: 'kaolin', label: '高岭土', description: '质地细腻，适合精细工艺品' },
    { value: 'porcelain_clay', label: '瓷土', description: '硬度高，适合日用陶瓷' },
    { value: 'stoneware_clay', label: '陶土', description: '质感厚重，适合装饰品' }
  ],
  glazeType: [
    { value: 'celadon', label: '青釉', description: '温润如玉，古典雅致' },
    { value: 'white_glaze', label: '白釉', description: '纯净素雅，现代简约' },
    { value: 'blue_and_white', label: '青花', description: '传统图案，经典中国风' },
    { value: 'crystalline_glaze', label: '结晶釉', description: '晶莹剔透，艺术感强' }
  ],
  sizeOptions: [
    { value: 'small', label: '小号', dimensions: '直径10-15cm', priceMultiplier: 0.8 },
    { value: 'medium', label: '中号', dimensions: '直径16-25cm', priceMultiplier: 1.0 },
    { value: 'large', label: '大号', dimensions: '直径26-35cm', priceMultiplier: 1.3 }
  ]
}

// 各选择区选项映射，便于按 key 反查标签
const optionMap = {
  shapeType: customizationOptions.shapeType,
  baseMaterial: customizationOptions.baseMaterial,
  glazeType: customizationOptions.glazeType,
  size: customizationOptions.sizeOptions
}

const currentDesign = reactive({
  shapeType: '',
  baseMaterial: '',
  glazeType: '',
  size: '',
  customText: '',
  customPattern: '',
  additionalNotes: ''
})

// 当某项选择「自定义」时，顾客手填的风格描述（预设之外的需求）
const customValues = reactive({
  shapeType: '',
  baseMaterial: '',
  glazeType: '',
  size: ''
})
type CustomKey = keyof typeof customValues

// 四个必选项的中文标签，供确认卡与校验复用
const customKeyLabels: Record<CustomKey, string> = {
  shapeType: '器型',
  baseMaterial: '材质',
  glazeType: '釉色',
  size: '尺寸'
}

// 选了「自定义」却没填内容的项（用于拦截提交/加购）
const customBlanks = computed(() =>
  (Object.keys(customValues) as CustomKey[])
    .filter(k => currentDesign[k] === 'custom' && !customValues[k].trim())
    .map(k => customKeyLabels[k])
)

// 将某一项解析为最终文案：选了「自定义」用手填内容，否则用预设标签
const resolveLabel = (key: CustomKey): string => {
  if (currentDesign[key] === 'custom') return customValues[key].trim()
  const opt = optionMap[key].find(o => o.value === currentDesign[key])
  return opt ? opt.label : ''
}

// 提交/入购物车时，把 'custom' 占位替换为顾客手填的真实文案
const resolvedDesign = computed(() => ({
  ...currentDesign,
  shapeType: currentDesign.shapeType === 'custom' ? customValues.shapeType.trim() : currentDesign.shapeType,
  baseMaterial: currentDesign.baseMaterial === 'custom' ? customValues.baseMaterial.trim() : currentDesign.baseMaterial,
  glazeType: currentDesign.glazeType === 'custom' ? customValues.glazeType.trim() : currentDesign.glazeType,
  size: currentDesign.size === 'custom' ? customValues.size.trim() : currentDesign.size
}))

const contactInfo = reactive({
  name: '',
  phone: '',
  shippingAddress: ''
})

// 定制数量（成品可批量定制），预估总价 = 单价 × 数量
const quantity = ref(1)

const designImageUrl = ref('')
const imageUploading = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)
const calculatedPrice = ref(299.00)
const loading = ref(false)

// 预估总价：单价 × 数量
const totalPrice = computed(() => parseFloat((calculatedPrice.value * quantity.value).toFixed(2)))

// 已选规格清单，构成「需求确认卡」主体，随每次选择实时刷新
const selectedSummary = computed(() => {
  const items: { label: string; value: string }[] = []
  const shape = resolveLabel('shapeType')
  if (shape) items.push({ label: '器型', value: shape })
  const mat = resolveLabel('baseMaterial')
  if (mat) items.push({ label: '材质', value: mat })
  const glaze = resolveLabel('glazeType')
  if (glaze) items.push({ label: '釉色', value: glaze })
  const size = resolveLabel('size')
  if (size) items.push({ label: '尺寸', value: size })
  if (currentDesign.customText) items.push({ label: '刻字', value: currentDesign.customText })
  if (currentDesign.customPattern) items.push({ label: '图案', value: currentDesign.customPattern })
  return items
})

// 必选项缺失提示：引导顾客补全，卡片因此对每次点击都有反馈
const requiredMissing = computed(() => {
  const missing: string[] = []
  const check = (key: CustomKey) => {
    const v = currentDesign[key]
    if (!v) missing.push(customKeyLabels[key])
    else if (v === 'custom' && !customValues[key].trim()) missing.push(`${customKeyLabels[key]}（待填写）`)
  }
  check('shapeType')
  check('baseMaterial')
  check('glazeType')
  check('size')
  return missing
})
const isReady = computed(() => requiredMissing.value.length === 0)

// 购物车/订单快照封面：优先用顾客上传的参考图，否则用器型示意图
const shapeImageMap: Record<string, string> = {
  teacup: '/images/products/1.jpg',
  vase: '/images/products/2.jpg',
  bowl: '/images/products/3.jpg',
  plate: '/images/products/4.jpg',
  tableware_set: '/images/products/5.jpg',
  ornament: '/images/products/6.jpg'
}
const snapshotImage = computed(() =>
  designImageUrl.value || shapeImageMap[currentDesign.shapeType] || '/images/products/3.jpg'
)

const decrementQuantity = () => {
  if (quantity.value > 1) quantity.value--
}
const incrementQuantity = () => {
  if (quantity.value < 999) quantity.value++
}
const normalizeQuantity = () => {
  if (!Number.isFinite(quantity.value) || quantity.value < 1) quantity.value = 1
  else quantity.value = Math.floor(quantity.value)
  if (quantity.value > 999) quantity.value = 999
}

const updatePrice = () => {
  let basePrice = 299.00
  const shapeOption = customizationOptions.shapeType.find(s => s.value === currentDesign.shapeType)
  if (shapeOption) {
    basePrice = shapeOption.basePrice
  }
  const sizeOption = customizationOptions.sizeOptions.find(s => s.value === currentDesign.size)
  if (sizeOption) {
    basePrice *= sizeOption.priceMultiplier
  }
  if (currentDesign.customText) basePrice += 50
  if (currentDesign.customPattern) basePrice += 100
  calculatedPrice.value = parseFloat(basePrice.toFixed(2))
}

const selectOption = (category: keyof typeof currentDesign, value: string) => {
  currentDesign[category] = value
  updatePrice()
}

const triggerFileInput = () => {
  fileInputRef.value?.click()
}

const handleImageUpload = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  imageUploading.value = true
  try {
    const url = await api.uploadImage(file)
    designImageUrl.value = url
  } catch (error) {
    alert(`图片上传失败：${error instanceof Error ? error.message : '未知错误'}`)
  } finally {
    imageUploading.value = false
    target.value = ''
  }
}

const removeImage = () => {
  designImageUrl.value = ''
}

const addToCart = async () => {
  if (!userStore.isLoggedIn) {
    alert('请先登录')
    router.push('/login')
    return
  }
  if (!currentDesign.baseMaterial || !currentDesign.glazeType || !currentDesign.size) {
    alert('请选择所有必选项！')
    return
  }
  if (customBlanks.value.length) {
    alert(`请填写自定义内容：${customBlanks.value.join('、')}`)
    return
  }

  const design = resolvedDesign.value
  const customProduct: Product = {
    id: Date.now(),
    name: `定制陶瓷 - ${design.baseMaterial} ${design.size}`,
    categoryId: 4,
    price: calculatedPrice.value,
    description: '个性化定制陶瓷制品',
    material: design.baseMaterial,
    glazeColor: design.glazeType,
    size: design.size,
    stock: 1,
    imageUrl: snapshotImage.value,
    customizable: false,
    status: 'active',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString()
  }

  try {
    const success = await cartStore.addToCart(customProduct, quantity.value, JSON.stringify(design))
    if (success) {
      alert('定制方案已加入购物车！')
    } else {
      alert('添加购物车失败，请重试')
    }
  } catch (error) {
    alert(`添加购物车失败：${error instanceof Error ? error.message : '未知错误'}`)
  }
}

const submitCustomization = async () => {
  if (!userStore.isLoggedIn) {
    alert('请先登录')
    router.push('/login')
    return
  }
  if (!currentDesign.shapeType || !currentDesign.baseMaterial || !currentDesign.glazeType || !currentDesign.size) {
    alert('请选择所有必选项！')
    return
  }
  if (customBlanks.value.length) {
    alert(`请填写自定义内容：${customBlanks.value.join('、')}`)
    return
  }
  if (!contactInfo.name || !contactInfo.phone) {
    alert('请填写联系人信息！')
    return
  }
  if (!contactInfo.shippingAddress || !contactInfo.shippingAddress.trim()) {
    alert('请填写收货地址！')
    return
  }

  loading.value = true

  const design = resolvedDesign.value
  const customizationData: Partial<Customization> = {
    designSpecifications: JSON.stringify(design),
    designImageUrl: designImageUrl.value || undefined,
    productId: sourceProductId.value,
    price: calculatedPrice.value,
    shape: design.shapeType,
    glazeColor: design.glazeType,
    pattern: currentDesign.customPattern,
    inscription: currentDesign.customText,
    size: design.size,
    quantity: quantity.value,
    budget: totalPrice.value,
    contactName: contactInfo.name,
    contactPhone: contactInfo.phone,
    shippingAddress: contactInfo.shippingAddress.trim(),
    requirement: currentDesign.additionalNotes,
    status: 'PENDING'
  }

  try {
    await api.createCustomization(customizationData)
    alert('定制申请提交成功！可在"我的定制"中查看进度。')
    resetCustomization()
    router.push('/my-customizations')
  } catch (error) {
    alert(`提交失败：${error instanceof Error ? error.message : '未知错误'}`)
  } finally {
    loading.value = false
  }
}

const resetCustomization = () => {
  Object.keys(currentDesign).forEach(key => {
    currentDesign[key as keyof typeof currentDesign] = ''
  })
  ;(Object.keys(customValues) as CustomKey[]).forEach(key => {
    customValues[key] = ''
  })
  contactInfo.name = ''
  contactInfo.phone = ''
  contactInfo.shippingAddress = ''
  quantity.value = 1
  designImageUrl.value = ''
  calculatedPrice.value = 299.00
}
</script>

<template>
  <div class="customize-page">
    <div class="container">
      <h1>陶瓷定制中心</h1>
      <p class="subtitle">打造专属于您的独特陶瓷作品</p>

      <div class="customize-container">
        <div class="design-preview">
          <h2>定制需求确认</h2>

          <!-- 参考图：顾客上传的设计图是唯一真实可视锚点 -->
          <div class="ref-image-box">
            <img v-if="designImageUrl" :src="designImageUrl" alt="参考图" class="ref-image" />
            <div v-else class="ref-image-empty">
              <div class="ref-icon">🖼️</div>
              <p>可在右侧「个性化定制」上传<br />设计图 / 参考图</p>
            </div>
          </div>

          <!-- 完成度引导 -->
          <div class="ready-hint" :class="{ ok: isReady }">
            <template v-if="isReady">✓ 必选项已齐全，可提交定制申请</template>
            <template v-else>待选择：{{ requiredMissing.join('、') }}</template>
          </div>

          <!-- 规格清单：随每次选择实时刷新 -->
          <div class="spec-sheet">
            <h4>规格清单</h4>
            <ul v-if="selectedSummary.length">
              <li v-for="item in selectedSummary" :key="item.label">
                <span class="spec-label">{{ item.label }}</span>
                <span class="spec-value">{{ item.value }}</span>
              </li>
              <li>
                <span class="spec-label">数量</span>
                <span class="spec-value">{{ quantity }} 件</span>
              </li>
            </ul>
            <p v-else class="spec-empty">
              请从右侧选择器型、材质、釉色与尺寸，规格将在此汇总。
            </p>
          </div>

          <!-- 价格 -->
          <div class="price-display">
            <h3>预估价格</h3>
            <div class="price">¥{{ calculatedPrice.toFixed(2) }}</div>
            <div v-if="quantity > 1" class="price-total">
              × {{ quantity }} 件 = <strong>¥{{ totalPrice.toFixed(2) }}</strong>
            </div>
            <p class="price-note">预估价仅供参考，最终价格以商家报价为准</p>
          </div>
        </div>

        <div class="customization-options">
          <div class="option-group">
            <h3 class="ui-section-title">器型选择</h3>
            <div class="options-grid">
              <div
                v-for="option in customizationOptions.shapeType"
                :key="option.value"
                class="option-card"
                :class="{ selected: currentDesign.shapeType === option.value }"
                @click="selectOption('shapeType', option.value)"
              >
                <div class="option-header">
                  <h4>{{ option.label }}</h4>
                  <span class="price-multiplier">¥{{ option.basePrice }}</span>
                </div>
                <p class="option-description">{{ option.description }}</p>
              </div>
              <div
                class="option-card option-card-custom"
                :class="{ selected: currentDesign.shapeType === 'custom' }"
                @click="selectOption('shapeType', 'custom')"
              >
                <div class="option-header">
                  <h4>自定义</h4>
                  <span class="price-multiplier neutral">面议</span>
                </div>
                <p class="option-description">预设之外的器型，写下你的想法</p>
              </div>
            </div>
            <div v-if="currentDesign.shapeType === 'custom'" class="custom-fill">
              <input
                type="text"
                v-model="customValues.shapeType"
                placeholder="描述你想要的器型，如：束口花器 / 异形茶宠"
              />
            </div>
          </div>

          <div class="option-group">
            <h3 class="ui-section-title">基础材质</h3>
            <div class="options-grid">
              <div
                v-for="option in customizationOptions.baseMaterial"
                :key="option.value"
                class="option-card"
                :class="{ selected: currentDesign.baseMaterial === option.value }"
                @click="selectOption('baseMaterial', option.value)"
              >
                <div class="option-header">
                  <h4>{{ option.label }}</h4>
                </div>
                <p class="option-description">{{ option.description }}</p>
              </div>
              <div
                class="option-card option-card-custom"
                :class="{ selected: currentDesign.baseMaterial === 'custom' }"
                @click="selectOption('baseMaterial', 'custom')"
              >
                <div class="option-header">
                  <h4>自定义</h4>
                </div>
                <p class="option-description">指定其他泥料或配比</p>
              </div>
            </div>
            <div v-if="currentDesign.baseMaterial === 'custom'" class="custom-fill">
              <input
                type="text"
                v-model="customValues.baseMaterial"
                placeholder="填写你想要的材质，如：紫砂 / 骨瓷"
              />
            </div>
          </div>

          <div class="option-group">
            <h3 class="ui-section-title">釉色选择</h3>
            <div class="options-grid">
              <div
                v-for="option in customizationOptions.glazeType"
                :key="option.value"
                class="option-card"
                :class="{ selected: currentDesign.glazeType === option.value }"
                @click="selectOption('glazeType', option.value)"
              >
                <div class="option-header">
                  <h4>{{ option.label }}</h4>
                </div>
                <p class="option-description">{{ option.description }}</p>
              </div>
              <div
                class="option-card option-card-custom"
                :class="{ selected: currentDesign.glazeType === 'custom' }"
                @click="selectOption('glazeType', 'custom')"
              >
                <div class="option-header">
                  <h4>自定义</h4>
                </div>
                <p class="option-description">指定预设之外的釉色</p>
              </div>
            </div>
            <div v-if="currentDesign.glazeType === 'custom'" class="custom-fill">
              <input
                type="text"
                v-model="customValues.glazeType"
                placeholder="填写你想要的釉色，如：天青 / 窑变红"
              />
            </div>
          </div>

          <div class="option-group">
            <h3 class="ui-section-title">尺寸规格</h3>
            <div class="options-grid">
              <div
                v-for="option in customizationOptions.sizeOptions"
                :key="option.value"
                class="option-card"
                :class="{ selected: currentDesign.size === option.value }"
                @click="selectOption('size', option.value)"
              >
                <div class="option-header">
                  <h4>{{ option.label }}</h4>
                  <span class="price-multiplier" :class="{ neutral: option.priceMultiplier === 1.0 }">
                  {{ option.priceMultiplier === 1.0 ? '基准价' : (option.priceMultiplier > 1.0 ? '+' : '') + (option.priceMultiplier * 100 - 100).toFixed(0) + '%' }}
                </span>
                </div>
                <p class="option-description">{{ option.dimensions }}</p>
              </div>
              <div
                class="option-card option-card-custom"
                :class="{ selected: currentDesign.size === 'custom' }"
                @click="selectOption('size', 'custom')"
              >
                <div class="option-header">
                  <h4>自定义</h4>
                  <span class="price-multiplier neutral">面议</span>
                </div>
                <p class="option-description">按你的实际尺寸定制</p>
              </div>
            </div>
            <div v-if="currentDesign.size === 'custom'" class="custom-fill">
              <input
                type="text"
                v-model="customValues.size"
                placeholder="填写具体尺寸，如：口径 18cm、高 22cm"
              />
            </div>
          </div>

          <div class="option-group">
            <h3 class="ui-section-title">个性化定制</h3>
            <div class="custom-inputs">
              <div class="input-group">
                <label>刻字内容</label>
                <input
                  type="text"
                  v-model="currentDesign.customText"
                  placeholder="请输入您想要刻的文字内容"
                  @input="updatePrice"
                />
              </div>

              <div class="input-group">
                <label>图案描述</label>
                <textarea
                  v-model="currentDesign.customPattern"
                  placeholder="描述您想要的图案样式..."
                  rows="3"
                  @input="updatePrice"
                ></textarea>
              </div>

              <div class="input-group">
                <label>设计图/参考图上传</label>
                <input
                  ref="fileInputRef"
                  type="file"
                  accept="image/*"
                  class="file-input-hidden"
                  @change="handleImageUpload"
                />
                <div
                  v-if="!designImageUrl"
                  class="upload-area"
                  :class="{ uploading: imageUploading }"
                  @click="triggerFileInput"
                >
                  <div v-if="imageUploading" class="upload-text">上传中...</div>
                  <template v-else>
                    <div class="upload-icon">+</div>
                    <div class="upload-text">点击上传设计图或参考图</div>
                    <div class="upload-hint">支持 JPG / PNG 等图片格式</div>
                  </template>
                </div>
                <div v-else class="image-preview-wrapper">
                  <img
                    :src="designImageUrl"
                    alt="设计参考图"
                    class="image-preview"
                  />
                  <button
                    type="button"
                    class="remove-image-btn"
                    @click="removeImage"
                  >×</button>
                </div>
              </div>

              <div class="input-group">
                <label>其他要求</label>
                <textarea
                  v-model="currentDesign.additionalNotes"
                  placeholder="如特殊尺寸、颜色偏好等..."
                  rows="2"
                ></textarea>
              </div>
            </div>
          </div>

          <div class="option-group">
            <h3 class="ui-section-title">数量与配送</h3>
            <div class="custom-inputs">
              <div class="input-group">
                <label>定制数量</label>
                <div class="quantity-controls">
                  <button type="button" @click="decrementQuantity" :disabled="quantity <= 1">-</button>
                  <input
                    type="number"
                    v-model.number="quantity"
                    min="1"
                    max="999"
                    @change="normalizeQuantity"
                  />
                  <button type="button" @click="incrementQuantity" :disabled="quantity >= 999">+</button>
                </div>
              </div>

              <div class="input-group">
                <label>收货地址</label>
                <textarea
                  v-model="contactInfo.shippingAddress"
                  placeholder="请填写详细收货地址（省/市/区 + 街道门牌），成品完成后据此发货"
                  rows="2"
                ></textarea>
              </div>
            </div>
          </div>

          <div class="option-group">
            <h3 class="ui-section-title">联系人信息</h3>
            <div class="custom-inputs">
              <div class="input-group">
                <label>联系人姓名</label>
                <input
                  type="text"
                  v-model="contactInfo.name"
                  placeholder="请输入联系人姓名"
                />
              </div>

              <div class="input-group">
                <label>联系电话</label>
                <input
                  type="tel"
                  v-model="contactInfo.phone"
                  placeholder="请输入联系电话"
                />
              </div>
            </div>
          </div>

          <div class="action-buttons">
            <button class="btn btn-secondary" @click="resetCustomization">
              重新定制
            </button>
            <button class="btn btn-success" @click="submitCustomization" :disabled="loading">
              {{ loading ? '提交中...' : '提交定制申请' }}
            </button>
            <button class="btn btn-primary" @click="addToCart">
              加入购物车
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.customize-page {
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
  margin-bottom: var(--sp-2);
  color: var(--c-text);
}

.subtitle {
  text-align: center;
  color: var(--c-text-muted);
  margin-bottom: var(--sp-6);
}

.customize-container {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: var(--sp-5);
}

.design-preview {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  padding: var(--sp-5);
  box-shadow: var(--shadow);
  height: fit-content;
  position: sticky;
  top: 80px;
}

.ref-image-box {
  width: 100%;
  height: 220px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  overflow: hidden;
  margin-bottom: var(--sp-4);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--c-bg);
}

.ref-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.ref-image-empty {
  text-align: center;
  color: var(--c-text-muted);
  padding: var(--sp-4);
}

.ref-icon {
  font-size: 2.2rem;
  margin-bottom: var(--sp-2);
  opacity: 0.7;
}

.ref-image-empty p {
  margin: 0;
  font-size: 0.85rem;
  line-height: 1.5;
}

.ready-hint {
  padding: var(--sp-2) var(--sp-3);
  border-radius: var(--radius-sm);
  font-size: 0.85rem;
  margin-bottom: var(--sp-4);
  background: var(--c-primary-soft);
  color: var(--c-text-muted);
  text-align: center;
}

.ready-hint.ok {
  background: rgba(16, 185, 129, 0.12);
  color: #0f9d6f;
  font-weight: 600;
}

.spec-sheet {
  margin-bottom: var(--sp-4);
  padding: var(--sp-3) var(--sp-4);
  background: var(--c-bg);
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
}

.spec-sheet h4 {
  margin: 0 0 var(--sp-3) 0;
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--c-text-muted);
}

.spec-sheet ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.spec-sheet li {
  display: flex;
  justify-content: space-between;
  gap: var(--sp-3);
  font-size: 0.92rem;
  padding-bottom: var(--sp-2);
  border-bottom: 1px dashed var(--c-border);
}

.spec-sheet li:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.spec-label {
  color: var(--c-text-muted);
  flex-shrink: 0;
}

.spec-value {
  font-weight: 600;
  color: var(--c-text);
  text-align: right;
  word-break: break-word;
}

.spec-empty {
  margin: 0;
  font-size: 0.85rem;
  color: var(--c-text-muted);
  text-align: center;
  line-height: 1.5;
}

.price-display {
  text-align: center;
  padding: var(--sp-4);
  background: var(--c-primary-soft);
  border-radius: var(--radius);
}

.price-display h3 {
  margin: 0 0 var(--sp-2) 0;
  color: var(--c-text-muted);
}

.price {
  font-size: 2rem;
  font-weight: bold;
  color: var(--c-accent);
}

.price-total {
  margin-top: var(--sp-2);
  color: var(--c-text-muted);
  font-size: 0.95rem;
}

.price-total strong {
  color: var(--c-accent);
  font-size: 1.15rem;
}

.price-note {
  margin: var(--sp-3) 0 0 0;
  font-size: 0.78rem;
  color: var(--c-text-muted);
  line-height: 1.4;
}

.price-multiplier.neutral {
  background: var(--c-text-muted);
}

.quantity-controls {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--c-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
  width: fit-content;
}

.quantity-controls button {
  width: 40px;
  height: 40px;
  border: none;
  background: var(--c-primary-soft);
  color: var(--c-primary-dark);
  cursor: pointer;
  font-size: 1.2rem;
  transition: background-color 0.2s;
}

.quantity-controls button:hover:not(:disabled) {
  background: var(--c-primary);
  color: #fff;
}

.quantity-controls button:disabled {
  background: var(--c-bg);
  color: #ccc;
  cursor: not-allowed;
}

.quantity-controls input {
  width: 64px;
  height: 40px;
  border: none;
  text-align: center;
  border-left: 1px solid var(--c-border);
  border-right: 1px solid var(--c-border);
  color: var(--c-text);
  background: var(--c-surface);
  font-size: 1rem;
}

.customization-options {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  padding: var(--sp-5);
  box-shadow: var(--shadow);
}

.option-group {
  margin-bottom: var(--sp-6);
}

.option-group h3 {
  text-align: left;
  margin-bottom: var(--sp-4);
  color: var(--c-text);
  padding-bottom: var(--sp-2);
  border-bottom: 1px solid var(--c-border);
}

.options-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--sp-4);
}

.option-card {
  border: 2px solid var(--c-border);
  border-radius: var(--radius);
  padding: var(--sp-4);
  cursor: pointer;
  transition: all 0.2s;
  background: var(--c-surface);
}

.option-card:hover {
  border-color: var(--c-primary);
  transform: translateY(-2px);
  box-shadow: var(--shadow);
}

.option-card.selected {
  border-color: var(--c-primary);
  background: var(--c-primary-soft);
}

/* 「自定义」卡：虚线描边区分于预设项 */
.option-card-custom {
  border-style: dashed;
  border-color: var(--c-gold);
}

.option-card-custom.selected {
  border-style: solid;
  border-color: var(--c-primary);
  background: var(--c-primary-soft);
}

/* 选中「自定义」后展开的手填输入 */
.custom-fill {
  margin-top: var(--sp-3);
}

.custom-fill input {
  width: 100%;
  padding: var(--sp-3);
  border: 1px solid var(--c-primary);
  border-radius: var(--radius-sm);
  font-size: 1rem;
  font-family: inherit;
  color: var(--c-text);
  background: var(--c-surface);
  box-sizing: border-box;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.custom-fill input:focus {
  outline: none;
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px var(--c-primary-soft);
}

.custom-fill input::placeholder {
  color: var(--c-text-muted);
}

.option-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--sp-2);
}

.option-header h4 {
  margin: 0;
  color: var(--c-text);
}

.price-multiplier {
  background: var(--c-primary);
  color: white;
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  font-size: 0.8rem;
}

.option-description {
  margin: 0;
  color: var(--c-text-muted);
  font-size: 0.9rem;
}

.custom-inputs {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.input-group label {
  font-weight: 600;
  color: var(--c-text);
}

.input-group input,
.input-group textarea {
  padding: var(--sp-3);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-sm);
  font-size: 1rem;
  font-family: inherit;
  color: var(--c-text);
  background: var(--c-surface);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.input-group input::placeholder,
.input-group textarea::placeholder {
  color: var(--c-text-muted);
}

.input-group input:focus,
.input-group textarea:focus {
  outline: none;
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px var(--c-primary-soft);
}

.file-input-hidden {
  display: none;
}

.upload-area {
  border: 2px dashed var(--c-primary);
  border-radius: var(--radius);
  padding: var(--sp-6) var(--sp-4);
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
  background: var(--c-primary-soft);
  color: var(--c-primary-dark);
}

.upload-area:hover {
  background: var(--c-primary-soft);
  border-color: var(--c-primary-dark);
}

.upload-area.uploading {
  cursor: progress;
  opacity: 0.7;
}

.upload-icon {
  font-size: 2.5rem;
  font-weight: bold;
  line-height: 1;
  margin-bottom: var(--sp-2);
}

.upload-text {
  font-size: 1rem;
  font-weight: bold;
}

.upload-hint {
  font-size: 0.85rem;
  color: var(--c-text-muted);
  margin-top: var(--sp-1);
}

.image-preview-wrapper {
  position: relative;
  width: 100%;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--c-bg);
}

.image-preview {
  display: block;
  width: 100%;
  max-height: 320px;
  object-fit: contain;
}

.remove-image-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: white;
  font-size: 1.2rem;
  line-height: 1;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.2s;
}

.remove-image-btn:hover {
  background: var(--c-accent);
}

.action-buttons {
  display: flex;
  gap: var(--sp-4);
  margin-top: var(--sp-6);
}

.btn {
  flex: 1;
  padding: var(--sp-3) var(--sp-5);
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  text-decoration: none;
  display: inline-block;
  text-align: center;
  font-size: 1rem;
  transition: background-color 0.2s;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-primary {
  background: var(--c-primary);
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: var(--c-primary-dark);
}

.btn-secondary {
  background: var(--c-surface);
  color: var(--c-text);
  border: 1px solid var(--c-border);
}

.btn-secondary:hover:not(:disabled) {
  background: var(--c-bg);
}

.btn-success {
  background: var(--c-primary);
  color: white;
}

.btn-success:hover:not(:disabled) {
  background: var(--c-primary-dark);
}

@media (max-width: 768px) {
  .customize-container {
    grid-template-columns: 1fr;
  }

  .options-grid {
    grid-template-columns: 1fr;
  }

  .action-buttons {
    flex-direction: column;
  }
}
</style>
