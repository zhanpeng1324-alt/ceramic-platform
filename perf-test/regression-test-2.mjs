// Round-2 regression: order lifecycle + shipped-order return/refund, customization chain, peripheral features
// Run: C:\软件\node.exe regression-test-2.mjs
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

const orderStatusOf = (j) => (j.data && (j.data.order ? j.data.order.status : j.data.status)) ?? null;
const errMsg = (j) => ((j.message || j.msg || '') + '').slice(0, 50);

async function login(phone) {
  let code = null;
  for (let i = 0; i < 8 && !code; i++) {
    const s = await api('POST', '/api/users/sms/send', null, { phone });
    code = s.json && s.json.data && s.json.data.code;
    if (!code) {
      if (!/频繁|上限/.test(errMsg(s.json))) throw new Error(`sms/send failed ${phone}: ${JSON.stringify(s.json).slice(0, 120)}`);
      console.log(`  ... sms throttled ${phone}, wait 15s`);
      await sleep(15000);
    }
  }
  const l = await api('POST', '/api/users/sms/login', null, { phone, code: String(code) });
  const token = l.json && l.json.data && l.json.data.token;
  if (!token) throw new Error(`sms/login failed ${phone}: ${JSON.stringify(l.json).slice(0, 120)}`);
  return token;
}

// pay a business order via gateway; returns paymentNo
async function payViaGateway(token, bizType, bizId, channel = 'wechat') {
  const init = await api('POST', '/api/payments', token, { bizType, bizId, channel });
  if (init.json.code !== 200 || !init.json.data || !init.json.data.paymentNo) {
    throw new Error(`initiate ${bizType}#${bizId} failed: ${errMsg(init.json)}`);
  }
  const conf = await api('POST', `/api/payments/${init.json.data.paymentNo}/confirm`, token);
  if (conf.json.code !== 200) throw new Error(`confirm ${init.json.data.paymentNo} failed: ${errMsg(conf.json)}`);
  return init.json.data.paymentNo;
}

async function main() {
  const userToken = await login('13910099999');
  const otherToken = await login('13910099998');
  const adminToken = await login('13800000000');
  console.log('tokens ready: user/other/admin');

  // ================= A. Order lifecycle =================
  // ---- #1 order product#2 -> PAID
  let orderId = null, paidAmount = null;
  try {
    await api('POST', '/api/cart', userToken, { productId: 2, quantity: 1 });
    const od = await api('POST', '/api/orders', userToken, {
      receiverName: 'Test User', receiverPhone: '13910099999',
      receiverAddress: 'Test Road 456', remark: 'round2 lifecycle order', payType: 'wechat',
    });
    orderId = od.json.data && od.json.data.id;
    await payViaGateway(userToken, 'ORDER', orderId);
    const st = orderStatusOf((await api('GET', `/api/orders/${orderId}`, userToken)).json);
    const detail = (await api('GET', `/api/orders/${orderId}`, userToken)).json;
    paidAmount = detail.data && detail.data.order && detail.data.order.totalAmount;
    record(1, 'Order product#2 -> PAID', st === 'PAID', `orderId=${orderId} status=${st} paid=${paidAmount}`);
  } catch (e) { record(1, 'Order -> PAID', false, String(e.message).slice(0, 130)); }

  // ---- #2 admin ship -> SHIPPED
  try {
    const sh = await api('POST', `/api/orders/${orderId}/ship`, adminToken,
      { shippingCompany: 'SF Express', trackingNo: 'SHIP-TEST-001' });
    const st = orderStatusOf((await api('GET', `/api/orders/${orderId}`, userToken)).json);
    record(2, 'Admin ship SHIP-TEST-001', sh.json.code === 200 && st === 'SHIPPED',
      `ship.code=${sh.json.code} status=${st}`);
  } catch (e) { record(2, 'Admin ship', false, String(e.message).slice(0, 130)); }

  // ---- #3 customer confirm receipt -> COMPLETED
  try {
    const cf = await api('POST', `/api/orders/${orderId}/confirm`, userToken);
    const st = orderStatusOf((await api('GET', `/api/orders/${orderId}`, userToken)).json);
    record(3, 'Customer confirm receipt', cf.json.code === 200 && st === 'COMPLETED',
      `confirm.code=${cf.json.code} status=${st}`);
  } catch (e) { record(3, 'Confirm receipt', false, String(e.message).slice(0, 130)); }

  // ---- #4 shipped-order return & refund (full chain)
  let retId = null;
  try {
    const cr = await api('POST', '/api/return-requests', userToken, {
      orderId, productId: 2, productName: 'product-2', unitPrice: 149.00, quantity: 1,
      returnType: 'RETURN_REFUND', reason: 'round2 return test',
    });
    retId = cr.json.data && cr.json.data.id;
    let st = cr.json.data && cr.json.data.status; // expect PENDING (no auto refund for COMPLETED)
    const ap = await api('PUT', `/api/return-requests/${retId}/approve`, adminToken, { returnAddress: 'Return Warehouse 1' });
    const sb = await api('PUT', `/api/return-requests/${retId}/ship-back`, userToken, { trackingNo: 'RETURN-TRK-001' });
    const rc = await api('PUT', `/api/return-requests/${retId}/receive`, adminToken);
    const rf1 = await api('PUT', `/api/return-requests/${retId}/refund`, adminToken);           // RECEIVED -> REFUNDING
    const rf2 = await api('PUT', `/api/return-requests/${retId}/refund`, adminToken, { refundAmount: paidAmount }); // -> REFUNDED
    const det = (await api('GET', `/api/return-requests/${retId}`, userToken)).json.data;
    const capped = Number(det.refundAmount) <= Number(paidAmount);
    const chainOk = st === 'PENDING' && ap.json.code === 200 && sb.json.code === 200 && rc.json.code === 200
      && rf1.json.code === 200 && rf2.json.code === 200 && det.status === 'REFUNDED' && capped;
    record(4, 'Return+refund chain (APPROVED>RETURNING>RECEIVED>REFUNDING>REFUNDED)', chainOk,
      `retId=${retId} final=${det.status} refund=${det.refundAmount} paid=${paidAmount} capped=${capped}` +
      (chainOk ? '' : ` codes[ap=${ap.json.code},sb=${sb.json.code},rc=${rc.json.code},rf1=${rf1.json.code},rf2=${rf2.json.code}] ${errMsg(rf2.json)}`));
  } catch (e) { record(4, 'Return+refund chain', false, String(e.message).slice(0, 130)); }

  // ---- #5 illegal transitions on COMPLETED order
  try {
    const re = await api('POST', `/api/orders/${orderId}/ship`, adminToken, { shippingCompany: 'SF', trackingNo: 'SHIP-AGAIN' });
    const rc = await api('POST', `/api/orders/${orderId}/cancel`, adminToken);
    const blocked = re.json.code !== 200 && rc.json.code !== 200;
    record(5, 'Re-ship / re-cancel COMPLETED rejected', blocked,
      `reShip.code=${re.json.code}(${errMsg(re.json)}) reCancel.code=${rc.json.code}(${errMsg(rc.json)})`);
  } catch (e) { record(5, 'Illegal transitions', false, String(e.message).slice(0, 130)); }

  // ================= B. Customization chain =================
  // ---- #6 create -> PENDING
  let czId = null;
  try {
    const cz = await api('POST', '/api/customizations', userToken, {
      contactName: 'Test User', contactPhone: '13910099999', shippingAddress: 'Test Road 456',
      requirement: 'Custom mug, 500ml', quantity: 1, budget: 200,
    });
    czId = cz.json.data && cz.json.data.id;
    record(6, 'Create customization', cz.json.code === 200 && cz.json.data.status === 'PENDING',
      `czId=${czId} status=${cz.json.data.status}`);
  } catch (e) { record(6, 'Create customization', false, String(e.message).slice(0, 130)); }

  // ---- #7 full chain QUOTED -> CONFIRMED -> (deposit) -> IN_PROGRESS -> QUALITY_CHECK -> (balance) -> COMPLETED
  try {
    const q = await api('PATCH', `/api/customizations/${czId}/quote`, adminToken,
      { quotedPrice: '99.90', expectedCompleteDate: '2026-10-15', timelineNote: 'About 18 days' });
    let st1 = (await api('GET', `/api/customizations/${czId}`, userToken)).json.data.status;
    const cq = await api('POST', `/api/customizations/${czId}/confirm-quote`, userToken);
    let st2 = (await api('GET', `/api/customizations/${czId}`, userToken)).json.data.status;
    await payViaGateway(userToken, 'CUSTOM_DEPOSIT', czId); // deposit = 29.97
    const dep = (await api('GET', `/api/customizations/${czId}`, userToken)).json.data.depositPaid;
    const ip = await api('PATCH', `/api/customizations/${czId}/status`, adminToken, { status: 'IN_PROGRESS' });
    let st3 = (await api('GET', `/api/customizations/${czId}`, userToken)).json.data.status;
    const qc = await api('PATCH', `/api/customizations/${czId}/status`, adminToken, { status: 'QUALITY_CHECK' });
    let st4 = (await api('GET', `/api/customizations/${czId}`, userToken)).json.data.status;
    await payViaGateway(userToken, 'CUSTOM_BALANCE', czId); // balance = 69.93
    const fin = (await api('GET', `/api/customizations/${czId}`, userToken)).json.data.finalPaid;
    const ac = await api('POST', `/api/customizations/${czId}/accept`, userToken);
    let st5 = (await api('GET', `/api/customizations/${czId}`, userToken)).json.data.status;
    const ok = q.json.code === 200 && st1 === 'QUOTED' && cq.json.code === 200 && st2 === 'CONFIRMED'
      && dep === true && ip.json.code === 200 && st3 === 'IN_PROGRESS' && qc.json.code === 200 && st4 === 'QUALITY_CHECK'
      && fin === true && ac.json.code === 200 && st5 === 'COMPLETED';
    record(7, 'Customization chain -> COMPLETED', ok,
      `states=${[st1, st2, st3, st4, st5].join('>')} depositPaid=${dep} finalPaid=${fin}` +
      (ok ? '' : ` codes[q=${q.json.code},cq=${cq.json.code},ip=${ip.json.code},qc=${qc.json.code},ac=${ac.json.code}] ${errMsg(ac.json) || errMsg(ip.json)}`));
  } catch (e) { record(7, 'Customization chain', false, String(e.message).slice(0, 130)); }

  // ---- #8 illegal jump PENDING -> COMPLETED rejected + #9 cross-user guard
  let czId2 = null;
  try {
    const cz2 = await api('POST', '/api/customizations', userToken, {
      contactName: 'Test User', contactPhone: '13910099999', shippingAddress: 'Test Road 456',
      requirement: 'Second test piece', quantity: 1,
    });
    czId2 = cz2.json.data.id;
    const jump = await api('PATCH', `/api/customizations/${czId2}/status`, adminToken, { status: 'COMPLETED' });
    const czAfter = (await api('GET', `/api/customizations/${czId2}`, userToken)).json.data;
    const jumpBlocked = jump.json.code !== 200 && czAfter.status === 'PENDING';
    record(8, 'PENDING -> COMPLETED rejected (LEGAL_TRANSITIONS)', jumpBlocked,
      `code=${jump.json.code} msg=${errMsg(jump.json)} status=${czAfter.status}`);
  } catch (e) { record(8, 'Illegal jump', false, String(e.message).slice(0, 130)); }

  try {
    const stolen = await api('POST', `/api/customizations/${czId2}/confirm-quote`, otherToken);
    const stillPending = (await api('GET', `/api/customizations/${czId2}`, userToken)).json.data.status;
    const guarded = stolen.json.code !== 200 && stillPending === 'PENDING';
    record(9, 'User B operates user A customization rejected', guarded,
      `code=${stolen.json.code} msg=${errMsg(stolen.json)} status=${stillPending}`);
  } catch (e) { record(9, 'Cross-user guard', false, String(e.message).slice(0, 130)); }

  // ================= C. Peripherals =================
  // ---- #10 address CRUD
  try {
    const ad = await api('POST', '/api/addresses', userToken,
      { receiverName: 'Test Receiver', receiverPhone: '13910099999', address: 'Test Road 789' });
    const aid = ad.json.data && ad.json.data.id;
    const ls = await api('GET', '/api/addresses', userToken);
    const inList = (ls.json.data || []).some((a) => a.id === aid);
    const up = await api('PUT', `/api/addresses/${aid}`, userToken,
      { receiverName: 'Test Receiver', receiverPhone: '13910099999', address: 'Updated Road 999', isDefault: false });
    const upAddr = (await api('GET', `/api/addresses/${aid}`, userToken)).json.data.address;
    const de = await api('DELETE', `/api/addresses/${aid}`, userToken);
    const gone = (await api('GET', `/api/addresses/${aid}`, userToken)).json.code; // expect 404
    const ok = ad.json.code === 200 && inList && up.json.code === 200 && upAddr === 'Updated Road 999'
      && de.json.code === 200 && gone === 404;
    record(10, 'Address add/list/update/delete', ok,
      `addrId=${aid} add=${ad.json.code} inList=${inList} upd=${up.json.code} newAddr=${upAddr} del=${de.json.code} reGet=${gone}`);

    // #10b known defect probe: PUT without isDefault field -> Boolean null unboxing NPE -> 500
    const ad2 = await api('POST', '/api/addresses', userToken,
      { receiverName: 'Test Receiver', receiverPhone: '13910099999', address: 'Npe Probe Road' });
    const aid2 = ad2.json.data && ad2.json.data.id;
    const up2 = await api('PUT', `/api/addresses/${aid2}`, userToken,
      { receiverName: 'Test Receiver', receiverPhone: '13910099999', address: 'Npe Probe Road 2' });
    record('10b', 'DEFECT probe: PUT address without isDefault field', false,
      `http=${up2.http} code=${up2.json.code} [UserAddressService.updateAddress L48 unboxing NPE; client must send isDefault] cleanup will remove addrId=${aid2}`);
  } catch (e) { record(10, 'Address CRUD', false, String(e.message).slice(0, 130)); }

  // ---- #11 admin stats + dual permission
  try {
    const st1 = await api('GET', '/api/admin/stats', adminToken);
    const d = st1.json.data || {};
    const numericKeys = Object.keys(d).filter((k) => typeof d[k] === 'number' || typeof d[k] === 'object');
    const st2 = await api('GET', '/api/admin/stats', userToken);
    record(11, 'Admin stats (admin ok / customer 403)', st1.json.code === 200 && numericKeys.length > 0 && st2.json.code !== 200,
      `admin.code=${st1.json.code} keys=[${numericKeys.slice(0, 5).join(',')}] customer.code=${st2.json.code}(${errMsg(st2.json)})`);
  } catch (e) { record(11, 'Admin stats', false, String(e.message).slice(0, 130)); }

  // ---- #12 AI chat + service reply
  try {
    const conv = await api('POST', '/api/chat/conversations', userToken, {});
    const convId = conv.json.data && conv.json.data.id;
    const ai = await api('POST', '/api/chat/ai/messages', userToken,
      { conversationId: String(convId), message: 'Do you have tea sets in stock?' });
    const am = ai.json.data && ai.json.data.assistantMessage;
    const reply = am && (am.content || am.message || am.text || '');
    const sv = await api('POST', '/api/chat/messages', adminToken,
      { conversationId: String(convId), message: 'Service reply test' });
    record(12, 'AI chat reply + service reply', ai.json.code === 200 && !!reply && sv.json.code === 200,
      `convId=${convId} ai.code=${ai.json.code} reply.len=${(reply || '').length} service.code=${sv.json.code}`);
  } catch (e) { record(12, 'Chat', false, String(e.message).slice(0, 130)); }

  // ---- #13 WebSocket
  record(13, 'WebSocket push', null, 'SKIP - needs manual browser test');

  console.log('\n===== SUMMARY =====');
  for (const r of results) console.log(`#${r.id} ${r.pass === null ? 'SKIP' : r.pass ? 'PASS' : 'FAIL'} ${r.name}`);
  const failed = results.filter((r) => r.pass === false && r.id !== '10b').length;
  const skipped = results.filter((r) => r.pass === null).length;
  const defects = results.filter((r) => r.id === '10b').length;
  console.log(`TOTAL ${results.length - failed - skipped - defects}/${results.length - skipped - defects} passed, ${skipped} skipped, ${defects} known-defect probe`);
}

main().catch((e) => { console.error('FATAL', e); process.exit(1); });
