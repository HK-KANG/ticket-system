# Phase 6 API benchmark (ASCII only: PowerShell 5.1 reads no-BOM UTF-8 as GBK)
param([string]$anchorT = '', [string]$anchorI = '')
$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:8080'
$out = 'D:\_setup\api-bench.txt'
"" | Out-File -FilePath $out -Encoding utf8

function Tok($u) {
    $b = '{"username":"' + $u + '","password":"123456"}'
    $r = Invoke-WebRequest -Uri "$base/api/auth/login" -Method POST -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($b)) -UseBasicParsing
    return ([Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray()) | ConvertFrom-Json).data.token
}
function Bench($label, $path, $token, $n) {
    $h = @{ Authorization = "Bearer $token" }
    $times = @()
    for ($i = 0; $i -lt $n; $i++) {
        $sw = [Diagnostics.Stopwatch]::StartNew()
        $null = Invoke-WebRequest -Uri ($base + $path) -Headers $h -UseBasicParsing
        $sw.Stop()
        $times += $sw.ElapsedMilliseconds
    }
    $s = $times | Sort-Object
    $line = "{0,-34} n={1}  min={2,5}  med={3,5}  max={4,5}   [{5}]" -f $label, $n, $s[0], $s[[int]($s.Count/2)], $s[$s.Count-1], ($times -join ',')
    $line | Out-File -FilePath $out -Append -Encoding utf8
}

$admin = Tok 'admin'
Bench 'stats (1st, cache miss)' '/api/ticket/stats' $admin 1
Bench 'stats (cached)' '/api/ticket/stats' $admin 5
Bench 'cursor page1' '/api/ticket/cursor?size=20' $admin 5
Bench 'offset page1' '/api/ticket/page?page=1&size=20' $admin 3
Bench 'offset page4500 (deep)' '/api/ticket/page?page=4500&size=20' $admin 3
if ($anchorT -ne '') {
    Bench 'cursor deep (row 90000)' ("/api/ticket/cursor?size=20&lastCreateTime=" + $anchorT + "&lastId=" + $anchorI) $admin 5
}
Bench 'detail 10500' '/api/ticket/10500' $admin 5
Write-Host "DONE"
