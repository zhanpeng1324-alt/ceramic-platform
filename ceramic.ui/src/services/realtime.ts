// src/services/realtime.ts
// WebSocket / STOMP 实时客户端（单例）。
// 供站内通知 store 与聊天页共用同一条连接；断线自动重连。
import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'

type Handler = (payload: unknown) => void

interface PendingSub {
  destination: string
  handler: Handler
}

let client: Client | null = null
// 已激活的订阅：destination -> { sub, handler }
const active = new Map<string, { sub: StompSubscription | null; handler: Handler }>()

function brokerURL(): string {
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${proto}://${window.location.host}/ws`
}

function doSubscribe(destination: string, handler: Handler): StompSubscription | null {
  if (!client || !client.connected) return null
  return client.subscribe(destination, (msg: IMessage) => {
    let payload: unknown = msg.body
    try {
      payload = JSON.parse(msg.body)
    } catch {
      // 非 JSON 消息原样返回
    }
    handler(payload)
  })
}

/** 建立连接（带 token）。重复调用是幂等的。连接成功后自动重放已登记的订阅。 */
export function connect(): void {
  const token = localStorage.getItem('token')
  if (!token) return
  if (client && (client.active || client.connected)) return

  client = new Client({
    brokerURL: brokerURL(),
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      // (重)连成功后，把所有登记的订阅重新挂上
      active.forEach((entry, destination) => {
        entry.sub = doSubscribe(destination, entry.handler)
      })
    },
  })
  client.activate()
}

/**
 * 订阅一个目的地。若连接尚未就绪，订阅会被登记并在连接成功后自动生效。
 * 返回取消订阅函数。
 */
export function subscribe(destination: string, handler: Handler): () => void {
  const entry = { sub: null as StompSubscription | null, handler }
  active.set(destination, entry)
  entry.sub = doSubscribe(destination, handler)
  return () => unsubscribe(destination)
}

/** 取消某个目的地的订阅。 */
export function unsubscribe(destination: string): void {
  const entry = active.get(destination)
  if (entry?.sub) {
    try {
      entry.sub.unsubscribe()
    } catch {
      /* ignore */
    }
  }
  active.delete(destination)
}

/** 断开连接并清空所有订阅（登出时调用）。 */
export function disconnect(): void {
  active.clear()
  if (client) {
    try {
      client.deactivate()
    } catch {
      /* ignore */
    }
    client = null
  }
}

export const realtime = { connect, subscribe, unsubscribe, disconnect }
