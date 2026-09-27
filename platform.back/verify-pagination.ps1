$ErrorActionPreference = "Continue"
$base = "http://localhost:8080"

Write-Host "seeding 20000 products..."
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("INSERT INTO ceramic.products(category_id,name,subtitle,description,price,stock,status,customizable) VALUES")
for ($i=1; $i -le 20000; $i++) {
    $name = "perf_test_prd_$i"
    $price = 100.00 + $i
    $row = "(5,'$name','p$i','$name', $price, 50, 'active', 0)"
    if ($i -lt 20000) { $row += "," }
    [void]$sb.AppendLine($row)
    if ($i % 5000 -eq 0) { Write-Host "  built $i rows..." }
}
$f = "C:\Users\zp\AppData\Local\Temp\seed20k.sql"
$sb.ToString() | Out-File -Encoding ascii $f
Write-Host "sql file ready ($([math]::Round((Get-Item $f).Length/1MB,1)) MB), importing..."
Get-Content $f | mysql -u root -p123456 2>$null
$cnt = (& mysql -u root -p123456 -N -e "SELECT COUNT(*) FROM ceramic.products" 2>$null)
Write-Host "import done, total=$cnt"

$Timings = @{}
function TimeApi($name, $uri) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    try {
        $resp = Invoke-WebRequest -Uri $uri -TimeoutSec 60 -UseBasicParsing
        $dt = $sw.ElapsedMilliseconds
        $Timings[$name] = $dt
        Write-Host "$name => $dt ms http=$($resp.StatusCode) bytes=$($resp.RawContentLength)"
    } catch {
        $dt = $sw.ElapsedMilliseconds
        $Timings[$name] = $dt
        Write-Host "$name => FAILED $dt ms : $($_.Exception.Message)"
    }
}

Write-Host "=== BEFORE (old full-list mode, no page param) ==="
TimeApi "old_full_list" "$base/api/products"

Write-Host "=== AFTER (paged) ==="
TimeApi "paged_p1"   "$base/api/products?page=1&size=20"
TimeApi "paged_p2"   "$base/api/products?page=2&size=20"
TimeApi "paged_deep" "$base/api/products?page=1000&size=20"
TimeApi "paged_kw"   "$base/api/products?page=1&size=20&keyword=perf_test"
TimeApi "paged_cat"  "$base/api/products?page=1&size=20&categoryId=5"
TimeApi "paged_sort" "$base/api/products?page=1&size=20&sort=price_asc"
TimeApi "detail_10001" "$base/api/products/10001"

Write-Host "---timings---"
$Timings.GetEnumerator() | Sort-Object Name | ForEach-Object { Write-Host ("  {0,-16}: {1} ms" -f $_.Key, $_.Value) }

# cleanup
Write-Host "cleaning up..."
mysql -u root -p123456 -e "DELETE FROM ceramic.products WHERE name LIKE 'perf_test_%'" 2>$null
$cnt2 = (& mysql -u root -p123456 -N -e "SELECT COUNT(*) FROM ceramic.products" 2>$null)
Write-Host "cleanup done, total=$cnt2"
Remove-Item $f -ErrorAction SilentlyContinue
