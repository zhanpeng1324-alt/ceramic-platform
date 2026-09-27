<script setup lang="ts">
import { useRouter } from 'vue-router'
import ProductRecommendation from '@/components/ProductRecommendation.vue'
import SeckillBanner from '@/components/home/SeckillBanner.vue'

const router = useRouter()

const goToProducts = () => {
  router.push('/products')
}

const goToCustomize = () => {
  router.push('/customize')
}

// 分类入口（id 与商品列表页的 categoryId 一致）
// icon 为线描器物 SVG（stroke=currentColor，随主题着色），替代原 emoji
const categories = [
  {
    id: 1,
    name: '茶器',
    desc: '茶杯 · 茶壶 · 盖碗',
    svg: '<svg viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M12 18h22v8a11 11 0 0 1-11 11 11 11 0 0 1-11-11z"/><path d="M34 21h4a4 4 0 0 1 0 8h-4"/><path d="M17 12c0 2-2 2-2 4M23 11c0 2-2 2-2 4M29 12c0 2-2 2-2 4"/><path d="M9 43h30"/></svg>',
  },
  {
    id: 2,
    name: '餐器',
    desc: '碗碟 · 餐具套装',
    svg: '<svg viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M8 22h32a16 16 0 0 1-16 14A16 16 0 0 1 8 22z"/><path d="M24 22c5 0 9-2 9-5s-4-5-9-5-9 2-9 5 4 5 9 5z"/><path d="M24 36v4"/></svg>',
  },
  {
    id: 3,
    name: '摆件',
    desc: '花瓶 · 艺术陈设',
    svg: '<svg viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8h12l-2 6c4 3 6 7 6 13 0 8-5 12-10 12s-10-4-10-12c0-6 2-10 6-13z"/><path d="M18 24h12"/></svg>',
  },
  {
    id: 4,
    name: '企业定制',
    desc: '批量 · 礼品定制',
    svg: '<svg viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><rect x="8" y="18" width="32" height="22" rx="2"/><path d="M8 26h32M24 18v22"/><path d="M24 18c-3-8-11-6-11-1 0 3 5 4 11 1zM24 18c3-8 11-6 11-1 0 3-5 4-11 1z"/></svg>',
  },
]

const goCategory = (id: number) => {
  router.push({ path: '/products', query: { category: String(id) } })
}

// 卖点 / 信任区
const features = [
  { icon: '✦', title: '匠心工艺', desc: '传统手作，窑火淬炼' },
  { icon: '✓', title: '正品保障', desc: '官方自营，品质可溯' },
  { icon: '⚡', title: '顺丰包邮', desc: '全场包邮，极速送达' },
  { icon: '♡', title: '售后无忧', desc: '七天退换，专属客服' },
]
</script>

<template>
  <div class="home">
    <!-- Hero -->
    <section class="hero-section tex-crackle">
      <div class="hero-glaze" aria-hidden="true"></div>
      <span class="ui-seal hero-seal" aria-hidden="true">瓷</span>
      <div class="hero-content">
        <p class="hero-eyebrow">CERAMIC · 传承千年的东方器物美学</p>
        <h1>欢迎来到陶瓷世界</h1>
        <span class="hero-rule" aria-hidden="true"></span>
        <p class="hero-sub">探索传统工艺与现代设计的完美结合，一器一物，皆有温度。</p>
        <div class="hero-actions">
          <button class="cta-button" @click="goToProducts">开始购物</button>
          <button class="cta-button cta-ghost" @click="goToCustomize">定制专区</button>
        </div>
      </div>
    </section>

    <!-- 限时秒杀 -->
    <SeckillBanner />

    <!-- 分类入口 -->
    <section class="section categories-section">
      <h2 class="ui-section-title">按分类选购</h2>
      <div class="category-grid">
        <button
          v-for="cat in categories"
          :key="cat.id"
          class="category-card"
          @click="goCategory(cat.id)"
        >
          <span class="category-icon" v-html="cat.svg"></span>
          <span class="category-name">{{ cat.name }}</span>
          <span class="category-desc">{{ cat.desc }}</span>
        </button>
      </div>
    </section>

    <!-- 精选商品推荐 -->
    <section class="section">
      <h2 class="ui-section-title">为你精选</h2>
      <ProductRecommendation
        recommendation-type="comprehensive"
        :limit="8"
        :userId="1"
      />
    </section>

    <!-- 卖点 / 信任区 -->
    <section class="section features-section">
      <div class="feature-strip tex-crackle">
        <div class="ui-divider-meander feature-meander" aria-hidden="true"></div>
        <div class="feature-row">
          <div v-for="f in features" :key="f.title" class="feature-item">
            <span class="feature-icon">{{ f.icon }}</span>
            <div class="feature-text">
              <span class="feature-title">{{ f.title }}</span>
              <span class="feature-desc">{{ f.desc }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 热门商品推荐 -->
    <section class="section">
      <h2 class="ui-section-title">热门商品</h2>
      <ProductRecommendation
        recommendation-type="popular"
        :limit="4"
        :userId="1"
      />
    </section>

    <!-- 新品推荐 -->
    <section class="section">
      <h2 class="ui-section-title">新品上市</h2>
      <ProductRecommendation
        recommendation-type="trending"
        :limit="4"
        :userId="1"
      />
    </section>
  </div>
</template>

<style scoped>
.home {
  padding-bottom: var(--sp-6);
}

/* ===== Hero ===== */
.hero-section {
  position: relative;
  overflow: hidden;
  /* 青瓷釉面：多停靠点渐变，模拟釉水由浅入深积聚 */
  background:
    radial-gradient(1200px 420px at 12% -25%, rgba(255, 255, 255, 0.22), transparent 60%),
    radial-gradient(760px 480px at 100% 125%, rgba(31, 95, 76, 0.55), transparent 55%),
    linear-gradient(135deg, var(--c-celadon) 0%, var(--c-primary) 42%, var(--c-primary-dark) 78%, var(--c-primary-deep) 100%);
  color: var(--c-surface);
  padding: calc(var(--sp-6) * 2) var(--sp-4);
  text-align: center;
  margin: 0 auto var(--sp-6) auto;
  max-width: 1200px;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  /* 描金细框 */
  outline: 1px solid var(--c-gold);
  outline-offset: -7px;
}

/* 釉光高光层：左上一抹柔光，增加釉面的湿润感 */
.hero-glaze {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: radial-gradient(520px 300px at 22% 8%, rgba(255, 255, 255, 0.28), transparent 62%);
  mix-blend-mode: screen;
}

/* Hero 右上角印章点缀 */
.hero-seal {
  position: absolute;
  top: 20px;
  right: 24px;
  width: 42px;
  height: 42px;
  font-size: 1.3rem;
  z-index: 2;
}

.hero-content {
  position: relative;
  max-width: 720px;
  margin: 0 auto;
  z-index: 1;
}

.hero-eyebrow {
  font-size: 0.85rem;
  letter-spacing: 2px;
  text-transform: uppercase;
  opacity: 0.9;
  margin-bottom: var(--sp-3);
}

.hero-content h1 {
  font-family: var(--font-serif);
  font-size: 2.7rem;
  font-weight: 700;
  margin-bottom: var(--sp-3);
  letter-spacing: 3px;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.18);
}

/* 标题下的描金细分隔线 */
.hero-rule {
  display: block;
  width: 72px;
  height: 2px;
  margin: 0 auto var(--sp-4);
  background: linear-gradient(90deg, transparent, var(--c-gold-soft), var(--c-gold), var(--c-gold-soft), transparent);
}

.hero-sub {
  font-size: 1.15rem;
  margin-bottom: var(--sp-5);
  opacity: 0.95;
  line-height: 1.7;
}

.hero-actions {
  display: flex;
  gap: var(--sp-3);
  justify-content: center;
  flex-wrap: wrap;
}

.cta-button {
  background: var(--c-accent);
  color: var(--c-surface);
  border: 2px solid var(--c-accent);
  padding: var(--sp-3) var(--sp-6);
  border-radius: 50px;
  font-size: 1.05rem;
  font-weight: 700;
  cursor: pointer;
  box-shadow: var(--shadow);
  transition: transform 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}

.cta-button:hover {
  transform: translateY(-2px) scale(1.03);
  background: var(--c-accent-dark);
  border-color: var(--c-accent-dark);
  box-shadow: var(--shadow-lg);
}

.cta-ghost {
  background: transparent;
  border-color: rgba(255, 255, 255, 0.7);
  box-shadow: none;
}

.cta-ghost:hover {
  background: rgba(255, 255, 255, 0.14);
  border-color: #fff;
}

/* ===== 通用小节 ===== */
.section {
  max-width: 1200px;
  margin: 0 auto var(--sp-6) auto;
  padding: 0 var(--sp-4);
}

.section > h2 {
  margin-bottom: var(--sp-5);
  font-size: 1.6rem;
}

/* ===== 分类入口 ===== */
.category-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--sp-4);
}

.category-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-1);
  padding: var(--sp-5) var(--sp-4);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow);
  cursor: pointer;
  text-align: center;
  transition: transform 0.2s, box-shadow 0.2s, border-color 0.2s;
}

.category-card:hover {
  transform: translateY(-4px);
  border-color: var(--c-gold);
  box-shadow: var(--shadow-lg);
}

.category-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 66px;
  height: 66px;
  margin-bottom: var(--sp-2);
  color: var(--c-primary-deep);
  /* 青瓷釉面圆座 + 描金细边 */
  background: radial-gradient(circle at 34% 28%, #ffffff 0%, var(--c-celadon) 60%, var(--c-primary-soft) 100%);
  border: 1px solid var(--c-gold-soft);
  border-radius: 50%;
  transition: transform 0.2s, box-shadow 0.2s, color 0.2s, border-color 0.2s;
}

.category-icon :deep(svg) {
  width: 38px;
  height: 38px;
}

.category-card:hover .category-icon {
  color: var(--c-primary);
  border-color: var(--c-gold);
  transform: scale(1.06);
  /* 悬停釉光 */
  box-shadow: 0 0 0 4px rgba(215, 232, 224, 0.6), inset 0 2px 6px rgba(255, 255, 255, 0.8);
}

.category-name {
  font-size: 1.1rem;
  font-weight: 700;
  font-family: var(--font-serif);
  color: var(--c-text);
}

.category-desc {
  font-size: 0.85rem;
  color: var(--c-text-muted);
}

/* ===== 卖点 / 信任区 ===== */
.features-section {
  padding: 0 var(--sp-4);
}

.feature-strip {
  position: relative;
  padding: var(--sp-6) var(--sp-5) var(--sp-5);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.6), rgba(255, 255, 255, 0.2)),
    var(--c-celadon);
  border: 1px solid var(--c-gold-soft);
  border-radius: var(--radius-lg);
}

/* 顶部回纹分隔条 */
.feature-meander {
  position: absolute;
  top: 12px;
  left: var(--sp-5);
  right: var(--sp-5);
}

.feature-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--sp-4);
}

.feature-item {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.feature-icon {
  flex-shrink: 0;
  width: 46px;
  height: 46px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--c-surface);
  color: var(--c-primary-deep);
  font-size: 1.2rem;
  /* 描金圈 */
  border: 1.5px solid var(--c-gold);
  box-shadow: inset 0 0 0 3px rgba(255, 255, 255, 0.8);
}

.feature-text {
  display: flex;
  flex-direction: column;
}

.feature-title {
  font-weight: 700;
  font-family: var(--font-serif);
  color: var(--c-text);
}

.feature-desc {
  font-size: 0.85rem;
  color: var(--c-text-muted);
}

/* ===== 响应式 ===== */
@media (max-width: 900px) {
  .category-grid,
  .feature-row {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .hero-section {
    padding: var(--sp-6) var(--sp-3);
    border-radius: var(--radius);
  }

  .hero-content h1 {
    font-size: 2rem;
  }

  .hero-sub {
    font-size: 1rem;
  }

  .section > h2 {
    font-size: 1.35rem;
  }
}

@media (max-width: 480px) {
  .category-grid,
  .feature-row {
    grid-template-columns: 1fr;
  }
}
</style>
