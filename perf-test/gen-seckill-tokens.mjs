/**
 * 秒杀压测 token 生成器：批量注册/登录测试用户，产出 JMeter 用的 seckill-tokens.csv
 *
 * 原理：演示模式短信验证码由 POST /api/users/sms/send 直接回显，
 *       POST /api/users/sms/login 对新手机号自动注册（loginOrRegisterBySms）。
 *       每个请求一个独立手机号 => 一个独立用户 => 一个独立 JWT。
 *
 * 用法（需 Node 18+，项目机器为 v24）：
 *   node gen-seckill-tokens.mjs            # 默认 1000 个，写入 ./seckill-tokens.csv
 *   node gen-seckill-tokens.mjs 500        # 指定数量
 *   node gen-seckill-tokens.mjs 1000 http://localhost:8080
 *
 * 测完清理数据库（可选）：
 *   DELETE FROM users WHERE phone LIKE '139100%';
 */
import { writeFileSync } from 'node:fs'

const args = process.argv.slice(2)
const COUNT = Number(args[0] ?? 1000)
const BASE = args[1] ?? 'http://localhost:8080'
const POOL = 20 // 并发注册数，避免瞬时打爆后端

// 13910000000 起连续手机号，保证互不相同且符合 11 位校验
const phones = Array.from({ length: COUNT }, (_, i) => `139${String(10000000 + i).slice(-8)}`)

async function register(phone) {
  const send = await (
    await fetch(`${BASE}/api/users/sms/send`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone }),
    })
  ).json()
  if (send.code !== 200) throw new Error(`send 失败 ${phone}: ${JSON.stringify(send)}`)
  // 兼容验证码直接回显为字符串或对象字段两种形态
  const code = typeof send.data === 'string' ? send.data : send.data?.code ?? send.data?.devCode
  const login = await (
    await fetch(`${BASE}/api/users/sms/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone, code: String(code) }),
    })
  ).json()
  if (login.code !== 200 || !login.data?.token) {
    throw new Error(`login 失败 ${phone}: ${JSON.stringify(login)}`)
  }
  return login.data.token
}

const tokens = []
let failed = 0
for (let i = 0; i < phones.length; i += POOL) {
  const batch = phones.slice(i, i + POOL)
  const results = await Promise.all(
    batch.map((p) =>
      register(p).catch((e) => {
        console.error(e.message)
        failed++
        return null
      }),
    ),
  )
  tokens.push(...results.filter(Boolean))
  process.stdout.write(`\r${Math.min(i + POOL, phones.length)}/${phones.length}`)
}
console.log()

writeFileSync('seckill-tokens.csv', 'token\n' + tokens.join('\n') + '\n', 'utf8')
console.log(`完成：成功 ${tokens.length} 个，失败 ${failed} 个 => seckill-tokens.csv`)
if (failed > 0) process.exitCode = 1
