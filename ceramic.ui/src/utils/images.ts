// 图片图册工具：后端把多图以「JSON 数组字符串」存于 text 列（商品 images、
// 售后凭证 evidenceImages 等），前端统一用这里的方法解析 / 序列化，避免各处重复。

/** 解析 JSON 数组字符串为字符串数组；非法/空值返回 []。 */
export function parseImages(raw?: string | null): string[] {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter((s): s is string => typeof s === 'string' && !!s) : []
  } catch {
    return []
  }
}

/** 序列化字符串数组为 JSON 字符串（供提交/保存），空数组返回 ''。 */
export function stringifyImages(list: string[]): string {
  const cleaned = (list || []).filter(s => typeof s === 'string' && !!s)
  return cleaned.length ? JSON.stringify(cleaned) : ''
}

/**
 * 计算商品展示用图集：优先图册 images，回退到封面 imageUrl，
 * 再回退到约定的静态图路径，保证详情页/卡片始终有图可显。
 */
export function productGallery(product: { id: number; imageUrl?: string; images?: string }): string[] {
  const gallery = parseImages(product.images)
  if (gallery.length) return gallery
  if (product.imageUrl) return [product.imageUrl]
  return [`/images/products/${product.id}.jpg`]
}
