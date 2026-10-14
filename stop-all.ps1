# Stop all EthioLoadAI project services
$ErrorActionPreference = 'SilentlyContinue'

Write-Host "Stopping EthioLoadAI services..." -ForegroundColor Yellow

$ports = @(8080, 8000, 5173, 5174, 8765)
foreach ($port in $ports) {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($conns) {
        foreach ($conn in $conns) {
            $pidToKill = $conn.OwningProcess
            if ($pidToKill -and $pidToKill -ne 0) {
                Write-Host "Terminating process on port $port (PID: $pidToKill)..." -ForegroundColor Cyan
                Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
            }
        }
    }
}

Write-Host "Services stopped." -ForegroundColor Green
