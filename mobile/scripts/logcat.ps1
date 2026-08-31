<#
.SYNOPSIS
    Mostra so os logs do Notify Share, incluindo stack traces de crash.

.DESCRIPTION
    Filtrar por PID em vez de por tag e o que faz a diferenca: um crash sai com
    a tag AndroidRuntime, nao com a tag do app, entao filtro por tag esconde
    justamente o que voce mais precisa ver.

    Se o app ainda nao estiver aberto, o script espera ele subir.

.EXAMPLE
    .\scripts\logcat.ps1
#>
param(
    [string]$Package = "com.notifyshare.debug",
    [int]   $WaitSeconds = 60
)

$ErrorActionPreference = "Stop"

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "adb nao encontrado em $adb" }

Write-Host "Procurando o processo de $Package..." -ForegroundColor Cyan

$pidValue = $null
for ($i = 0; $i -lt $WaitSeconds; $i++) {
    $found = (& $adb shell pidof $Package) -replace '\s', ''
    if ($found) { $pidValue = $found; break }
    if ($i -eq 0) { Write-Host "App fechado. Esperando abrir..." -ForegroundColor DarkGray }
    Start-Sleep -Seconds 1
}

if (-not $pidValue) {
    Write-Host "O app nao abriu em $WaitSeconds segundos. Rode .\scripts\run.ps1" -ForegroundColor Red
    exit 1
}

# Limpa o buffer para nao despejar o historico inteiro na tela.
& $adb logcat -c

Write-Host "Seguindo o PID $pidValue. Ctrl+C encerra." -ForegroundColor Green
Write-Host ""

& $adb logcat --pid=$pidValue -v brief
