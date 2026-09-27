$ErrorActionPreference = "Continue"
$base = "http://localhost:8080"
function Query($sql) { (& mysql -u root -p123456 -N -e $sql) 2>$null }

$CT = (Invoke-RestMethod -Uri "$base/api/users/login" -Method Post -ContentType "application/json" -Body '{"username":"13900009999","password":"test12345"}' -TimeoutSec 5).data.token
$AT = (Get-Content "C:\Users\zp\AppData\Local\Temp\admin_tok2.txt" -Raw).Trim()
$CH = @{ Authorization = "Bearer $CT" }
$AH = @{ Authorization = "Bearer $AT" }

$pass = 0; $fail = 0
function Check($n, $c) { if ($c) { $script:pass++; Write-Host "   PASS: $n" } else { $script:fail++; Write-Host "   FAIL: $n" } }
function GetP6 { (Invoke-RestMethod -Uri "$base/api/products/6" -Headers $CH -TimeoutSec 5).data.product }

Write-Host "=== cache consistency: update-via-API must evict ==="
$p1 = GetP6
$origPrice = [decimal]$p1.price
Write-Host "1. warm read, product 6 price=$origPrice"
$inRedis = (& C:\Users\zp\redis\redis-cli.exe EXISTS product:detail:6) 2>$null
Write-Host "2. redis has product:detail:6 => $inRedis"
Check "cache warmed" ($inRedis -eq "1")

$p2 = GetP6
Write-Host "3. cached read price=$($p2.price)"

# admin updates price via API
$p2.price = 666.66
$u = Invoke-RestMethod -Uri "$base/api/products/6" -Method Put -Headers $AH -ContentType "application/json" -Body ($p2 | ConvertTo-Json -Depth 5) -TimeoutSec 5
Write-Host "4. admin PUT price=666.66 => code=$($u.code) msg=$($u.msg)"

$inRedis2 = (& C:\Users\zp\redis\redis-cli.exe EXISTS product:detail:6) 2>$null
Write-Host "5. redis product:detail:6 after update => $inRedis2 (expect 0)"
Check "cache evicted on write" ($inRedis2 -eq "0")

$p3 = GetP6
Write-Host "6. post-update read price=$($p3.price) (db truth: $(Query "SELECT price FROM ceramic.products WHERE id=6"))"
Check "fresh value after eviction (no stale read)" ("$($p3.price)" -eq "666.66")

$inRedis3 = (& C:\Users\zp\redis\redis-cli.exe EXISTS product:detail:6) 2>$null
Check "cache rebuilt on next read" ($inRedis3 -eq "1")

Write-Host "=== dirty-read window: bypass API, direct DB write ==="
mysql -u root -p123456 -e "UPDATE ceramic.products SET price=777.77 WHERE id=6" 2>$null
$p4 = GetP6
Write-Host "7. after direct-DB write, cached read price=$($p4.price) (db=777.77)"
Check "stale window exists (known trade-off, TTL-bounded)" ("$($p4.price)" -eq "666.66")

# restore price via API
$p4.price = $origPrice
$u2 = Invoke-RestMethod -Uri "$base/api/products/6" -Method Put -Headers $AH -ContentType "application/json" -Body ($p4 | ConvertTo-Json -Depth 5) -TimeoutSec 5
$p5 = GetP6
Write-Host "8. restored price=$($p5.price) (orig=$origPrice)"
Check "price restored" ([decimal]$p5.price -eq $origPrice)

Write-Host "=== RESULT: PASS=$pass FAIL=$fail ==="
