<#
.SYNOPSIS
    Compila, instala, liga a ponte USB e abre o app no aparelho.

.DESCRIPTION
    O passo que quase sempre esquecemos e o adb reverse: sem ele o app sobe,
    mas nao acha o backend e todo login falha com erro de rede. Por isso ele
    esta aqui dentro, e nao como passo separado.

    A porta do PC e configuravel para apontar o mesmo app ora no backend de
    desenvolvimento (8080), ora no de E2E (8081), sem rebuildar nada.

.EXAMPLE
    .\scripts\run.ps1
    .\scripts\run.ps1 -BackendPort 8081     # aponta para o backend de E2E
    .\scripts\run.ps1 -SkipBuild            # so reinstala o APK que ja existe
#>
param(
    [int]   $BackendPort = 8080,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$mobileRoot = Split-Path -Parent $PSScriptRoot

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    throw "adb nao encontrado em $adb. Ajuste o caminho ou instale o platform-tools."
}

$package = "com.notifyshare.debug"
$activity = "$package/com.notifyshare.MainActivity"

# --- aparelho ---------------------------------------------------------------

# @() forca array: com um aparelho so, o pipeline devolveria uma string,
# e indexar uma string devolve um Char em vez da linha inteira.
$devices = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\sdevice$" })
if ($devices.Count -eq 0) {
    Write-Host ""
    Write-Host "Nenhum aparelho conectado." -ForegroundColor Red
    Write-Host "  - conecte o cabo e autorize a depuracao no aparelho" -ForegroundColor DarkGray
    Write-Host "  - em Xiaomi/HyperOS, ligue tambem 'Instalar via USB'" -ForegroundColor DarkGray
    Write-Host "    nas opcoes de desenvolvedor" -ForegroundColor DarkGray
    exit 1
}
Write-Host "Aparelho: $($devices[0].Split()[0])" -ForegroundColor Cyan

# --- build e instalacao -----------------------------------------------------

Push-Location $mobileRoot
try {
    if ($SkipBuild) {
        $apk = Join-Path $mobileRoot "app\build\outputs\apk\debug\app-debug.apk"
        if (-not (Test-Path $apk)) { throw "APK nao existe ainda. Rode sem -SkipBuild." }
        Write-Host "Instalando o APK existente..." -ForegroundColor Cyan
        & $adb install -r $apk
    } else {
        Write-Host "Compilando e instalando..." -ForegroundColor Cyan
        & "$mobileRoot\gradlew.bat" :app:installDebug
    }
    if ($LASTEXITCODE -ne 0) { throw "Instalacao falhou." }
} finally {
    Pop-Location
}

# --- ponte e abertura -------------------------------------------------------

& $adb reverse tcp:8080 "tcp:$BackendPort" | Out-Null
Write-Host "Ponte USB: localhost:8080 do aparelho -> localhost:$BackendPort do PC" -ForegroundColor Green

& $adb shell am start -n $activity | Out-Null

Write-Host ""
Write-Host "App aberto no aparelho." -ForegroundColor Green
if ($BackendPort -ne 8080) {
    Write-Host "Atencao: apontando para a porta $BackendPort (backend de E2E)." -ForegroundColor Yellow
}
Write-Host "Lembre de deixar o backend rodando, senao o login falha por rede." -ForegroundColor DarkGray
