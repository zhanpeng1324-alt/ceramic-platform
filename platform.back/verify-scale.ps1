$ErrorActionPreference = "Continue"
$base = "http://localhost:8080"
$sqlFile = "C:\Users\zp\AppData\Local\Temp\seed_batch.sql"

Write-Host "seeding 20000 test products in batches (via sql file)..."
$totalInserted = 0
$batchSize = 1000
$rows = ""
$count = 0
for ($i=1; $i -le 20000; $i++) {
    $name = "perf_test_prd_$i"
    $price = 100.00 + $i
    if ($rows) { $rows += "," }
    $rows += "(5,'$name','p$i','$name', $price, 50, 'active', 0)"
    $count++
    if ($count -ge $batchSize) {
        "INSERT INTO ceramic.products(category_id,name,subtitle,description,price,stock,status,customizable) VALUES $rows;" | Out-File -Encoding ascii $sqlFile
        Get-Content $sqlFile | mysql -u root -p123456 2>$null
        $totalInserted += $count
        $rows = ""; $count = 0
    }
}
if ($rows) {
    "INSERT INTO ceramic.products(category_id,name,subtitle,description,price,stock,status,customizable) VALUES $rows;" | Out-File -Encoding ascii $sqlFile
    Get-Content $sqlFile | mysql -u root -p123456 2>$null
    $totalInserted += $count
}
$cnt = (& mysql -u root -p123456 -N -e "SELECT COUNT(*) FROM ceramic.products" 2>$null)
Write-Host "seed done inserted=$totalInserted total now=$cnt"

$Timings = @{}
function TimeApi($name, $uri) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    try {
        $resp = Invoke-WebRequest -Uri $uri -TimeoutSec 30 -UseBasicParsing
        $dt = $sw.ElapsedMilliseconds
        $Timings[$name] = $dt
        Write-Host "$name => $dt ms http=$($resp.StatusCode) bytes=$($resp.RawContentLength)"
    } catch {
        $dt = $sw.ElapsedMilliseconds
        $Timings[$name] = $dt
        Write-Host "$name => FAILED $dt ms : $($_.Exception.Message)"
    }
}
TimeApi "list_all(2w)"  "http://localhost:8080/api/products"
TimeApi "list_keyword"  "http://localhost:8080/api/products?keyword=perf_test"
TimeApi "list_cat5"     "http://localhost:8080/api/products?categoryId=5"
TimeApi "detail_first"  "http://localhost:8080/api/products/7"
TimeApi "detail_middle" "http://localhost:8080/api/products/10001"
TimeApi "detail_last"   "http://localhost:8080/api/products/20000"
TimeApi "keyword_like"  "http://localhost:8080/api/products?keyword=prd_1500"

Write-Host "---timings---"
$Timings.GetEnumerator() | Sort-Object Name | ForEach-Object { Write-Host ("  {0,-18}: {1} ms" -f $_.Key, $_.Value) }

mysql -u root -p123456 -e "DELETE FROM ceramic.products WHERE name LIKE 'perf_test_%'" 2>$null
$cnt2 = (& mysql -u root -p123456 -N -e "SELECT COUNT(*) FROM ceramic.products" 2>$null)
Write-Host "cleanup done, total=$cnt2 (was $cnt)"
Remove-Item $sqlFile -ErrorAction SilentlyContinue
