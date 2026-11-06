# Phase 4 concurrency experiment: 20 parallel accepts on ONE ticket, both implementations.
# ASCII only (PowerShell 5.1 reads no-BOM UTF-8 as GBK and would corrupt Chinese).
# Usage: powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verify-concurrency.ps1
# Requires: backend on 127.0.0.1:8080, at least one AGENT in the ticket's skill group.

$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:8080'
$PARALLEL = 20
$script:fail = 0

function Call($method, $path, $token, $body) {
    $h = @{}
    if ($token) { $h['Authorization'] = "Bearer $token" }
    try {
        if ($body) {
            $r = Invoke-WebRequest -Uri ($base + $path) -Method $method -Headers $h -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body)) -UseBasicParsing
        } else {
            $r = Invoke-WebRequest -Uri ($base + $path) -Method $method -Headers $h -UseBasicParsing
        }
        $text = [Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
        return New-Object PSObject -Property @{ status = [int]$r.StatusCode; body = $text }
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $ms = New-Object IO.MemoryStream
            $resp.GetResponseStream().CopyTo($ms)
            $text = [Text.Encoding]::UTF8.GetString($ms.ToArray())
            return New-Object PSObject -Property @{ status = [int]$resp.StatusCode; body = $text }
        }
        return New-Object PSObject -Property @{ status = -1; body = $_.Exception.Message }
    }
}

function Login($u) {
    return ((Call 'POST' '/api/auth/login' $null ('{"username":"' + $u + '","password":"123456"}')).body | ConvertFrom-Json).data.token
}

function NewTicket($zhang) {
    $rid = 'conc-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
    $payload = '{"title":"concurrency experiment","content":"20 parallel accepts on this ticket, refund category","category":"\u9000\u6b3e","priority":"P0","requestId":"' + $rid + '"}'
    $r = Call 'POST' '/api/ticket/create' $zhang $payload
    return ($r.body | ConvertFrom-Json).data
}

# Fire N parallel POSTs to the same path, return per-request results
function Fire($path, $token, $n) {
    $pool = [runspacefactory]::CreateRunspacePool(1, $n)
    $pool.Open()
    $jobs = @()
    for ($i = 0; $i -lt $n; $i++) {
        $ps = [powershell]::Create()
        $ps.RunspacePool = $pool
        $null = $ps.AddScript({
            param($b, $p, $t)
            $sw = [Diagnostics.Stopwatch]::StartNew()
            try {
                $r = Invoke-WebRequest -Uri ($b + $p) -Method POST -Headers @{ Authorization = "Bearer $t" } -UseBasicParsing
                $sw.Stop()
                $txt = [Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
                $c = ($txt | ConvertFrom-Json).code
                return [pscustomobject]@{ code = $c; ms = $sw.ElapsedMilliseconds }
            } catch {
                $sw.Stop()
                $resp = $_.Exception.Response
                $c = -1
                if ($resp) {
                    $ms2 = New-Object IO.MemoryStream
                    $resp.GetResponseStream().CopyTo($ms2)
                    $obj = [Text.Encoding]::UTF8.GetString($ms2.ToArray()) | ConvertFrom-Json
                    if ($null -ne $obj.code) { $c = $obj.code } else { $c = [int]$resp.StatusCode }
                }
                return [pscustomobject]@{ code = $c; ms = $sw.ElapsedMilliseconds }
            }
        }).AddArgument($base).AddArgument($path).AddArgument($token)
        $jobs += @{ ps = $ps; h = $ps.BeginInvoke() }
    }
    $out = @()
    foreach ($j in $jobs) {
        $out += $j.ps.EndInvoke($j.h)
        $j.ps.Dispose()
    }
    $pool.Close()
    return $out
}

function Report($label, $results) {
    $ok = @($results | Where-Object { $_.code -eq 0 })
    $fail = @($results | Where-Object { $_.code -ne 0 })
    $all = $results.ms | Sort-Object
    $avg = [math]::Round(($results | Measure-Object -Property ms -Average).Average, 1)
    $p95 = $all[[math]::Min($all.Count - 1, [int][math]::Floor($all.Count * 0.95))]
    Write-Host ("  " + $label + ": success=" + $ok.Count + " failed=" + $fail.Count +
                "  ms avg=" + $avg + " min=" + $all[0] + " p95=" + $p95 + " max=" + $all[$all.Count - 1])
    if ($ok.Count -ne 1) { Write-Host ("  FAIL " + $label + " expected exactly 1 success, got " + $ok.Count); $script:fail++ }
    return [pscustomobject]@{ label = $label; success = $ok.Count; failed = $fail.Count; avg = $avg; min = $all[0]; p95 = $p95; max = $all[$all.Count - 1] }
}

$zhang = Login 'zhangsan'
$lisi = Login 'lisi'
$admin = Login 'admin'

Write-Host ("Phase 4 concurrency experiment, parallel=" + $PARALLEL + ", same ticket each round")

# ---------- round 1: optimistic lock ----------
$t1 = NewTicket $zhang
Write-Host ("  ticket A (optimistic) id=" + $t1.id + " no=" + $t1.ticketNo)
$r1 = Report 'A-optimistic' (Fire ("/api/ticket/" + $t1.id + "/accept") $lisi $PARALLEL)

# ---------- round 2: redis lock ----------
$t2 = NewTicket $zhang
Write-Host ("  ticket B (redis lock) id=" + $t2.id + " no=" + $t2.ticketNo)
$r2 = Report 'B-redis-lock' (Fire ("/api/ticket/" + $t2.id + "/accept-lock") $lisi $PARALLEL)

# ---------- state check: nobody double-assigned ----------
$d1 = (Call 'GET' ("/api/ticket/" + $t1.id) $admin $null).body | ConvertFrom-Json
$d2 = (Call 'GET' ("/api/ticket/" + $t2.id) $admin $null).body | ConvertFrom-Json
Write-Host ("  ticket A final: status=" + $d1.data.status + " handlerId=" + $d1.data.handlerId)
Write-Host ("  ticket B final: status=" + $d2.data.status + " handlerId=" + $d2.data.handlerId)
if ($d1.data.handlerId -and $d2.data.handlerId) { Write-Host "  OK   both tickets have exactly one handler" } else { Write-Host "  FAIL a ticket ended without handler"; $script:fail++ }

# count ACCEPT logs: must be exactly 1 per ticket (no double processing)
$a1 = @($d1.data.logs | Where-Object { $_.action -eq 'ACCEPT' }).Count
$a2 = @($d2.data.logs | Where-Object { $_.action -eq 'ACCEPT' }).Count
Write-Host ("  ACCEPT log rows: A=" + $a1 + " B=" + $a2 + " (must be 1 each)")
if ($a1 -ne 1 -or $a2 -ne 1) { Write-Host "  FAIL duplicate ACCEPT log detected"; $script:fail++ }

# ---------- round 3: auto dispatch ----------
$t3 = NewTicket $zhang
$r = Call 'POST' ("/api/ticket/" + $t3.id + "/auto-dispatch") $admin $null
$j = $r.body | ConvertFrom-Json
Write-Host ("  C-auto-dispatch: status=" + $r.status + " code=" + $j.code)
if ($r.status -eq 200 -and $j.code -eq 0) { Write-Host "  OK   auto dispatch assigned" } else { Write-Host "  FAIL auto dispatch failed"; $script:fail++ }
$d3 = (Call 'GET' ("/api/ticket/" + $t3.id) $admin $null).body | ConvertFrom-Json
Write-Host ("  ticket C final: status=" + $d3.data.status + " handlerId=" + $d3.data.handlerId)
if ($d3.data.handlerId) { Write-Host "  OK   dispatch target set" } else { Write-Host "  FAIL dispatch target missing"; $script:fail++ }

# ---------- round 4: resolve ----------
$r = Call 'POST' ("/api/ticket/" + $t1.id + "/resolve") $lisi '{"resolution":"concurrency test resolved"}'
$j = $r.body | ConvertFrom-Json
Write-Host ("  D-resolve: status=" + $r.status + " code=" + $j.code)
if ($j.code -eq 0) { Write-Host "  OK   resolve works" } else { Write-Host ("  FAIL resolve: " + $j.message); $script:fail++ }

Write-Host ""
Write-Host "MEASURED DATA (feed into docs/concurrency-compare.md)"
Write-Host ("  " + $r1.label + " | success=" + $r1.success + " | failed=" + $r1.failed + " | avg=" + $r1.avg + "ms | min=" + $r1.min + " | p95=" + $r1.p95 + " | max=" + $r1.max)
Write-Host ("  " + $r2.label + " | success=" + $r2.success + " | failed=" + $r2.failed + " | avg=" + $r2.avg + "ms | min=" + $r2.min + " | p95=" + $r2.p95 + " | max=" + $r2.max)

Write-Host ""
Write-Host ("RESULT: " + $script:fail + " failed")
if ($script:fail -eq 0) { exit 0 } else { exit 1 }
