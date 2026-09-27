$ErrorActionPreference = "Continue"
$base = "http://localhost:8080"
# 1x1 PNG
$png = [Convert]::FromBase64String("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")
$f = "C:\Users\zp\AppData\Local\Temp\test-upload.png"
[IO.File]::WriteAllBytes($f, $png)

$CT = (Invoke-RestMethod -Uri "$base/api/users/login" -Method Post -ContentType "application/json" -Body '{"username":"13900009999","password":"test12345"}' -TimeoutSec 5).data.token
Write-Host "customer token ok"

# multipart upload via curl (PS5 Invoke-RestMethod -Form not supported)
$url = & curl.exe -s -X POST "$base/api/files/upload" -H "Authorization: Bearer $CT" -F "file=@$f;type=image/png" 2>$null
Write-Host "upload response: $url"
if ($url -match 'uploads/([a-f0-9\-]+\.png)') {
    $img = $Matches[1]
    $r = Invoke-WebRequest -Uri "$base/uploads/$img" -TimeoutSec 5 -UseBasicParsing
    Write-Host "fetch uploaded image => http=$($r.StatusCode) bytes=$($r.RawContentLength)"
    Write-Host "file on disk: $(Test-Path "C:\Users\zp\Desktop\custom\ceramic-platform\platform.back\uploads\$img")"
    # cleanup test file
    Remove-Item "C:\Users\zp\Desktop\custom\ceramic-platform\platform.back\uploads\$img" -ErrorAction SilentlyContinue
    Write-Host "(test file cleaned)"
} else {
    Write-Host "upload FAILED - checking backend log tail"
    Get-Content "C:\Users\zp\AppData\Local\Temp\backend_run.log" -Tail 20 -ErrorAction SilentlyContinue | Select-String -Pattern "ERROR|Exception|upload" | Select-Object -Last 5
}
