# Phase 2.1 auth acceptance script
# NOTE: ASCII only on purpose. Windows PowerShell 5.1 reads a UTF-8 file WITHOUT BOM
# as GBK; Chinese comments then corrupt parsing and silently swallow code lines.
# Usage (WSL): powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-auth.ps1
# Usage (Windows): .\scripts\verify-auth.ps1
# Requires: backend running on 127.0.0.1:8080 and V2 seed data applied.

$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:8080'
$script:fail = 0

function Call($method, $path, $token, $body) {
    $h = @{}
    if ($token) { $h['Authorization'] = "Bearer $token" }
    try {
        if ($body) {
            $r = Invoke-WebRequest -Uri ($base + $path) -Method $method -Headers $h -ContentType 'application/json' -Body $body -UseBasicParsing
        } else {
            $r = Invoke-WebRequest -Uri ($base + $path) -Method $method -Headers $h -UseBasicParsing
        }
        return New-Object PSObject -Property @{ status = [int]$r.StatusCode; body = $r.Content }
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $sr = New-Object IO.StreamReader($resp.GetResponseStream())
            return New-Object PSObject -Property @{ status = [int]$resp.StatusCode; body = $sr.ReadToEnd() }
        }
        return New-Object PSObject -Property @{ status = -1; body = $_.Exception.Message }
    }
}

function Check($name, $cond, $detail) {
    if ($cond) { Write-Host ("  PASS  " + $name + "  [" + $detail + "]") }
    else { Write-Host ("  FAIL  " + $name + "  [" + $detail + "]"); $script:fail++ }
}

# 1. login
$r = Call 'POST' '/api/auth/login' $null '{"username":"zhangsan","password":"123456"}'
$j = $r.body | ConvertFrom-Json
$token = $j.data.token
Check 'login' ($r.status -eq 200 -and $j.code -eq 0 -and $j.data.user.role -eq 'CUSTOMER') ("status=" + $r.status + " role=" + $j.data.user.role + " tokenLen=" + $token.Length)

# 2. me with token
$r = Call 'GET' '/api/auth/me' $token $null
$j = $r.body | ConvertFrom-Json
Check 'me' ($r.status -eq 200 -and $j.data.username -eq 'zhangsan') ("status=" + $r.status + " username=" + $j.data.username)
Check 'me-no-password' ($null -eq $j.data.password) 'password field absent'

# 3. no token -> 401 with unified Result + traceId
$r = Call 'GET' '/api/auth/me' $null $null
$j = $r.body | ConvertFrom-Json
Check 'no-token-401' ($r.status -eq 401 -and $j.code -eq 401 -and $j.traceId.Length -eq 16) ("status=" + $r.status + " traceIdLen=" + $j.traceId.Length)

# 4. role check -> 403
$r = Call 'GET' '/api/user/list' $token $null
$j = $r.body | ConvertFrom-Json
Check 'customer-forbidden-403' ($r.status -eq 403 -and $j.code -eq 403) ("status=" + $r.status)

# 5. wrong password -> business code 1000
$r = Call 'POST' '/api/auth/login' $null '{"username":"zhangsan","password":"wrong"}'
$j = $r.body | ConvertFrom-Json
Check 'wrong-password-1000' ($j.code -eq 1000) ("code=" + $j.code)

# 6. admin sees 6 accounts
$r = Call 'POST' '/api/auth/login' $null '{"username":"admin","password":"123456"}'
$j = $r.body | ConvertFrom-Json
$adminToken = $j.data.token
$r = Call 'GET' '/api/user/list' $adminToken $null
$j = $r.body | ConvertFrom-Json
Check 'admin-user-list' ($r.status -eq 200 -and $j.data.Count -eq 6) ("status=" + $r.status + " count=" + $j.data.Count)

# 7. logout revokes token (Redis)
$null = Call 'POST' '/api/auth/logout' $adminToken $null
$r = Call 'GET' '/api/auth/me' $adminToken $null
Check 'token-revoked-after-logout' ($r.status -eq 401) ("reuseStatus=" + $r.status)

# 8. bean validation -> 400
$r = Call 'POST' '/api/auth/login' $null '{"username":"zhangsan","password":""}'
$j = $r.body | ConvertFrom-Json
Check 'validation-400' ($j.code -eq 400) ("code=" + $j.code)

Write-Host ""
if ($script:fail -eq 0) { Write-Host "RESULT: all auth checks passed"; exit 0 }
Write-Host ("RESULT: " + $script:fail + " check(s) failed"); exit 1
