# Phase 3 ticket flow acceptance script
# ASCII ONLY. PowerShell 5.1 reads a no-BOM UTF-8 file as GBK; Chinese text here
# corrupts quotes and the parser dies. Chinese literals are written as \uXXXX escapes.
# Usage: powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verify-ticket.ps1

$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:8080'
$script:fail = 0
$script:pass = 0

# build the Chinese literals from char codes: PowerShell 5.1 has no \uXXXX escape,
# and literal Chinese in a no-BOM UTF-8 file would be decoded as GBK and break parsing.
# 0x552E 0x540E 0x4E00 0x7EC4 = after-sales group 1
$GROUP_1 = [string]([char]0x552E) + [char]0x540E + [char]0x4E00 + [char]0x7EC4

function Call($method, $path, $token, $body) {
    $h = @{}
    if ($token) { $h['Authorization'] = "Bearer $token" }
    try {
        if ($body) {
            $r = Invoke-WebRequest -Uri ($base + $path) -Method $method -Headers $h -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body)) -UseBasicParsing
        } else {
            $r = Invoke-WebRequest -Uri ($base + $path) -Method $method -Headers $h -UseBasicParsing
        }
        # Spring returns application/json WITHOUT charset, and PowerShell 5.1 then decodes
        # the bytes as ISO-8859-1 -> every Chinese field turns into mojibake. Decode UTF-8 explicitly.
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

function Check($name, $cond, $detail) {
    if ($cond) { Write-Host ("  PASS  " + $name + "  [" + $detail + "]"); $script:pass++ }
    else { Write-Host ("  FAIL  " + $name + "  [" + $detail + "]"); $script:fail++ }
}

function Login($u) {
    $r = Call 'POST' '/api/auth/login' $null ('{"username":"' + $u + '","password":"123456"}')
    $j = $r.body | ConvertFrom-Json
    return $j.data.token
}

$zhang = Login 'zhangsan'
$limei = Login 'limei'
$lisi  = Login 'lisi'
$admin = Login 'admin'
Check 'login-4-users' ($zhang -and $limei -and $lisi -and $admin) 'zhangsan/limei/lisi/admin'

# ---------- T1 create ----------
$rid = 'req-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
# category is sent as a JSON \u escape: JSON decodes it server side, so the file stays ASCII
$payload = '{"title":"autotest refund request","content":"autotest order A12345 not received, please refund","category":"\u9000\u6b3e","priority":"P0","requestId":"' + $rid + '"}'
$r = Call 'POST' '/api/ticket/create' $zhang $payload
$j = $r.body | ConvertFrom-Json
$ticketId = $j.data.id
$ticketNo = $j.data.ticketNo
Check 'T1-create' ($r.status -eq 200 -and $j.code -eq 0 -and $j.data.status -eq 'PENDING') ("no=" + $ticketNo + " status=" + $j.data.status)
Check 'T1-ticket-no-format' ($ticketNo -match '^TK\d{8}\d{6}$') ("no=" + $ticketNo)
Check 'T1-sla-from-rule' ($null -ne $j.data.slaDeadline) ("sla=" + $j.data.slaDeadline)
Check 'T1-skill-group' ($j.data.skillGroup -eq $GROUP_1) ("group=" + $j.data.skillGroup)

# ---------- T2 idempotency: 4 more identical requests ----------
$ids = @()
for ($i = 0; $i -lt 4; $i++) {
    $r = Call 'POST' '/api/ticket/create' $zhang $payload
    $j = $r.body | ConvertFrom-Json
    $ids += $j.data.ticketNo
}
$same = ($ids | Where-Object { $_ -eq $ticketNo }).Count
Check 'T2-idempotent-5-times' ($same -eq 4) ("same ticketNo on " + $same + "/4 retries")

$r = Call 'GET' "/api/ticket/page?page=1&size=100" $admin $null
$j = $r.body | ConvertFrom-Json
$count = @($j.data.records | Where-Object { $_.ticketNo -eq $ticketNo }).Count
Check 'T2-only-one-row' ($count -eq 1) ("rows with this ticketNo = " + $count)

# ---------- T3/T4/T5 role isolation ----------
$r = Call 'GET' "/api/ticket/page?page=1&size=100" $zhang $null
$j = $r.body | ConvertFrom-Json
Check 'T3-customer-sees-own' ($j.data.total -ge 1) ("total=" + $j.data.total)

$r = Call 'GET' "/api/ticket/page?page=1&size=100" $limei $null
$j = $r.body | ConvertFrom-Json
Check 'T4-other-customer-cannot-see' ($j.data.total -eq 0) ("limei total=" + $j.data.total)

$r = Call 'GET' ("/api/ticket/" + $ticketId) $limei $null
$j = $r.body | ConvertFrom-Json
Check 'T5-detail-404-for-other-customer' ($r.status -eq 404 -or $j.code -eq 404) ("status=" + $r.status + " code=" + $j.code)

# ---------- T6 agent sees group pool ----------
$r = Call 'GET' "/api/ticket/page?status=PENDING&page=1&size=100" $lisi $null
$j = $r.body | ConvertFrom-Json
$seen = @($j.data.records | Where-Object { $_.ticketNo -eq $ticketNo }).Count
Check 'T6-agent-sees-group-pending' ($seen -eq 1) ("lisi sees " + $seen + " of this ticket")

# ---------- T7/T8 comments ----------
$r = Call 'POST' ("/api/ticket/" + $ticketId + "/comment") $zhang '{"content":"autotest customer follow-up: order id provided","internal":false}'
Check 'T7-customer-comment' ($r.status -eq 200) ("status=" + $r.status)

$r = Call 'POST' ("/api/ticket/" + $ticketId + "/comment") $lisi '{"content":"autotest internal note: parcel may be lost","internal":true}'
Check 'T8-agent-internal-comment' ($r.status -eq 200) ("status=" + $r.status)

$r = Call 'POST' ("/api/ticket/" + $ticketId + "/comment") $zhang '{"content":"autotest customer tries internal flag","internal":true}'
Check 'T8b-customer-internal-downgraded' ($r.status -eq 200) ("status=" + $r.status + " server must force internal=0")

$r = Call 'GET' ("/api/ticket/" + $ticketId) $zhang $null
$j = $r.body | ConvertFrom-Json
$custInternal = @($j.data.comments | Where-Object { $_.isInternal -eq 1 }).Count
Check 'T9-customer-cannot-see-internal' ($custInternal -eq 0) ("internal visible to customer = " + $custInternal + ", comments=" + $j.data.comments.Count)

$r = Call 'GET' ("/api/ticket/" + $ticketId) $lisi $null
$j = $r.body | ConvertFrom-Json
$agentInternal = @($j.data.comments | Where-Object { $_.isInternal -eq 1 }).Count
Check 'T10-agent-sees-internal' ($agentInternal -ge 1) ("internal visible to agent = " + $agentInternal)

# ---------- T11 illegal transition ----------
$r = Call 'POST' ("/api/ticket/" + $ticketId + "/close") $zhang '{"resolution":"autotest try close while pending"}'
$j = $r.body | ConvertFrom-Json
Check 'T11-illegal-pending-to-closed' ($j.code -ne 0) ("code=" + $j.code)

# ---------- T12 transfer by admin ----------
$r = Call 'GET' '/api/user/agents' $admin $null
$j = $r.body | ConvertFrom-Json
$target = ($j.data | Where-Object { $_.username -eq 'zhaoliu' }).id
Check 'T12a-agent-list' ($null -ne $target) ("zhaoliu id=" + $target)

$r = Call 'POST' ("/api/ticket/" + $ticketId + "/transfer") $admin ('{"toHandlerId":' + $target + ',"reason":"TRANSFER"}')
$j = $r.body | ConvertFrom-Json
Check 'T12b-transfer-by-admin' ($r.status -eq 200) ("status=" + $r.status + " code=" + $j.code)

$r = Call 'GET' ("/api/ticket/" + $ticketId) $admin $null
$j = $r.body | ConvertFrom-Json
Check 'T12c-status-assigned' ($j.data.status -eq 'ASSIGNED') ("status=" + $j.data.status + " handler=" + $j.data.handlerName)

$r = Call 'GET' ("/api/ticket/" + $ticketId) $lisi $null
$j = $r.body | ConvertFrom-Json
Check 'T12d-old-group-lost-access' ($r.status -eq 404 -or $j.code -eq 404) ("lisi status=" + $r.status + " code=" + $j.code)

# ---------- T13 close by new handler ----------
$zhao = Login 'zhaoliu'
$r = Call 'POST' ("/api/ticket/" + $ticketId + "/close") $zhao '{"resolution":"autotest refund done"}'
$j = $r.body | ConvertFrom-Json
Check 'T13-close-by-handler' ($r.status -eq 200) ("status=" + $r.status + " code=" + $j.code)

$r = Call 'GET' ("/api/ticket/" + $ticketId) $admin $null
$j = $r.body | ConvertFrom-Json
Check 'T13b-status-closed' ($j.data.status -eq 'CLOSED') ("status=" + $j.data.status)
$actions = ($j.data.logs | ForEach-Object { $_.action }) -join ','
Check 'T13c-log-chain' ($actions -match 'CREATE' -and $actions -match 'TRANSFER' -and $actions -match 'CLOSE') ("logs=" + $actions)

# ---------- T14 six-condition query ----------
$r = Call 'GET' "/api/ticket/page?category=%E9%80%80%E6%AC%BE&status=CLOSED&priority=P0&page=1&size=10" $admin $null
$j = $r.body | ConvertFrom-Json
Check 'T14-six-conditions' ($j.data.total -ge 1) ("refund+CLOSED+P0 total=" + $j.data.total)

$r = Call 'GET' "/api/ticket/page?keyword=autotest&page=1&size=10" $admin $null
$j = $r.body | ConvertFrom-Json
Check 'T14b-keyword' ($j.data.total -ge 1) ("keyword=autotest total=" + $j.data.total)

$r = Call 'GET' "/api/ticket/page?handlerId=999999&page=1&size=10" $admin $null
$j = $r.body | ConvertFrom-Json
Check 'T14c-no-match' ($j.data.total -eq 0) ("handlerId=999999 total=" + $j.data.total)

# ---------- T15 stats ----------
$r = Call 'GET' '/api/ticket/stats' $admin $null
$j = $r.body | ConvertFrom-Json
Check 'T15-stats-admin' ($r.status -eq 200 -and $j.data.total -ge 1) ("total=" + $j.data.total + " slaRate=" + $j.data.slaAchieveRate)

$r = Call 'GET' '/api/ticket/stats' $zhang $null
Check 'T15b-stats-forbidden' ($r.status -eq 403) ("customer status=" + $r.status)

Write-Host ""
Write-Host ("RESULT: " + $script:pass + " passed, " + $script:fail + " failed")
if ($script:fail -eq 0) { exit 0 } else { exit 1 }
