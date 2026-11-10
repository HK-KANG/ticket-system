# Start a headless Chrome with CDP on port 9222 (for scripts/ui-smoke.mjs).
# ASCII only: PowerShell 5.1 reads no-BOM UTF-8 as GBK.
$chrome = 'C:\Users\kh\AppData\Local\Google\Chrome\Application\chrome.exe'
if (-not (Test-Path $chrome)) { Write-Host "chrome.exe not found: $chrome"; exit 1 }
$profile = 'D:\_setup\cdp-profile'
$args = @(
    '--headless=new',
    '--remote-debugging-port=9222',
    "--user-data-dir=$profile",
    '--no-first-run',
    '--window-size=1440,900',
    'about:blank'
)
Start-Process -FilePath $chrome -ArgumentList $args -WindowStyle Hidden
Start-Sleep -Seconds 4
try {
    $v = Invoke-WebRequest -Uri 'http://127.0.0.1:9222/json/version' -UseBasicParsing -TimeoutSec 5
    Write-Host "CDP ready on 9222"
} catch {
    Write-Host "CDP not reachable, check chrome process"
}
