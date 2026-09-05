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
    .\scripts\logcat.ps1 -Serial 6baca47f   # com 2+ aparelhos, escolha um
#>
param(
    [string]$Package = "com.notifyshare.debug",
    [int]   $WaitSeconds = 60,
    [string]$Serial
)

$ErrorActionPreference = "Stop"

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "adb nao encontrado em $adb" }

# -s <serial> em todas as chamadas quando ha mais de um aparelho.
$dev = @()
if ($Serial) {
    $dev = @("-s", $Serial)
} else {
    $ready = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "^\S+\s+device$" })
    if ($ready.Count -gt 1) {
        Write-Host "$($ready.Count) aparelhos conectados. Rode com -Serial <id>:" -ForegroundColor Yellow
        $ready | ForEach-Object { Write-Host "  $(($_ -split '\s+')[0])" }
        exit 1
    }
}

Write-Host "Procurando o processo de $Package..." -ForegroundColor Cyan

$pidValue = $null
for ($i = 0; $i -lt $WaitSeconds; $i++) {
    $found = (& $adb @dev shell pidof $Package) -replace '\s', ''
    if ($found) { $pidValue = $found; break }
    if ($i -eq 0) { Write-Host "App fechado. Esperando abrir..." -ForegroundColor DarkGray }
    Start-Sleep -Seconds 1
}

if (-not $pidValue) {
    Write-Host "O app nao abriu em $WaitSeconds segundos. Rode .\scripts\run.ps1" -ForegroundColor Red
    exit 1
}

# Limpa o buffer para nao despejar o historico inteiro na tela.
& $adb @dev logcat -c

Write-Host "Seguindo o PID $pidValue. Ctrl+C encerra." -ForegroundColor Green
Write-Host ""

& $adb @dev logcat --pid=$pidValue -v brief
