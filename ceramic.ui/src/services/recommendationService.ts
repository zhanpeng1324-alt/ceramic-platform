import type { Product, Recommendation } from '@/types'
import { api } from './api'

export class RecommendationService {
  async getRecommendations(limit: number = 6): Promise<Recommendation[]> {
    try {
      return await api.recommendations(limit)
    } catch {
      return []
    }
  }

  async generateRecommendations(limit: number = 6): Promise<Recommendation[]> {
    try {
      return await api.generateRecommendations(limit)
    } catch {
      return []
    }
  }

  async getSimilarProducts(productId: number, limit: number = 6): Promise<Recommendation[]> {
    try {
      return await api.similarProducts(productId, limit)
    } catch {
      return []
    }
  }

  async getRecommendationRules(): Promise<any[]> {
    try {
      return await api.recommendationRules()
    } catch {
      return []
    }
  }

  async createRecommendationRule(rule: any): Promise<any> {
    return api.createRecommendationRule(rule)
  }

  async updateRecommendationRule(id: number, rule: any): Promise<any> {
    return api.updateRecommendationRule(id, rule)
  }

  async toggleRecommendationRule(id: number, isActive: number): Promise<void> {
    return api.toggleRecommendationRule(id, isActive)
  }

  async recordBehavior(productId: number, action: string, sessionDuration?: number, referrer?: string): Promise<void> {
    try {
      await api.recordBehavior({ productId, action, sessionDuration, referrer })
    } catch {
      console.warn('记录用户行为失败')
    }
  }

  async getUserBehaviors(productId?: number): Promise<any[]> {
    try {
      return await api.userBehaviors(productId)
    } catch {
      return []
    }
  }

  async getUserPreferences(): Promise<any> {
    try {
      return await api.userPreferences()
    } catch {
      return null
    }
  }
}

export const recommendationService = new RecommendationService()