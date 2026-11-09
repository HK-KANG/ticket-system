# Phase 5 SLA acceptance helper. ASCII only (PowerShell 5.1 + no-BOM UTF-8 = GBK).
# Usage: -Action create | scan | status
param([string]$Action = 'create')

$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:8080'

function Call($method, $path, $token, $body) {
    $h = @{}
    if ($token) { $h['Authorization'] = "Bearer $token" }
    try {
        if ($body) {
            $r = Invoke-WebRequest -Uri ($base + $path) -Method $method -Headers $h -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body)) -UseBasicParsing
        } else {
            $r = Invoke-WebRequest -Uri ($base + $path) -Method $method -Headers $h -UseBasicParsing
        }
        $txt = [Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
        return New-Object PSObject -Property @{ status = [int]$r.StatusCode; body = $txt }
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $ms = New-Object IO.MemoryStream
            $resp.GetResponseStream().CopyTo($ms)
            return New-Object PSObject -Property @{ status = [int]$resp.StatusCode; body = [Text.Encoding]::UTF8.GetString($ms.ToArray()) }
        }
        return New-Object PSObject -Property @{ status = -1; body = $_.Exception.Message }
    }
}
function Login($u) { return ((Call 'POST' '/api/auth/login' $null ('{"username":"' + $u + '","password":"123456"}')).body | ConvertFrom-Json).data.token }

$zhang = Login 'zhangsan'
$admin = Login 'admin'

if ($Action -eq 'create') {
    # ticket A: category 测试MQ (1-minute SLA rule), tests the MQ delay path
    $ra = 'sla-mq-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
    $ba = '{"title":"sla mq test","content":"one minute sla, should escalate by mq","category":"\u6d4b\u8bd5MQ","priority":"P0","requestId":"' + $ra + '"}'
    $a = (Call 'POST' '/api/ticket/create' $zhang $ba).body | ConvertFrom-Json
    Write-Host ("A id=" + $a.data.id + " no=" + $a.data.ticketNo + " sla=" + $a.data.slaDeadline + " status=" + $a.data.status)

    # ticket B: refund P0 (30-min SLA); will be backdated in DB to test the scan path
    $rb = 'sla-scan-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
    $bb = '{"title":"sla scan test","content":"backdated deadline, should escalate by scan","category":"\u9000\u6b3e","priority":"P0","requestId":"' + $rb + '"}'
    $b = (Call 'POST' '/api/ticket/create' $zhang $bb).body | ConvertFrom-Json
    Write-Host ("B id=" + $b.data.id + " no=" + $b.data.ticketNo + " sla=" + $b.data.slaDeadline + " status=" + $b.data.status)
}

if ($Action -eq 'scan') {
    $r1 = Call 'POST' '/api/job/sla-scan' $admin $null
    $j1 = $r1.body | ConvertFrom-Json
    Write-Host ("scan #1: status=" + $r1.status + " code=" + $j1.code + " escalated=" + $j1.data)
    $r2 = Call 'POST' '/api/job/sla-scan' $admin $null
    $j2 = $r2.body | ConvertFrom-Json
    Write-Host ("scan #2: status=" + $r2.status + " code=" + $j2.code + " escalated=" + $j2.data)
}

if ($Action -eq 'status') {
    foreach ($id in $args) {
        $r = Call 'GET' ("/api/ticket/" + $id) $admin $null
        $t = ($r.body | ConvertFrom-Json).data
        $esc = @($t.logs | Where-Object { $_.action -eq 'ESCALATE' }).Count
        Write-Host ("id=" + $id + " status=" + $t.status + " handler=" + $t.handlerId + " escalateLogs=" + $esc + " logActions=" + (($t.logs | ForEach-Object { $_.action }) -join ','))
    }
}
