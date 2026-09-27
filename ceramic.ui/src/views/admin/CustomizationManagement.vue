<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { api } from '@/services/api'
import type { Customization, CustomizationProgress } from '@/types'
import AdminNavbar from '@/components/admin/AdminNavbar.vue'

const items = ref<Customization[]>([])
const selectedItem = ref<Customization | null>(null)
const progressList = ref<CustomizationProgress[]>([])
const loading = ref(false)
const imageUploading = ref(false)
const showProgressModal = ref(false)
const acting = ref(false)

/** 每个定制单的报价草稿 */
const quoteDraft = ref<Record<number, { price: number; expectedDate: string; note: string }>>({})
/** 状态流转备注草稿 */
const noteDraft = ref<Record<number, string>>({})
/** 完成时成品图 */
const finishedImage = ref<Record<number, string>>({})

const progressDraft = ref<{ stage: string; description: string; imageUrl: string }>({
  stage: '',
  description: '',
  imageUrl: ''
})

const labels: Record<string, string> = {
  PENDING: '待报价',
  QUOTED: '已报价',
  CONFIRMED: '已确认',
  IN_PROGRESS: '制作中',
  QUALITY_CHECK: '质检中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REJECTED: '已拒绝'
}

const statusClass = (s: string) => `st-${s}`
const depositRate = 0.3

/** design_specifications JSON 里的枚举值 → 中文 */
const shapeLabels: Record<string, string> = {
  teacup: '茶杯', vase: '花瓶', bowl: '碗', plate: '盘',
  tableware_set: '餐具套装', ornament: '摆件'
}
const materialLabels: Record<string, string> = {
  kaolin: '高岭土', porcelain_clay: '瓷土', stoneware_clay: '陶土'
}
const glazeLabels: Record<string, string> = {
  celadon: '青釉', white_glaze: '白釉', blue_and_white: '青花', crystalline_glaze: '结晶釉'
}
const sizeLabels: Record<string, string> = { small: '小号', medium: '中号', large: '大号' }

const enumLabel = (map: Record<string, string>, v: string | null | undefined): string =>
  v ? (map[v] || v) : ''

/** 需求展示：优先人工补充说明（requirement），否则把结构化规格 JSON 转成可读文本 */
const formatSpec = (item: Customization): string => {
  if (item.requirement) return item.requirement
  const raw = item.designSpecifications
  if (!raw) return '未填写'
  try {
    const s = JSON.parse(raw)
    const parts = [
      enumLabel(shapeLabels, s.shapeType),
      enumLabel(materialLabels, s.baseMaterial),
      enumLabel(glazeLabels, s.glazeType),
      enumLabel(sizeLabels, s.size)
    ].filter(Boolean)
    const extras: string[] = []
    if (s.customText && s.customText !== '无') extras.push(`刻字：${s.customText}`)
    if (s.customPattern && s.customPattern !== '无') extras.push(`图案：${s.customPattern}`)
    if (s.additionalNotes) extras.push(`备注：${s.additionalNotes}`)
    const text = parts.join(' · ')
    return text ? (extras.length ? `${text}，${extras.join('，')}` : text) : (extras.join('，') || '未填写')
  } catch {
    return raw || '未填写'
  }
}

/** 当前状态可执行的合法流转动作（仅管理员驱动项；客户确认报价由客户操作） */
const actionsFor = (s: string): { to: string; label: string; tone: 'primary' | 'danger' | 'ghost' }[] => {
  switch (s) {
    case 'PENDING':
      return [
        { to: 'QUOTED', label: '录入报价', tone: 'primary' },
        { to: 'REJECTED', label: '驳回', tone: 'danger' },
        { to: 'CANCELLED', label: '取消', tone: 'ghost' }
      ]
    case 'QUOTED':
      return [
        { to: 'REJECTED', label: '驳回', tone: 'danger' },
        { to: 'CANCELLED', label: '取消', tone: 'ghost' }
      ]
    case 'CONFIRMED':
      return [
        { to: 'IN_PROGRESS', label: '开始制作', tone: 'primary' },
        { to: 'CANCELLED', label: '取消', tone: 'ghost' }
      ]
    case 'IN_PROGRESS':
      return [
        { to: 'QUALITY_CHECK', label: '提交质检', tone: 'primary' },
        { to: 'CANCELLED', label: '取消', tone: 'ghost' }
      ]
    case 'QUALITY_CHECK':
      return [
        { to: 'COMPLETED', label: '质检通过并完成', tone: 'primary' },
        { to: 'IN_PROGRESS', label: '返工', tone: 'ghost' }
      ]
    default:
      return []
  }
}

const canAddProgress = (s: string) => s === 'CONFIRMED' || s === 'IN_PROGRESS' || s === 'QUALITY_CHECK'

const sortedItems = computed(() => [...items.value].sort((a, b) => b.id - a.id))

const formatDate = (t?: string) => {
  if (!t) return ''
  try {
    return new Date(t).toLocaleString('zh-CN')
  } catch {
    return t
  }
}

const load = async () => {
  loading.value = true
  try {
    items.value = await api.customizations()
    if (selectedItem.value) {
      const fresh = items.value.find(i => i.id === selectedItem.value!.id)
      if (fresh) selectedItem.value = fresh
    }
  } catch (e) {
    console.error('加载定制单失败', e)
  } finally {
    loading.value = false
  }
}

const loadProgress = async (item: Customization) => {
  try {
    progressList.value = await api.customizationProgress(item.id)
  } catch (e) {
    console.error('加载进度失败', e)
    progressList.value = []
  }
}

const openProgress = async (item: Customization) => {
  selectedItem.value = item
  showProgressModal.value = true
  await loadProgress(item)
}

const closeProgressModal = () => {
  showProgressModal.value = false
}

const getQuoteDraft = (item: Customization) => {
  if (!quoteDraft.value[item.id]) {
    quoteDraft.value[item.id] = {
      price: item.quotedPrice ? Number(item.quotedPrice) : 0,
      expectedDate: item.expectedCompleteDate || '',
      note: item.timelineNote || ''
    }
  }
  return quoteDraft.value[item.id]!
}

const quote = async (item: Customization) => {
  const draft = getQuoteDraft(item)
  if (!draft.price || draft.price <= 0) {
    alert('请输入有效的报价金额')
    return
  }
  acting.value = true
  try {
    await api.quoteCustomization(item.id, draft.price, draft.expectedDate, draft.note)
    await load()
  } catch (e) {
    alert(e instanceof Error ? e.message : '报价失败')
  } finally {
    acting.value = false
  }
}

const transition = async (item: Customization, to: string) => {
  const note = noteDraft.value[item.id] || ''
  const imgUrl = to === 'COMPLETED' ? (finishedImage.value[item.id] || '') : ''
  if (to === 'COMPLETED' && !imgUrl) {
    alert('完成制作前请先上传成品图')
    return
  }
  if (!confirm(`确定执行「${labels[to]}」操作？`)) return
  acting.value = true
  try {
    await api.transitionCustomizationStatus(item.id, to, note || undefined, imgUrl || undefined)
    noteDraft.value[item.id] = ''
    finishedImage.value[item.id] = ''
    await load()
  } catch (e) {
    alert(e instanceof Error ? e.message : '操作失败')
  } finally {
    acting.value = false
  }
}

const onProgressImageChange = async (e: Event) => {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  imageUploading.value = true
  try {
    progressDraft.value.imageUrl = await api.uploadImage(file)
  } catch (err) {
    console.error(err)
    alert('图片上传失败')
  } finally {
    imageUploading.value = false
  }
}

const addProgress = async (item: Customization) => {
  if (!progressDraft.value.stage.trim()) {
    alert('请输入阶段名称')
    return
  }
  if (!progressDraft.value.description.trim()) {
    alert('请输入描述')
    return
  }
  acting.value = true
  try {
    await api.addCustomizationProgress(
      item.id,
      progressDraft.value.stage,
      progressDraft.value.description,
      progressDraft.value.imageUrl || undefined
    )
    progressDraft.value = { stage: '', description: '', imageUrl: '' }
    await loadProgress(item)
  } catch (e) {
    alert(e instanceof Error ? e.message : '添加进度失败')
  } finally {
    acting.value = false
  }
}

const onFinishedImageChange = async (e: Event, item: Customization) => {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  imageUploading.value = true
  try {
    finishedImage.value[item.id] = await api.uploadImage(file)
  } catch (err) {
    console.error(err)
    alert('图片上传失败')
  } finally {
    imageUploading.value = false
    target.value = ''
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <AdminNavbar />
    <main>
      <header class="page-header">
        <div>
          <h1>定制订单管理</h1>
          <p>审核需求、报价、跟进制作进度并完成交付</p>
        </div>
        <button class="btn-primary" @click="load">刷新</button>
      </header>

      <p v-if="loading" class="loading-text">加载中…</p>

      <div v-else class="content-wrap">
        <section class="cards-grid">
          <article
            v-for="item in sortedItems"
            :key="item.id"
            class="card"
          >
            <div class="card-top">
              <strong class="card-no">#{{ item.id }} 陶瓷定制</strong>
              <span class="status" :class="statusClass(item.status)">{{ labels[item.status] || item.status }}</span>
            </div>

            <div class="card-body">
              <div class="info-row"><span class="info-label">客户</span><span class="info-value">{{ item.contactName || '用户 #' + item.userId }} · {{ item.contactPhone || '未留电话' }}</span></div>
              <div class="info-row"><span class="info-label">需求</span><span class="info-value">{{ formatSpec(item) }}</span></div>
              <div class="info-row"><span class="info-label">规格</span><span class="info-value">{{ enumLabel(shapeLabels, item.shape) || '-' }} · {{ enumLabel(glazeLabels, item.glazeColor) || '-' }} · {{ enumLabel(sizeLabels, item.size) || '-' }}</span></div>
              <div class="info-row"><span class="info-label">预算/数量</span><span class="info-value">¥{{ item.budget || item.price || 0 }} / {{ item.quantity || 1 }}</span></div>

              <div v-if="item.designImageUrl" class="thumb-wrap">
                <img :src="item.designImageUrl" alt="设计图" class="thumb" />
              </div>

              <!-- 报价区域：仅 PENDING 显示录入 -->
              <div v-if="item.status === 'PENDING'" class="quote-area">
                <div class="form-row">
                  <label>报价金额（¥）</label>
                  <input v-model.number="getQuoteDraft(item).price" type="number" min="0.01" placeholder="请输入报价" class="input" />
                </div>
                <div class="form-row">
                  <label>预计完成日期</label>
                  <input v-model="getQuoteDraft(item).expectedDate" type="date" class="input" />
                </div>
                <div class="form-row">
                  <label>制作说明</label>
                  <textarea v-model="getQuoteDraft(item).note" placeholder="制作周期或进度说明" class="input textarea" rows="2"></textarea>
                </div>
                <div class="deposit-tip" v-if="getQuoteDraft(item).price > 0">
                  定金（30%）：¥{{ (getQuoteDraft(item).price * depositRate).toFixed(2) }}
                </div>
                <button class="btn-primary" :disabled="acting" @click="quote(item)">保存报价</button>
              </div>

              <!-- 已报价信息展示 -->
              <div v-else-if="item.quotedPrice" class="quoted-info">
                <div class="quoted-row"><span>报价：</span><strong>¥{{ item.quotedPrice }}</strong></div>
                <div class="quoted-row"><span>定金（30%）：</span><strong>¥{{ (Number(item.quotedPrice) * depositRate).toFixed(2) }}</strong></div>
                <div class="quoted-row" v-if="item.status === 'CONFIRMED' || item.depositPaid">
                  <span>定金状态：</span>
                  <strong :style="{ color: item.depositPaid ? '#1e8449' : '#ca6f1e' }">{{ item.depositPaid ? '已支付' : '待客户支付' }}</strong>
                </div>
                <div class="quoted-row" v-if="item.expectedCompleteDate"><span>预计完成：</span><strong>{{ item.expectedCompleteDate }}</strong></div>
                <div class="quoted-row" v-if="item.timelineNote"><span>制作说明：</span>{{ item.timelineNote }}</div>
                <div class="quoted-row" v-if="item.finishedProductUrl">
                  <span>成品图：</span>
                  <img :src="item.finishedProductUrl" alt="成品图" class="thumb small" />
                </div>
              </div>

              <!-- 状态流转备注 -->
              <div v-if="actionsFor(item.status).length && item.status !== 'PENDING'" class="form-row">
                <label>操作备注（可选）</label>
                <input v-model="noteDraft[item.id]" class="input" placeholder="如：客户沟通确认" />
              </div>

              <!-- 完成时上传成品图 -->
              <div v-if="item.status === 'QUALITY_CHECK'" class="form-row">
                <label>成品图（完成时必传）</label>
                <div class="upload-line">
                  <label class="btn-outline upload-label">
                    {{ finishedImage[item.id] ? '重新上传' : '上传成品图' }}
                    <input type="file" accept="image/*" hidden @change="onFinishedImageChange($event, item)" />
                  </label>
                  <img v-if="finishedImage[item.id]" :src="finishedImage[item.id]" alt="预览" class="thumb small" />
                  <span v-if="imageUploading" class="uploading-tip">上传中…</span>
                </div>
              </div>

              <!-- 动作按钮 -->
              <div class="action-row" v-if="actionsFor(item.status).length">
                <button
                  v-for="act in actionsFor(item.status)"
                  :key="act.to"
                  :class="['btn-' + act.tone]"
                  :disabled="acting"
                  @click="transition(item, act.to)"
                >{{ act.label }}</button>
                <button v-if="canAddProgress(item.status)" class="btn-outline" @click="openProgress(item)">进度/添加</button>
              </div>
              <div class="action-row" v-else-if="canAddProgress(item.status)">
                <button class="btn-outline" @click="openProgress(item)">查看/添加进度</button>
              </div>
            </div>
          </article>

          <p v-if="!sortedItems.length" class="empty">暂无定制申请</p>
        </section>
      </div>

      <!-- 进度时间线弹窗 -->
      <div v-if="showProgressModal && selectedItem" class="modal-mask" @click.self="closeProgressModal">
        <div class="modal">
          <div class="modal-header">
            <div>
              <h2>定制单 #{{ selectedItem.id }} 进度</h2>
              <p class="modal-sub">{{ selectedItem.contactName }} · {{ formatSpec(selectedItem) }}</p>
            </div>
            <button class="btn-close" @click="closeProgressModal">×</button>
          </div>

          <div class="modal-body">
            <div class="timeline">
              <div v-if="!progressList.length" class="empty-mini">暂无进度记录</div>
              <div v-for="p in progressList" :key="p.id" class="timeline-item">
                <div class="dot"></div>
                <div class="timeline-content">
                  <div class="timeline-stage">{{ p.stage }}</div>
                  <div class="timeline-desc">{{ p.description }}</div>
                  <div class="timeline-meta">
                    <span>{{ p.operatorRole === 'admin' ? '管理员' : (p.operatorRole === 'customer' ? '客户' : (p.operatorRole || '')) }}</span>
                    <span v-if="p.fromStatus && p.toStatus">{{ labels[p.fromStatus] || p.fromStatus }} → {{ labels[p.toStatus] || p.toStatus }}</span>
                    <span>{{ formatDate(p.createdAt) }}</span>
                  </div>
                  <img v-if="p.imageUrl" :src="p.imageUrl" alt="进度图" class="timeline-img" />
                </div>
              </div>
            </div>

            <div class="progress-form">
              <h3>添加制作进度</h3>
              <div class="form-row">
                <label>阶段</label>
                <input v-model="progressDraft.stage" class="input" placeholder="例如：拉坯、上釉、烧制" />
              </div>
              <div class="form-row">
                <label>描述</label>
                <textarea v-model="progressDraft.description" class="input textarea" rows="3" placeholder="当前阶段的详细描述"></textarea>
              </div>
              <div class="form-row">
                <label>进度图片（可选）</label>
                <div class="upload-line">
                  <input type="file" accept="image/*" @change="onProgressImageChange" class="input-file" />
                  <span v-if="imageUploading" class="uploading-tip">上传中…</span>
                  <img v-if="progressDraft.imageUrl" :src="progressDraft.imageUrl" alt="预览" class="thumb small" />
                </div>
              </div>
              <button class="btn-primary" :disabled="imageUploading || acting" @click="addProgress(selectedItem)">提交进度</button>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.admin-page {
  min-height: 100vh;
  background: #f6f8f7;
}

main {
  max-width: 1240px;
  margin: 0 auto;
  padding: 28px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-header h1 { margin: 0; color: #243b53; font-size: 22px; }
.page-header p { color: #718096; margin: 4px 0 0; font-size: 13px; }

.btn-primary {
  border: 0; border-radius: 6px; padding: 9px 16px;
  background: #3d9b78; color: #fff; cursor: pointer; font-size: 14px;
}
.btn-primary:hover { background: #358866; }
.btn-primary:disabled { background: #a0c4b5; cursor: not-allowed; }

.btn-danger {
  border: 0; border-radius: 6px; padding: 9px 16px;
  background: #e74c3c; color: #fff; cursor: pointer; font-size: 14px;
}
.btn-danger:hover { background: #cb4335; }
.btn-danger:disabled { background: #f1948a; cursor: not-allowed; }

.btn-ghost {
  border: 1px solid #ccd7d1; border-radius: 6px; padding: 8px 14px;
  background: #fff; color: #516375; cursor: pointer; font-size: 13px;
}
.btn-ghost:hover { background: #f4f7f6; }
.btn-ghost:disabled { opacity: 0.6; cursor: not-allowed; }

.btn-outline {
  border: 1px solid #3d9b78; border-radius: 6px; padding: 7px 12px;
  background: #fff; color: #3d9b78; cursor: pointer; font-size: 13px;
}
.btn-outline:hover { background: #f0f7f4; }
.btn-outline:disabled { opacity: 0.6; cursor: not-allowed; }

.btn-close {
  border: 0; background: transparent; font-size: 24px; cursor: pointer; color: #718096; line-height: 1;
}

.loading-text { text-align: center; padding: 48px; color: #718096; }

.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 16px;
}

.card {
  background: #fff; border-radius: 12px; padding: 18px;
  box-shadow: 0 2px 10px rgba(34, 50, 66, 0.08);
}

.card-top {
  display: flex; justify-content: space-between; align-items: center;
  gap: 10px; margin-bottom: 14px;
}

.card-no { color: #2c4a3e; font-size: 15px; }

.status { padding: 3px 10px; border-radius: 12px; font-size: 12px; font-weight: 600; }
.st-PENDING { background: #fef3e2; color: #b9770e; }
.st-QUOTED { background: #e8f4fd; color: #2874a6; }
.st-CONFIRMED { background: #e8f8f0; color: #1e8449; }
.st-IN_PROGRESS { background: #fdf2e9; color: #ca6f1e; }
.st-QUALITY_CHECK { background: #f4ecf7; color: #7d3c98; }
.st-COMPLETED { background: #e8f8f0; color: #1e8449; }
.st-CANCELLED { background: #fadbd8; color: #922b21; }
.st-REJECTED { background: #fadbd8; color: #922b21; }

.card-body { display: flex; flex-direction: column; gap: 8px; }

.info-row { display: flex; font-size: 13px; line-height: 1.6; }
.info-label { flex: 0 0 80px; color: #82918a; }
.info-value { flex: 1; color: #384951; word-break: break-word; }

.thumb-wrap { margin-top: 4px; }
.thumb { width: 100%; max-height: 180px; object-fit: cover; border-radius: 8px; display: block; }
.thumb.small { width: 80px; max-height: 80px; border-radius: 6px; }

.quote-area, .quoted-info {
  margin-top: 12px; padding-top: 12px; border-top: 1px dashed #e1e8e4;
  display: flex; flex-direction: column; gap: 8px;
}

.form-row { display: flex; flex-direction: column; gap: 4px; }
.form-row label { font-size: 12px; color: #82918a; }

.input {
  padding: 7px 10px; border: 1px solid #ccd7d1; border-radius: 6px;
  font-size: 13px; outline: none; font-family: inherit;
}
.input:focus { border-color: #3d9b78; }
.textarea { resize: vertical; min-height: 40px; }

.deposit-tip {
  font-size: 12px; color: #3d9b78; background: #f0f7f4;
  padding: 6px 10px; border-radius: 6px;
}

.quoted-info { font-size: 13px; color: #384951; }
.quoted-row { display: flex; align-items: center; gap: 6px; }
.quoted-row strong { color: #3d9b78; }

.upload-line { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.upload-label { display: inline-flex; align-items: center; cursor: pointer; }
.input-file { font-size: 12px; }
.uploading-tip { color: #3d9b78; font-size: 12px; }

.action-row {
  margin-top: 12px; padding-top: 12px; border-top: 1px dashed #e1e8e4;
  display: flex; flex-wrap: wrap; gap: 8px;
}

.empty { grid-column: 1 / -1; text-align: center; padding: 48px; color: #718096; }

/* 弹窗 */
.modal-mask {
  position: fixed; inset: 0; background: rgba(36, 59, 83, 0.5);
  display: flex; justify-content: center; align-items: flex-start;
  padding: 40px 16px; z-index: 1000; overflow-y: auto;
}

.modal {
  background: #fff; border-radius: 12px; width: 100%; max-width: 680px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.2); overflow: hidden;
}

.modal-header {
  display: flex; justify-content: space-between; align-items: flex-start;
  padding: 18px 20px; border-bottom: 1px solid #eef2ef;
}
.modal-header h2 { margin: 0; font-size: 17px; color: #243b53; }
.modal-sub { margin: 4px 0 0; font-size: 12px; color: #718096; }

.modal-body { padding: 20px; max-height: 70vh; overflow-y: auto; }

.timeline {
  position: relative; padding-left: 18px; border-left: 2px solid #e1e8e4;
  margin-left: 6px; display: flex; flex-direction: column; gap: 18px;
}
.timeline-item { position: relative; }
.dot {
  position: absolute; left: -25px; top: 4px; width: 12px; height: 12px;
  border-radius: 50%; background: #3d9b78; border: 2px solid #fff;
  box-shadow: 0 0 0 2px #3d9b78;
}
.timeline-content { background: #f9fbfa; border-radius: 8px; padding: 10px 12px; }
.timeline-stage { font-weight: 600; color: #2c4a3e; font-size: 14px; }
.timeline-desc { margin-top: 4px; font-size: 13px; color: #384951; line-height: 1.6; word-break: break-word; }
.timeline-meta { margin-top: 6px; font-size: 12px; color: #82918a; display: flex; gap: 12px; flex-wrap: wrap; }
.timeline-img { margin-top: 8px; width: 100%; max-height: 220px; object-fit: cover; border-radius: 6px; }
.empty-mini { color: #718096; font-size: 13px; padding: 12px 0; }

.progress-form {
  margin-top: 20px; padding-top: 20px; border-top: 1px solid #eef2ef;
  display: flex; flex-direction: column; gap: 10px;
}
.progress-form h3 { margin: 0 0 4px; font-size: 15px; color: #243b53; }

@media (max-width: 768px) {
  main { padding: 16px; }
  .cards-grid { grid-template-columns: 1fr; }
  .page-header h1 { font-size: 18px; }
  .modal { max-width: 100%; }
  .modal-body { max-height: 75vh; }
}
</style>
