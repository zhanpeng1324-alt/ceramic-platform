$ErrorActionPreference = "Continue"
$base = "http://localhost:8080"

function Query($sql) { (& mysql -u root -p123456 -N -e $sql) 2>$null }

# 1. login
$l = Invoke-RestMethod -Uri "$base/api/users/login" -Method Post -ContentType "application/json" -Body '{"username":"13900009999","password":"test12345"}' -TimeoutSec 5
$tok = $l.data.token
$H = @{ Authorization = "Bearer $tok" }
Write-Host "1. login ok (customer id=$($l.data.user.id))"

function New-OrderWithPayment {
    Invoke-RestMethod -Uri "$base/api/cart" -Method Post -Headers $H -ContentType "application/json" -Body '{"productId":5,"quantity":1}' -TimeoutSec 5 | Out-Null
    $o = Invoke-RestMethod -Uri "$base/api/orders" -Method Post -Headers $H -ContentType "application/json" -Body '{"payType":"alipay","receiverName":"Test","receiverPhone":"13900009999","receiverAddress":"Idem Test"}' -TimeoutSec 5
    $p = Invoke-RestMethod -Uri "$base/api/payments" -Method Post -Headers $H -ContentType "application/json" -Body ('{"bizType":"ORDER","bizId":' + $o.data.id + ',"channel":"alipay"}') -TimeoutSec 5
    return @{ orderId = $o.data.id; paymentNo = $p.data.paymentNo }
}
function Snapshot {
    $stock = Query "SELECT stock FROM ceramic.products WHERE id=5"
    $flow  = Query "SELECT COUNT(*) FROM ceramic.payments WHERE status='SUCCESS'"
    return @{ stock = [int]$stock; successFlows = [int]$flow }
}

Write-Host "=== A. serial repeated confirm (gateway retry) ==="
$s0 = Snapshot
$t = New-OrderWithPayment
Write-Host "A1. order=$($t.orderId) paymentNo=$($t.paymentNo)"
$r1 = Invoke-RestMethod -Uri "$base/api/payments/$($t.paymentNo)/confirm" -Method Post -Headers $H -TimeoutSec 5
Write-Host "A2. 1st confirm => code=$($r1.code) status=$($r1.data.status)"
$s1 = Snapshot
$r2 = Invoke-RestMethod -Uri "$base/api/payments/$($t.paymentNo)/confirm" -Method Post -Headers $H -TimeoutSec 5
Write-Host "A3. 2nd confirm => code=$($r2.code) status=$($r2.data.status)"
$r3 = Invoke-RestMethod -Uri "$base/api/payments/$($t.paymentNo)/confirm" -Method Post -Headers $H -TimeoutSec 5
Write-Host "A4. 3rd confirm => code=$($r3.code) status=$($r3.data.status)"
$s2 = Snapshot
$ostat = Query "SELECT status FROM ceramic.orders WHERE id=$($t.orderId)"
Write-Host "A5. order status=$ostat, stock=$($s2.stock) (was $($s0.stock)), successFlows=$($s2.successFlows) (was $($s0.successFlows))"
$passA = ($ostat -match "PAID") -and ($s2.stock -eq $s1.stock) -and ($s2.stock -eq ($s0.stock - 1)) -and ($s2.successFlows -eq $s1.successFlows) -and ($s2.successFlows -eq ($s0.successFlows + 1))
if ($passA) { Write-Host "   PASS A: stock deducted once, one SUCCESS flow, repeats have no side effect" } else { Write-Host "   FAIL A" }

Write-Host "=== B. concurrent double confirm ==="
$t2 = New-OrderWithPayment
Write-Host "B1. order=$($t2.orderId) paymentNo=$($t2.paymentNo)"
$sb = Snapshot
$job1 = Start-Job -ScriptBlock { param($b,$tt,$tk) Invoke-RestMethod -Uri "$b/api/payments/$($tt.paymentNo)/confirm" -Method Post -Headers @{Authorization="Bearer $tk"} -TimeoutSec 10 } -ArgumentList $base,$t2,$tok
$job2 = Start-Job -ScriptBlock { param($b,$tt,$tk) Invoke-RestMethod -Uri "$b/api/payments/$($tt.paymentNo)/confirm" -Method Post -Headers @{Authorization="Bearer $tk"} -TimeoutSec 10 } -ArgumentList $base,$t2,$tok
Wait-Job $job1,$job2 | Out-Null
$o1 = Receive-Job $job1; $o2 = Receive-Job $job2
Remove-Job $job1,$job2
Write-Host "B2. concurrent results => codes=[$($o1.code),$($o2.code)] statuses=[$($o1.data.status),$($o2.data.status)]"
$s3 = Snapshot
$ostat2 = Query "SELECT status FROM ceramic.orders WHERE id=$($t2.orderId)"
$paidCnt = Query "SELECT COUNT(*) FROM ceramic.payments WHERE biz_id=$($t2.orderId) AND biz_type='ORDER' AND status='SUCCESS'"
Write-Host "B3. order status=$ostat2, stock=$($s3.stock) (was $($sb.stock)), SUCCESS flows for this order=$paidCnt"
$passB = ($ostat2 -match "PAID") -and ([int]$paidCnt -eq 1) -and ($s3.stock -eq ($sb.stock - 1))
if ($passB) { Write-Host "   PASS B: concurrent double confirm applied exactly once (CAS gate holds)" } else { Write-Host "   FAIL B" }

Write-Host "=== C. permission gate: customer calls gateway callback ==="
try {
    $cb = Invoke-RestMethod -Uri "$base/api/payments/$($t.paymentNo)/callback" -Method Post -Headers $H -TimeoutSec 5
    Write-Host "C1. customer callback => code=$($cb.code) msg=$($cb.msg)"
    if ($cb.code -eq 403) { Write-Host "   PASS C" } else { Write-Host "   FAIL C: callback should reject customer" }
} catch {
    Write-Host "C1. customer callback => HTTP blocked"
    Write-Host "   PASS C"
}
