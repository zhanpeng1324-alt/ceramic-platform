$ErrorActionPreference = "Continue"
$base = "http://localhost:8080"

# 1. 登录拿 token
$l = Invoke-RestMethod -Uri "$base/api/users/login" -Method Post -ContentType "application/json" -Body '{"username":"13900009999","password":"test12345"}' -TimeoutSec 5
$tok = $l.data.token
$H = @{ Authorization = "Bearer $tok" }
Write-Host "1. login ok, userId=$($l.data.user.id)"

# 2. payments 基线
$before = (mysql -u root -p123456 -N -e "SELECT COALESCE(MAX(id),0) FROM ceramic.payments" 2>$null)
Write-Host "2. payments max id BEFORE = $before"

# 3. 加购物车（商品5）+ 创建订单
Invoke-RestMethod -Uri "$base/api/cart" -Method Post -Headers $H -ContentType "application/json" -Body '{"productId":5,"quantity":1}' -TimeoutSec 5 | Out-Null
$o1 = Invoke-RestMethod -Uri "$base/api/orders" -Method Post -Headers $H -ContentType "application/json" -Body '{"payType":"alipay","receiverName":"Test","receiverPhone":"13900009999","receiverAddress":"Test Addr 1"}' -TimeoutSec 5
$orderId1 = $o1.data.id
Write-Host "3. order A created id=$orderId1 status=$($o1.data.status)"

# 4. 取消订单 A（模拟"已被取消"）
$c = Invoke-RestMethod -Uri "$base/api/orders/$orderId1/cancel" -Method Post -Headers $H -ContentType "application/json" -Body '{}' -TimeoutSec 5
Write-Host "4. cancel A => code=$($c.code)"

# 5. 支付已取消的订单 A —— 预期被拒绝
$p1raw = ""
try {
    $p1 = Invoke-RestMethod -Uri "$base/api/orders/$orderId1/pay" -Method Post -Headers $H -ContentType "application/json" -Body '{}' -TimeoutSec 5
    Write-Host "5. pay A => code=$($p1.code) msg=$($p1.msg)  <<< 如果这里是200就有问题"
    if ($p1.code -eq 200) { Write-Host "   !!! BUG STILL EXISTS: pay on cancelled order returned success" }
} catch {
    $resp = $_.Exception.Response
    if ($resp) { $sr = New-Object IO.StreamReader($resp.GetResponseStream()); $p1raw = $sr.ReadToEnd() }
    Write-Host "5. pay A => REJECTED: $p1raw"
}

# 6. 验证 payments 表：取消订单不应产生任何新流水
$after = (mysql -u root -p123456 -N -e "SELECT COALESCE(MAX(id),0) FROM ceramic.payments" 2>$null)
Write-Host "6. payments max id AFTER = $after (before=$before)"
if ([int]$after -eq [int]$before) { Write-Host "   PASS: no payment flow recorded for cancelled order" } else { Write-Host "   FAIL: payment flow leaked!"; mysql -u root -p123456 -e "SELECT id,biz_type,biz_id,status,amount FROM ceramic.payments WHERE id > $before" 2>$null }

# 7. 正常路径：创建订单 B 并支付 —— 预期成功且记流水
Invoke-RestMethod -Uri "$base/api/cart" -Method Post -Headers $H -ContentType "application/json" -Body '{"productId":6,"quantity":1}' -TimeoutSec 5 | Out-Null
$o2 = Invoke-RestMethod -Uri "$base/api/orders" -Method Post -Headers $H -ContentType "application/json" -Body '{"payType":"alipay","receiverName":"Test","receiverPhone":"13900009999","receiverAddress":"Test Addr 2"}' -TimeoutSec 5
$orderId2 = $o2.data.id
$stockBefore = (mysql -u root -p123456 -N -e "SELECT stock FROM ceramic.products WHERE id=6" 2>$null)
$p2 = Invoke-RestMethod -Uri "$base/api/orders/$orderId2/pay" -Method Post -Headers $H -ContentType "application/json" -Body '{}' -TimeoutSec 5
Write-Host "7. pay B(order=$orderId2) => code=$($p2.code) msg=$($p2.msg)"
$after2 = (mysql -u root -p123456 -N -e "SELECT COALESCE(MAX(id),0) FROM ceramic.payments" 2>$null)
$stockAfter = (mysql -u root -p123456 -N -e "SELECT stock FROM ceramic.products WHERE id=6" 2>$null)
$ostat = (mysql -u root -p123456 -N -e "SELECT status FROM ceramic.orders WHERE id=$orderId2" 2>$null)
Write-Host "8. order B status=$ostat, stock 6: $stockBefore -> $stockAfter, payments: $before -> $after2"

if ($p2.code -eq 200 -and $ostat -match "PAID" -and [int]$after2 -gt [int]$after) { Write-Host "   PASS: normal pay path intact (status PAID, stock deducted, flow recorded)" } else { Write-Host "   FAIL: normal path broken" }
