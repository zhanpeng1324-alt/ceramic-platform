// Core trade-chain regression test for ceramic platform backend (port 8080)
// Run: C:\软件\node.exe regression-test.mjs
const BASE = 'http://localhost:8080';
const results = [];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function api(method, path, token, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json;
  try { json = JSON.parse(text); } catch { json = { raw: text.slice(0, 200) }; }
  return { http: res.status, json };
}

function record(id, name, pass, evidence) {
  results.push({ id, name, pass, evidence });
  console.log(`[${pass ? 'PASS' : 'FAIL'}] #${id} ${name} | ${evidence}`);
}

// GET /api/orders/{id} detail returns {order:{...}, items:[...]}
const orderStatusOf = (j) => (j.data && (j.data.order ? j.data.order.status : j.data.status)) ?? null;

async function login(phone) {
  // sms/send has a 60s per-phone rate limit; retry across the window if throttled
  let code = null;
  for (let i = 0; i < 8 && !code; i++) {
    const s = await api('POST', '/api/users/sms/send', null, { phone });
    code = s.json && s.json.data && s.json.data.code;
    if (!code) {
      const msg = (s.json.message || s.json.msg || '').slice(0, 40);
      if (!/频繁|上限/.test(msg)) throw new Error(`sms/send failed for ${phone}: ${JSON.stringify(s.json).slice(0, 150)}`);
      console.log(`  ... sms throttled for ${phone}, retry in 15s (${msg})`);
      await sleep(15000);
    }
  }
  if (!code) throw new Error(`sms/send still throttled for ${phone}`);
  const l = await api('POST', '/api/users/sms/login', null, { phone, code: String(code) });
  const token = l.json && l.json.data && l.json.data.token;
  if (!token) throw new Error(`sms/login failed for ${phone}: ${JSON.stringify(l.json)}`);
  return { token, userId: l.json.data.userId ?? (l.json.data.user && l.json.data.user.id) };
}

async function main() {
  // ---- #1 health check
  const h = await api('GET', '/api/products/1');
  record(1, 'Health GET /api/products/1', h.http === 200 && h.json.code === 200,
    `http=${h.http} code=${h.json.code} productName=${(h.json.data && (h.json.data.product ? h.json.data.product.name : h.json.data.name))}`);

  // ---- #2 user login
  let userToken;
  try {
    const u = await login('13910099999');
    userToken = u.token;
    globalThis.__userToken = userToken;
    record(2, 'User login 13910099999', !!userToken, `token.len=${(userToken || '').length}`);
  } catch (e) {
    record(2, 'User login 13910099999', false, String(e.message).slice(0, 120));
    return finish();
  }

  // ---- #3 product list
  const pl = await api('GET', '/api/products');
  record(3, 'Product list non-empty', pl.http === 200 && pl.json.code === 200 && Array.isArray(pl.json.data) && pl.json.data.length > 0,
    `http=${pl.http} code=${pl.json.code} count=${Array.isArray(pl.json.data) ? pl.json.data.length : 'n/a'}`);

  // ---- #4 add to cart + verify visible
  const add = await api('POST', '/api/cart', userToken, { productId: 1, quantity: 2 });
  const cartId = add.json && add.json.data && add.json.data.id;
  const cart = await api('GET', '/api/cart', userToken);
  const visible = Array.isArray(cart.json.data) && cart.json.data.some((c) => c.id === cartId && c.quantity === 2);
  record(4, 'Add to cart product#1 qty=2', add.http === 200 && add.json.code === 200 && visible,
    `add.code=${add.json.code} cartItemId=${cartId} visibleInCart=${visible}`);

  // ---- #5a checked-items checkout path (cartItemIds) — suspected Integer/Long type-mismatch bug
  const odA = await api('POST', '/api/orders', userToken, {
    receiverName: 'Test User',
    receiverPhone: '13910099999',
    receiverAddress: 'Test Address 123',
    remark: 'regression checked-items order',
    payType: 'wechat',
    cartItemIds: [cartId],
  });
  const aOk = odA.json.code === 200 && odA.json.data && odA.json.data.orderNo;
  record('5a', 'Checkout with cartItemIds=[id]', aOk,
    `code=${odA.json.code} msg=${((odA.json.message || odA.json.msg || '') + '').slice(0, 40)}` +
    (aOk ? ` orderNo=${odA.json.data.orderNo}` : ' [suspected List<Integer>.contains(Long) always-false bug in OrderService.createFromCart]'));
  if (aOk) {
    // keep DB consistent: cancel & clean this extra order is left to cleanup SQL
    globalThis.__extraOrderIds = [...(globalThis.__extraOrderIds || []), odA.json.data.id];
    // restore cart item for the main path
    await api('POST', '/api/cart', userToken, { productId: 1, quantity: 2 });
  }

  // ---- #5 whole-cart checkout
  const od = await api('POST', '/api/orders', userToken, {
    receiverName: 'Test User',
    receiverPhone: '13910099999',
    receiverAddress: 'Test Address 123',
    remark: 'regression test order',
    payType: 'wechat',
  });
  const order = od.json && od.json.data;
  const orderId = order && order.id;
  const orderNo = order && order.orderNo;
  const orderStatus = order && order.status;
  const paidAmount = order && order.totalAmount;
  record(5, 'Create order from cart', od.http === 200 && od.json.code === 200 && !!orderNo && orderStatus === 'PENDING_PAY',
    `orderNo=${orderNo} id=${orderId} status=${orderStatus} total=${paidAmount}`);

  // ---- #6 pay via gateway -> PAID
  let payPass = false, payEvidence = 'order create failed';
  if (orderId) {
    const init = await api('POST', '/api/payments', userToken, { bizType: 'ORDER', bizId: orderId, channel: 'wechat' });
    const paymentNo = init.json && init.json.data && init.json.data.paymentNo;
    const conf = paymentNo ? await api('POST', `/api/payments/${paymentNo}/confirm`, userToken) : { json: {} };
    const after = await api('GET', `/api/orders/${orderId}`, userToken);
    const st = orderStatusOf(after.json);
    payPass = init.json.code === 200 && conf.json.code === 200 && st === 'PAID';
    payEvidence = `paymentNo=${paymentNo} init.code=${init.json.code} confirm.code=${conf.json.code} order.status=${st}`;
  }
  record(6, 'Pay via gateway -> PAID', payPass, payEvidence);

  // ---- #7 unauthorized access by another account (13910099998)
  let otherToken = null;
  try { otherToken = (await login('13910099998')).token; } catch (e) {
    record(7, 'Login second account 13910099998', false, String(e.message).slice(0, 120));
  }
  if (otherToken && orderId) {
    const v = await api('GET', `/api/orders/${orderId}`, otherToken);           // view
    const p = await api('POST', '/api/payments', otherToken, { bizType: 'ORDER', bizId: orderId, channel: 'alipay' }); // pay
    const c = await api('POST', `/api/orders/${orderId}/cancel`, otherToken);   // cancel
    const blocked = (v.json.code !== 200) && (p.json.code !== 200) && (c.json.code !== 200);
    record(7, 'Ownership guard (view/pay/cancel by stranger)', blocked,
      `view.code=${v.json.code} pay.code=${p.json.code} cancel.code=${c.json.code} (all must !=200)`);
  } else {
    record(7, 'Ownership guard', false, 'second token or order missing');
  }

  // ---- #8 after-sale refund-only on PAID unshipped order (auto fast refund)
  let retPass = false, retEvidence = 'order missing';
  if (orderId) {
    const pd = (await api('GET', '/api/products/1')).json.data;
    const prod = pd && (pd.product ? pd.product : pd);
    const unitPrice = prod && prod.price;
    const rr = await api('POST', '/api/return-requests', userToken, {
      orderId, productId: 1,
      productName: (prod && prod.name) || 'product-1',
      unitPrice, quantity: 2,
      returnType: 'REFUND_ONLY',
      reason: 'regression test refund',
    });
    const rid = rr.json && rr.json.data && rr.json.data.id;
    const detail = rid ? await api('GET', `/api/return-requests/${rid}`, userToken) : null;
    const rStatus = detail && detail.json.data && detail.json.data.status;
    const rAmt = detail && detail.json.data && detail.json.data.refundAmount;
    const capped = rAmt != null && paidAmount != null && Number(rAmt) <= Number(paidAmount);
    retPass = rr.json.code === 200 && rStatus === 'REFUNDED' && capped;
    retEvidence = rid
      ? `returnId=${rid} status=${rStatus} refund=${rAmt} paid=${paidAmount} cappedOk=${capped}`
      : `create.code=${rr.json.code} msg=${((rr.json.message || rr.json.msg || '') + '').slice(0, 60)}`;
  }
  record(8, 'After-sale refund-only (PAID, unshipped, auto refund)', retPass, retEvidence);

  // ---- #9 second order then cancel PENDING_PAY
  let cPass = false, cEvidence = 'n/a';
  try {
    const add2 = await api('POST', '/api/cart', userToken, { productId: 2, quantity: 1 });
    const cartId2 = add2.json.data.id;
    const od2 = await api('POST', '/api/orders', userToken, {
      receiverName: 'Test User',
      receiverPhone: '13910099999',
      receiverAddress: 'Test Address 456',
      remark: 'regression cancel order',
      payType: 'wechat',
    });
    const o2 = od2.json.data;
    const before = o2.status;
    const cx = await api('POST', `/api/orders/${o2.id}/cancel`, userToken);
    const after = await api('GET', `/api/orders/${o2.id}`, userToken);
    const st = orderStatusOf(after.json);
    cPass = before === 'PENDING_PAY' && cx.json.code === 200 && st === 'CANCELLED';
    cEvidence = `orderNo=${o2.orderNo} before=${before} cancel.code=${cx.json.code} after=${st}`;
  } catch (e) { cEvidence = String(e.message).slice(0, 120); }
  record(9, 'Cancel own PENDING_PAY order -> CANCELLED', cPass, cEvidence);

  // ---- #10 seckill chain
  let activityId = null, skOrderNo = null, skDbPass = false;
  try {
    const admin = await login('13800000000');
    const fmt = (d) => d.toISOString().replace('T', ' ').slice(0, 19);
    const start = new Date(Date.now() - 60 * 1000);
    const end = new Date(Date.now() + 86400 * 1000);
    const ca = await api('POST', '/api/admin/seckill', admin.token, {
      productId: 5, seckillPrice: '9.90', totalStock: 5,
      startTime: fmt(start), endTime: fmt(end),
    });
    activityId = ca.json.data && ca.json.data.id;
    globalThis.__activityId = activityId;
    record(10.1, 'Admin create seckill activity (product#5 stock=5)', ca.json.code === 200 && !!activityId,
      `activityId=${activityId} code=${ca.json.code}`);

    // NOTE: admin token reused below in #11 via closure
    globalThis.__adminToken = admin.token;

    const b1 = await api('POST', `/api/seckill/${activityId}/buy`, userToken);
    skOrderNo = b1.json.data && b1.json.data.orderNo;
    record(10.2, 'Seckill buy #1 success', b1.json.code === 200 && !!skOrderNo,
      `code=${b1.json.code} orderNo=${skOrderNo}`);

    await sleep(1300); // bypass 1/s rate limiter so we hit the duplicate-purchase guard
    const b2 = await api('POST', `/api/seckill/${activityId}/buy`, userToken);
    record(10.3, 'Seckill buy #2 blocked (one per user)', b2.json.code === 400,
      `code=${b2.json.code} msg=${(b2.json.message || b2.json.msg || '').slice(0, 40)}`);

    // poll async DB persist (MQ consumer)
    let so = null;
    for (let i = 0; i < 30; i++) {
      const d = await api('GET', `/api/seckill/orders/${skOrderNo}`, userToken);
      if (d.json.code === 200 && d.json.data && d.json.data.id) { so = d.json.data; break; }
      await sleep(500);
    }
    if (!so) throw new Error('seckill order not persisted in 15s');
    record(10.4, 'Seckill order async persisted', true, `seckillOrderId=${so.id} status=${so.status}`);

    const si = await api('POST', '/api/payments', userToken, { bizType: 'SECKILL', bizId: so.id, channel: 'alipay' });
    const spNo = si.json.data && si.json.data.paymentNo;
    const sc = await api('POST', `/api/payments/${spNo}/confirm`, userToken);
    const sd = await api('GET', `/api/seckill/orders/${skOrderNo}`, userToken);
    const sData = sd.json.data || {};
    skDbPass = sData.status === 'PAID' && !!sData.convertOrderNo;
    record(10.5, 'Pay SECKILL -> PAID + convertOrderNo', skDbPass,
      `status=${sData.status} convertOrderNo=${sData.convertOrderNo} confirm.code=${sc.json.code}`);
    globalThis.__skOrderNo = skOrderNo;
    globalThis.__convertOrderNo = sData.convertOrderNo;
  } catch (e) {
    record(10, 'Seckill chain', false, String(e.message).slice(0, 150));
  }

  // ---- #11 admin cancel paid seckill formal order -> seckill REFUNDED
  try {
    const adminToken = globalThis.__adminToken;
    const convertNo = globalThis.__convertOrderNo;
    const skOrderNo2 = globalThis.__skOrderNo;
    if (!adminToken || !convertNo || !skOrderNo2) throw new Error('prereq missing');
    const all = await api('GET', '/api/orders', adminToken);
    const fo = (all.json.data || []).find((o) => o.orderNo === convertNo);
    if (!fo) throw new Error('formal order not found: ' + convertNo);
    const cx = await api('POST', `/api/orders/${fo.id}/cancel`, adminToken);
    // seckill order belongs to the customer — query with the customer token (ownership check)
    const sd = await api('GET', `/api/seckill/orders/${skOrderNo2}`, globalThis.__userToken);
    const st = sd.json.data && sd.json.data.status;
    record(11, 'Admin cancel paid seckill formal order -> REFUNDED', cx.json.code === 200 && st === 'REFUNDED',
      `formalOrderId=${fo.id} cancel.code=${cx.json.code} seckill.status=${st}${st !== 'REFUNDED' ? ' [if DB shows PAID: backend may need IDE restart to load new code]' : ''}`);
  } catch (e) {
    record(11, 'Admin cancel seckill formal order', false, String(e.message).slice(0, 150));
  }

  // ---- #12 customization read-only
  const cz = await api('GET', '/api/customizations', userToken);
  record(12, 'GET /api/customizations', cz.http === 200 && cz.json.code === 200,
    `http=${cz.http} code=${cz.json.code} count=${Array.isArray(cz.json.data) ? cz.json.data.length : 'n/a'}`);

  return finish();
}

function finish() {
  console.log('\n===== SUMMARY =====');
  for (const r of results) console.log(`#${r.id} ${r.pass ? 'PASS' : 'FAIL'} ${r.name}`);
  const fails = results.filter((r) => !r.pass).length;
  console.log(`TOTAL ${results.length - fails}/${results.length} passed`);
  console.log(`ACTIVITY_ID=${globalThis.__activityId} SK_ORDER_NO=${globalThis.__skOrderNo} EXTRA_ORDER_IDS=${(globalThis.__extraOrderIds || []).join(',')}`);
}

main().catch((e) => { console.error('FATAL', e); process.exit(1); });
