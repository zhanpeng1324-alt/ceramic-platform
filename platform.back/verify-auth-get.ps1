$ErrorActionPreference = "Continue"
$base = "http://localhost:8080"
function Query($sql) { (& mysql -u root -p123456 -N -e $sql) 2>$null }

$lc = Invoke-RestMethod -Uri "$base/api/users/login" -Method Post -ContentType "application/json" -Body '{"username":"13900009999","password":"test12345"}' -TimeoutSec 5
$CT = $lc.data.token
$CH = @{ Authorization = "Bearer $CT" }
Write-Host "customer token ok"

$pass = 0; $fail = 0
function Check($name, $cond) { if ($cond) { $script:pass++; Write-Host "   PASS: $name" } else { $script:fail++; Write-Host "   FAIL: $name" } }
function GetApi($uri, $headers) {
    try {
        $r = Invoke-RestMethod -Uri $uri -Method Get -Headers $headers -TimeoutSec 8
        return @{ code = $r.code; msg = $r.msg; data = $r.data; http = 200 }
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $sr = New-Object IO.StreamReader($resp.GetResponseStream())
            $txt = $sr.ReadToEnd()
            try { $j = $txt | ConvertFrom-Json; return @{ code = $j.code; msg = $j.msg; data = $j.data; http = [int]$resp.StatusCode } }
            catch { return @{ code = $null; msg = $txt; data = $null; http = [int]$resp.StatusCode } }
        }
        return @{ code = $null; msg = $_.Exception.Message; data = $null; http = 0 }
    }
}

# A1. read user9's order 42
$r = GetApi "$base/api/orders/42" $CH
Write-Host "A1. GET other's order 42 => code=$($r.code) msg=$($r.msg)"
Check "A1 read other's order denied" ($r.code -ne 200)
# A6. read user9's customization 10
$r = GetApi "$base/api/customizations/10" $CH
Write-Host "A6. GET other's customization 10 => code=$($r.code) msg=$($r.msg)"
Check "A6 read other's customization denied" ($r.code -ne 200)
# A7. forged userId param
$r = GetApi "$base/api/orders?userId=9" $CH
$foreign = 0
if ($r.code -eq 200 -and $r.data) { $foreign = @($r.data | Where-Object { $_.userId -ne 24 }).Count }
Write-Host "A7. GET /api/orders?userId=9 => code=$($r.code) returned=$(@($r.data).Count) foreign=$foreign"
Check "A7 forged userId ignored" ($r.code -eq 200 -and $foreign -eq 0)
# A8. admin stats
$r = GetApi "$base/api/admin/stats/overview" $CH
Write-Host "A8. GET admin stats => code=$($r.code) msg=$($r.msg)"
Check "A8 admin stats denied" ($r.code -ne 200)
# A9. other's payment flow
$pno = Query "SELECT payment_no FROM ceramic.payments WHERE user_id=9 AND status='SUCCESS' LIMIT 1"
$r = GetApi "$base/api/payments/$pno" $CH
Write-Host "A9. GET other's payment($pno) => code=$($r.code) msg=$($r.msg)"
Check "A9 read other's payment denied" ($r.code -ne 200)
# A10. control: own order should be readable
$r = GetApi "$base/api/orders/46" $CH
Write-Host "A10. GET own order 46 => code=$($r.code)"
Check "A10 own order readable" ($r.code -eq 200)

Write-Host "=== RESULT: PASS=$pass FAIL=$fail ==="
