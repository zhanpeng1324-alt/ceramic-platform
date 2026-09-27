param([string]$Path, [string]$Label)
Write-Host "===== $Label ====="
$rows = Import-Csv $Path
$ok = ($rows | Where-Object success -eq 'True').Count
$fail = ($rows | Where-Object success -eq 'False').Count
Write-Host "总样本: $($rows.Count)  成功: $ok  失败: $fail"
$groups = $rows | Group-Object label | Sort-Object Count -Descending
foreach ($g in $groups) {
    $items = $g.Group
    $times = $items | ForEach-Object { [int]$_.elapsed } | Sort-Object
    $total = $items.Count
    $avg = [math]::Round(($times | Measure-Object -Average).Average, 1)
    $p90 = $times[[math]::Floor($times.Count * 0.9)]
    $p95 = $times[[math]::Floor($times.Count * 0.95)]
    $start = ($items | Measure-Object -Property timeStamp -Minimum).Minimum
    $end = ($items | Measure-Object -Property timeStamp -Maximum).Maximum
    $qps = [math]::Round($total / [math]::Max(1, ($end - $start) / 1000.0), 1)
    $max = $times[-1]
    Write-Host ("  {0,-28} QPS={1,8}  Avg={2,7}ms  P90={3,5}ms  P95={4,5}ms  Max={5,5}ms  n={6}" -f $g.Name, $qps, $avg, $p90, $p95, $max, $total)
}
Write-Host ""
