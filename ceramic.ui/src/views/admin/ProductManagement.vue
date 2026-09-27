<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { api } from '@/services/api'
import type { Product, Category } from '@/types'
import { parseImages, stringifyImages } from '@/utils/images'
import AdminNavbar from '@/components/admin/AdminNavbar.vue'

const router = useRouter()
const userStore = useUserStore()
const products = ref<Product[]>([])
const categories = ref<Category[]>([])
const loading = ref(false)
const showModal = ref(false)
const isEditing = ref(false)
const searchKeyword = ref('')
const selectedCategory = ref<number | undefined>()

const form = ref<Partial<Product>>({
  name: '',
  subtitle: '',
  description: '',
  price: 0,
  stock: 0,
  imageUrl: '',
  glazeColor: '',
  material: '',
  size: '',
  customizable: false,
  status: 'active',
})

const imageUploading = ref(false)
// 商品图册：多张图片 URL，首图作为封面（imageUrl）。
const imageList = ref<string[]>([])

const handleImageUpload = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const files = target.files
  if (!files || !files.length) return

  imageUploading.value = true
  try {
    // 逐张上传，追加到图册（保留已上传的图片）
    for (const file of Array.from(files)) {
      const url = await api.uploadImage(file)
      imageList.value.push(url)
    }
  } catch (err) {
    console.error('图片上传失败:', err)
    alert('部分图片上传失败，请重试')
  } finally {
    imageUploading.value = false
    target.value = ''
  }
}

const removeImage = (index: number) => {
  imageList.value.splice(index, 1)
}

const moveImage = (index: number, dir: -1 | 1) => {
  const to = index + dir
  if (to < 0 || to >= imageList.value.length) return
  const arr = imageList.value
  const tmp = arr[index]!
  arr[index] = arr[to]!
  arr[to] = tmp
}

onMounted(() => {
  if (userStore.currentUser?.role !== 'admin') {
    router.push('/')
  }
  loadProducts()
  loadCategories()
})

const loadProducts = async () => {
  loading.value = true
  try {
    products.value = await api.adminProducts({ categoryId: selectedCategory.value, keyword: searchKeyword.value })
  } catch (err) {
    console.error('加载商品失败:', err)
  } finally {
    loading.value = false
  }
}

const loadCategories = async () => {
  try {
    categories.value = await api.categories()
  } catch (err) {
    console.error('加载分类失败:', err)
  }
}

const handleSearch = () => {
  loadProducts()
}

const handleAdd = () => {
  isEditing.value = false
  form.value = {
    name: '',
    subtitle: '',
    description: '',
    price: 0,
    stock: 0,
    imageUrl: '',
    glazeColor: '',
    material: '',
    size: '',
    customizable: false,
    status: 'active',
  }
  imageList.value = []
  showModal.value = true
}

const handleEdit = (product: Product) => {
  isEditing.value = true
  form.value = {
    id: product.id,
    categoryId: product.categoryId,
    name: product.name,
    subtitle: product.subtitle || '',
    description: product.description || '',
    price: product.price || 0,
    stock: product.stock || 0,
    imageUrl: product.imageUrl || '',
    glazeColor: product.glazeColor || '',
    material: product.material || '',
    size: product.size || '',
    customizable: product.customizable || false,
    status: product.status || 'active',
  }
  // 优先用图册；老数据只有封面 imageUrl 时回退为单图
  const gallery = parseImages(product.images)
  imageList.value = gallery.length ? gallery : (product.imageUrl ? [product.imageUrl] : [])
  showModal.value = true
}

const handleSave = async () => {
  try {
    // 首图作为封面，兼容仅读取 imageUrl 的列表/购物车/订单快照
    const payload: Partial<Product> = {
      ...form.value,
      images: stringifyImages(imageList.value),
      imageUrl: imageList.value[0] || '',
    }
    if (isEditing.value && form.value.id) {
      await api.adminProductUpdate(form.value.id, payload)
    } else {
      await api.adminProductCreate(payload)
    }
    showModal.value = false
    loadProducts()
  } catch (err) {
    console.error('保存商品失败:', err)
    alert('保存失败')
  }
}

const handleDelete = async (id: number) => {
  if (!confirm('确定要删除这个商品吗？')) return
  try {
    await api.adminProductDelete(id)
    loadProducts()
  } catch (err) {
    console.error('删除商品失败:', err)
    alert('删除失败')
  }
}

const handleToggleStatus = async (product: Product) => {
  const newStatus = product.status === 'active' ? 'inactive' : 'active'
  const actionLabel = newStatus === 'active' ? '上架' : '下架'
  if (!confirm(`确定要${actionLabel}商品「${product.name}」吗？`)) return
  try {
    // 只改状态：走专用接口，避免整行更新把其它字段清空
    await api.adminProductUpdateStatus(product.id, newStatus)
    loadProducts()
  } catch (err) {
    console.error('更新状态失败:', err)
    alert('操作失败')
  }
}

const getStatusLabel = (status: string) => {
  return status === 'active' ? '上架' : '下架'
}

const getStatusColor = (status: string) => {
  return status === 'active' ? '#10b981' : '#ef4444'
}
</script>

<template>
  <div class="admin-page">
    <AdminNavbar />
    
    <div class="page-header">
      <h1>商品管理</h1>
      <button class="btn-primary" @click="handleAdd">+ 添加商品</button>
    </div>

    <div class="search-bar">
      <input
        v-model="searchKeyword"
        type="text"
        placeholder="搜索商品名称..."
        @keyup.enter="handleSearch"
      />
      <select v-model="selectedCategory" @change="handleSearch">
        <option :value="undefined">全部分类</option>
        <option v-for="cat in categories" :key="cat.id" :value="cat.id">
          {{ cat.name }}
        </option>
      </select>
      <button class="btn-search" @click="handleSearch">搜索</button>
    </div>

    <div class="product-grid" v-if="!loading">
      <div class="product-card" :class="{ 'is-inactive': product.status !== 'active' }" v-for="product in products" :key="product.id">
        <div class="product-image" :style="{ backgroundImage: `url(${product.imageUrl})` }"></div>
        <div class="product-info">
          <h3>{{ product.name }}</h3>
          <p class="subtitle">{{ product.subtitle }}</p>
          <div class="product-meta">
            <span class="price">¥{{ product.price }}</span>
            <span class="stock">库存: {{ product.stock }}</span>
            <span class="status" :style="{ color: getStatusColor(product.status || '') }">
              {{ getStatusLabel(product.status || '') }}
            </span>
          </div>
          <div class="product-actions">
            <button class="btn-edit" @click="handleEdit(product)">编辑</button>
            <button class="btn-toggle" @click="handleToggleStatus(product)">
              {{ product.status === 'active' ? '下架' : '上架' }}
            </button>
            <button class="btn-delete" @click="handleDelete(product.id)">删除</button>
          </div>
        </div>
      </div>
    </div>

    <div class="loading" v-else>加载中...</div>

    <div class="modal-overlay" v-if="showModal" @click="showModal = false">
      <div class="modal-content" @click.stop>
        <h2>{{ isEditing ? '编辑商品' : '添加商品' }}</h2>
        <form @submit.prevent="handleSave">
          <div class="form-row">
            <div class="form-group">
              <label>商品名称 *</label>
              <input v-model="form.name" type="text" required />
            </div>
            <div class="form-group">
              <label>分类</label>
              <select v-model="form.categoryId">
                <option :value="undefined">请选择分类</option>
                <option v-for="cat in categories" :key="cat.id" :value="cat.id">
                  {{ cat.name }}
                </option>
              </select>
            </div>
          </div>
          <div class="form-group">
            <label>副标题</label>
            <input v-model="form.subtitle" type="text" />
          </div>
          <div class="form-group">
            <label>描述</label>
            <textarea v-model="form.description" rows="3"></textarea>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>价格 *</label>
              <input v-model.number="form.price" type="number" required min="0" step="0.01" />
            </div>
            <div class="form-group">
              <label>库存 *</label>
              <input v-model.number="form.stock" type="number" required min="0" />
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>釉色</label>
              <input v-model="form.glazeColor" type="text" />
            </div>
            <div class="form-group">
              <label>材质</label>
              <input v-model="form.material" type="text" />
            </div>
            <div class="form-group">
              <label>尺寸</label>
              <input v-model="form.size" type="text" />
            </div>
          </div>
          <div class="form-group">
            <label>商品图片（可上传多张，第一张为封面）</label>
            <div class="image-gallery-editor">
              <div
                v-for="(img, idx) in imageList"
                :key="img + idx"
                class="gallery-thumb"
                :class="{ cover: idx === 0 }"
              >
                <img :src="img" alt="商品图片" />
                <span v-if="idx === 0" class="cover-tag">封面</span>
                <div class="thumb-actions">
                  <button type="button" title="前移" :disabled="idx === 0" @click="moveImage(idx, -1)">‹</button>
                  <button type="button" title="删除" class="del" @click="removeImage(idx)">✕</button>
                  <button type="button" title="后移" :disabled="idx === imageList.length - 1" @click="moveImage(idx, 1)">›</button>
                </div>
              </div>
              <label class="upload-placeholder" :class="{ uploading: imageUploading }">
                <input type="file" accept="image/jpeg,image/png,image/gif,image/webp" multiple @change="handleImageUpload" hidden />
                <span v-if="imageUploading">上传中…</span>
                <span v-else>+ 添加图片</span>
              </label>
            </div>
          </div>
          <div class="form-group">
            <label>
              <input v-model="form.customizable" type="checkbox" />
              支持定制
            </label>
          </div>
          <div class="form-group">
            <label>状态</label>
            <select v-model="form.status">
              <option value="active">上架</option>
              <option value="inactive">下架</option>
            </select>
          </div>
          <div class="form-actions">
            <button type="button" class="btn-cancel" @click="showModal = false">取消</button>
            <button type="submit" class="btn-primary">保存</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-page {
  padding: 2rem;
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.page-header h1 {
  font-size: 1.8rem;
  color: #2c3e50;
}

.btn-primary {
  background: #3b82f6;
  color: white;
  border: none;
  padding: 0.75rem 1.5rem;
  border-radius: 6px;
  cursor: pointer;
  font-size: 1rem;
}

.btn-primary:hover {
  background: #2563eb;
}

.search-bar {
  display: flex;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.search-bar input,
.search-bar select {
  padding: 0.75rem;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 1rem;
}

.search-bar input {
  flex: 1;
  max-width: 300px;
}

.btn-search {
  background: #6b7280;
  color: white;
  border: none;
  padding: 0.75rem 1.5rem;
  border-radius: 6px;
  cursor: pointer;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 1.5rem;
}

.product-card {
  background: white;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

/* 已下架商品：整卡变灰，与上架商品区分（仍可编辑/重新上架/删除） */
.product-card.is-inactive {
  opacity: 0.6;
  background: #f3f4f6;
}

.product-image {
  height: 200px;
  background-size: cover;
  background-position: center;
}

.product-info {
  padding: 1rem;
}

.product-info h3 {
  margin: 0 0 0.5rem 0;
  font-size: 1.2rem;
  color: #1f2937;
}

.subtitle {
  margin: 0 0 1rem 0;
  color: #6b7280;
  font-size: 0.9rem;
}

.product-meta {
  display: flex;
  gap: 1rem;
  margin-bottom: 1rem;
  font-size: 0.9rem;
}

.price {
  color: #ef4444;
  font-weight: bold;
}

.stock {
  color: #6b7280;
}

.status {
  font-weight: bold;
}

.product-actions {
  display: flex;
  gap: 0.5rem;
}

.btn-edit,
.btn-toggle,
.btn-delete {
  flex: 1;
  padding: 0.5rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.85rem;
}

.btn-edit {
  background: #3b82f6;
  color: white;
}

.btn-toggle {
  background: #10b981;
  color: white;
}

.btn-delete {
  background: #ef4444;
  color: white;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: white;
  border-radius: 8px;
  padding: 2rem;
  width: 90%;
  max-width: 600px;
  max-height: 90vh;
  overflow-y: auto;
}

.modal-content h2 {
  margin: 0 0 1.5rem 0;
  font-size: 1.5rem;
}

.form-group {
  margin-bottom: 1rem;
}

.form-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
}

.form-group input,
.form-group select,
.form-group textarea {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 1rem;
  box-sizing: border-box;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 1rem;
  margin-top: 1.5rem;
}

.btn-cancel {
  background: #e5e7eb;
  color: #374151;
  border: none;
  padding: 0.75rem 1.5rem;
  border-radius: 6px;
  cursor: pointer;
}

.loading {
  text-align: center;
  padding: 2rem;
  color: #6b7280;
}

.image-upload-area {
  width: 100%;
}

.image-gallery-editor {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.gallery-thumb {
  position: relative;
  width: 110px;
  height: 110px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
}

.gallery-thumb.cover {
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.35);
}

.gallery-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-tag {
  position: absolute;
  top: 4px;
  left: 4px;
  background: #3b82f6;
  color: #fff;
  font-size: 0.65rem;
  padding: 1px 6px;
  border-radius: 8px;
}

.thumb-actions {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: space-between;
  background: rgba(0, 0, 0, 0.5);
}

.thumb-actions button {
  flex: 1;
  border: none;
  background: transparent;
  color: #fff;
  cursor: pointer;
  font-size: 0.9rem;
  padding: 2px 0;
}

.thumb-actions button:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.thumb-actions button.del:hover {
  background: rgba(239, 68, 68, 0.9);
}

.image-preview {
  position: relative;
  width: 200px;
  height: 200px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
}

.image-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.btn-remove-image {
  position: absolute;
  top: 4px;
  right: 4px;
  background: rgba(0, 0, 0, 0.6);
  color: white;
  border: none;
  border-radius: 50%;
  width: 24px;
  height: 24px;
  cursor: pointer;
  font-size: 0.8rem;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-remove-image:hover {
  background: rgba(239, 68, 68, 0.9);
}

.upload-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 110px;
  height: 110px;
  border: 2px dashed #d1d5db;
  border-radius: 8px;
  cursor: pointer;
  color: #9ca3af;
  font-size: 0.85rem;
  transition: border-color 0.2s, color 0.2s;
}

.upload-placeholder:hover {
  border-color: #3b82f6;
  color: #3b82f6;
}

.upload-placeholder.uploading {
  border-color: #3b82f6;
  color: #3b82f6;
  cursor: default;
}
</style>
