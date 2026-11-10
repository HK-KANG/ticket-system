# Concurrency benchmark: 30 parallel requests, compare virtual threads on/off.
# ASCII only. Usage: -Label off|on
param([string]$Label = 'off', [int]$Parallel = 30, [int]$Rounds = 2)
$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:8080'
$out = "D:\_setup\vt-$Label.txt"
"" | Out-File -FilePath $out -Encoding utf8

$b = '{"username":"admin","password":"123456"}'
$r = Invoke-WebRequest -Uri "$base/api/auth/login" -Method POST -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($b)) -UseBasicParsing
$token = ([Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray()) | ConvertFrom-Json).data.token

$path = '/api/ticket/stats'
for ($round = 1; $round -le $Rounds; $round++) {
    $pool = [runspacefactory]::CreateRunspacePool(1, $Parallel)
    $pool.Open()
    $jobs = @()
    $wallSw = [Diagnostics.Stopwatch]::StartNew()
    for ($i = 0; $i -lt $Parallel; $i++) {
        $ps = [powershell]::Create()
        $ps.RunspacePool = $pool
        $null = $ps.AddScript({
            param($u, $t)
            $sw = [Diagnostics.Stopwatch]::StartNew()
            try {
                $null = Invoke-WebRequest -Uri $u -Headers @{ Authorization = "Bearer $t" } -UseBasicParsing
                $sw.Stop()
                return [pscustomobject]@{ ok = $true; ms = $sw.ElapsedMilliseconds }
            } catch {
                $sw.Stop()
                return [pscustomobject]@{ ok = $false; ms = $sw.ElapsedMilliseconds }
            }
        }).AddArgument($base + $path).AddArgument($token)
        $jobs += @{ ps = $ps; h = $ps.BeginInvoke() }
    }
    $res = @()
    foreach ($j in $jobs) { $res += $j.ps.EndInvoke($j.h); $j.ps.Dispose() }
    $wallSw.Stop()
    $pool.Close()
    $pool.Dispose()

    $ms = ($res | Where-Object { $_.ok } | ForEach-Object { $_.ms }) | Sort-Object
    $ok = ($res | Where-Object { $_.ok }).Count
    $wall = $wallSw.ElapsedMilliseconds
    $p95 = $ms[[int][math]::Floor($ms.Count * 0.95)]
    $line = ("{0} round{1}  ok={2}/{3}  wall={4}ms  throughput={5:N1} req/s  min={6} med={7} p95={8} max={9}" -f `
            $Label, $round, $ok, $Parallel, $wall, ($Parallel * 1000.0 / $wall), $ms[0], $ms[[int]($ms.Count/2)], $p95, $ms[$ms.Count-1])
    $line | Out-File -FilePath $out -Append -Encoding utf8
    Write-Host $line
}
Write-Host "DONE"
