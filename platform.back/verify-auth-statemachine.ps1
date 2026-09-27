$ErrorActionPreference = "Continue"
$base = "http://localhost:8080"
function Query($sql) { (& mysql -u root -p123456 -N -e $sql) 2>$null }

# tokens
$lc = Invoke-RestMethod -Uri "$base/api/users/login" -Method Post -ContentType "application/json" -Body '{"username":"13900009999","password":"test12345"}' -TimeoutSec 5
$CT = $lc.data.token
$la = Invoke-RestMethod -Uri "$base/api/users/login" -Method Post -ContentType "application/json" -Body '{"username":"admin","password":"test12345"}' -TimeoutSec 5
$AT = $la.data.token
$CH = @{ Authorization = "Bearer $CT" }
$AH = @{ Authorization = "Bearer $AT" }
Write-Host "tokens ok (customer=$($lc.data.user.id), admin role=$($la.data.user.role))"

$pass = 0; $fail = 0
function Check($name, $cond) {
    if ($cond) { $script:pass++; Write-Host "   PASS: $name" } else { $script:fail++; Write-Host "   FAIL: $name" }
}
# helper: POST/PATCH and return code+msg
function CallApi($method, $uri, $headers, $body) {
    try {
        $r = Invoke-RestMethod -Uri $uri -Method $method -Headers $headers -ContentType "application/json" -Body $body -TimeoutSec 8
        return @{ code = $r.code; msg = $r.msg; http = 200 }
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $sr = New-Object IO.StreamReader($resp.GetResponseStream())
            $txt = $sr.ReadToEnd()
            try { $j = $txt | ConvertFrom-Json; return @{ code = $j.code; msg = $j.msg; http = [int]$resp.StatusCode } }
            catch { return @{ code = $null; msg = $txt; http = [int]$resp.StatusCode } }
        }
        return @{ code = $null; msg = $_.Exception.Message; http = 0 }
    }
}

Write-Host "=== A. authorization checks (customer 24 attacking others' data) ==="
# A1. read user9's order 42
$r = CallApi "GET" "$base/api/orders/42" $CH ""
Write-Host "A1. GET other's order => code=$($r.code) msg=$($r.msg)"
Check "A1 read other's order denied" ($r.code -ne 200)
# A2. pay other's order 42
$r = CallApi "POST" "$base/api/orders/42/pay" $CH "{}"
Write-Host "A2. PAY other's order => code=$($r.code) msg=$($r.msg)"
Check "A2 pay other's order denied" ($r.code -ne 200)
# A3. cancel other's order 42
$r = CallApi "POST" "$base/api/orders/42/cancel" $CH "{}"
Write-Host "A3. CANCEL other's order => code=$($r.code) msg=$($r.msg)"
Check "A3 cancel other's order denied" ($r.code -ne 200)
# A4. customer uses admin status API
$r = CallApi "PATCH" "$base/api/orders/46/status" $CH '{"status":"COMPLETED"}'
Write-Host "A4. customer PATCH order status => code=$($r.code) msg=$($r.msg)"
Check "A4 customer status API denied" ($r.code -ne 200)
# A5. customer uses admin customization status API
$r = CallApi "PATCH" "$base/api/customizations/10/status" $CH '{"status":"COMPLETED"}'
Write-Host "A5. customer PATCH customization status => code=$($r.code) msg=$($r.msg)"
Check "A5 customer custom-status API denied" ($r.code -ne 200)
# A6. read user9's customization 10
$r = CallApi "GET" "$base/api/customizations/10" $CH ""
Write-Host "A6. GET other's customization => code=$($r.code) msg=$($r.msg)"
Check "A6 read other's customization denied" ($r.code -ne 200)
# A7. forged userId param on order list
$r = CallApi "GET" "$base/api/orders?userId=9" $CH ""
Write-Host "A7. GET /api/orders?userId=9 => code=$($r.code) count=$($r.data.Count)"
$forged = $false
if ($r.code -eq 200 -and $r.data) { $forged = ($r.data | Where-Object { $_.userId -ne 24 }).Count -gt 0 }
Check "A7 forged userId ignored (only own orders)" ($r.code -eq 200 -and -not $forged)
# A8. admin stats with customer token
$r = CallApi "GET" "$base/api/admin/stats/overview" $CH ""
Write-Host "A8. customer GET admin stats => code=$($r.code) msg=$($r.msg)"
Check "A8 admin stats denied" ($r.code -ne 200)
# A9. read other's payment flow
$pno = Query "SELECT payment_no FROM ceramic.payments WHERE user_id=9 AND status='SUCCESS' LIMIT 1"
if ($pno) {
    $r = CallApi "GET" "$base/api/payments/$pno" $CH ""
    Write-Host "A9. GET other's payment flow($pno) => code=$($r.code) msg=$($r.msg)"
    Check "A9 read other's payment denied" ($r.code -ne 200)
} else { Write-Host "A9. no payment flow of user9, skipped"; $pass++ }

Write-Host "=== B. state machine illegal transitions (admin) ==="
# order 46: PAID
$r = CallApi "PATCH" "$base/api/orders/46/status" $AH '{"status":"COMPLETED"}'
Write-Host "B1. PAID->COMPLETED => $($r.code) $($r.msg)"
Check "B1 illegal jump rejected" ($r.code -ne 200)
$r = CallApi "PATCH" "$base/api/orders/46/status" $AH '{"status":"PENDING_PAY"}'
Write-Host "B2. PAID->PENDING_PAY => $($r.code) $($r.msg)"
Check "B2 backward jump rejected" ($r.code -ne 200)
$r = CallApi "PATCH" "$base/api/orders/46/status" $AH '{"status":"SHIPPED"}'
Write-Host "B3. PAID->SHIPPED => $($r.code) $($r.msg)"
Check "B3 legal transition allowed" ($r.code -eq 200)
$r = CallApi "PATCH" "$base/api/orders/46/status" $AH '{"status":"PAID"}'
Write-Host "B4. SHIPPED->PAID => $($r.code) $($r.msg)"
Check "B4 backward jump rejected" ($r.code -ne 200)
$r = CallApi "PATCH" "$base/api/orders/46/status" $AH '{"status":"COMPLETED"}'
Write-Host "B5. SHIPPED->COMPLETED => $($r.code) $($r.msg)"
Check "B5 legal transition allowed" ($r.code -eq 200)
$r = CallApi "PATCH" "$base/api/orders/46/status" $AH '{"status":"SHIPPED"}'
Write-Host "B6. COMPLETED->SHIPPED (terminal) => $($r.code) $($r.msg)"
Check "B6 terminal state locked" ($r.code -ne 200)
# customization 10: CONFIRMED (user9 real data; only illegal attempts, no state change)
$r = CallApi "PATCH" "$base/api/customizations/10/status" $AH '{"status":"COMPLETED"}'
Write-Host "B7. CONFIRMED->COMPLETED => $($r.code) $($r.msg)"
Check "B7 custom illegal jump rejected" ($r.code -ne 200)
$r = CallApi "PATCH" "$base/api/customizations/10/status" $AH '{"status":"PENDING"}'
Write-Host "B8. CONFIRMED->PENDING => $($r.code) $($r.msg)"
Check "B8 custom backward jump rejected" ($r.code -ne 200)
$cs = Query "SELECT status FROM ceramic.custom_orders WHERE id=10"
Check "B9 customization 10 untouched" ($cs -match "CONFIRMED")

Write-Host "=== RESULT: PASS=$pass FAIL=$fail ==="
