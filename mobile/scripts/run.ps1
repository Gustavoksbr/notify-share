<#
.SYNOPSIS
    Compila, instala e abre o app no aparelho, apontado para dev ou producao.

.DESCRIPTION
    -Target dev  (padrao): app fala com http://localhost:8080 pelo cabo. O passo
        que quase sempre esquecemos e o `adb reverse` — sem ele o app sobe mas
        nao acha o backend e todo login falha por rede. Por isso ele esta aqui
        dentro. -BackendPort troca a ponta no PC (8081 = backend de E2E).

    -Target prod: app fala com https://notify-share.onrender.com. Nao precisa de
        ponte USB — vai direto pela internet.

.EXAMPLE
    .\scripts\run.ps1                    # dev, localhost:8080
    .\scripts\run.ps1 -Target prod       # producao (Render)
    .\scripts\run.ps1 -BackendPort 8081  # dev apontando no backend de E2E
    .\scripts\run.ps1 -SkipBuild         # so reinstala o APK que ja existe
#>
param(
    [ValidateSet("dev", "prod")]
    [string]$Target = "dev",
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

$prodUrl = "https://notify-share.onrender.com/"
$isProd = $Target -eq "prod"

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
Write-Host "Alvo: $Target $(if ($isProd) { "($prodUrl)" } else { "(localhost:$BackendPort pelo cabo)" })" -ForegroundColor Cyan

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
        $gradleArgs = @(":app:installDebug")
        if ($isProd) { $gradleArgs += "-PapiBaseUrl=$prodUrl" }
        & "$mobileRoot\gradlew.bat" @gradleArgs
    }
    if ($LASTEXITCODE -ne 0) { throw "Instalacao falhou." }
} finally {
    Pop-Location
}

# --- ponte e abertura -------------------------------------------------------

if ($isProd) {
    # so remove a ponte se ela existir — chamar --remove numa ponte inexistente
    # faz o adb escrever no stderr e, com ErrorActionPreference=Stop, o script morre.
    if ((& $adb reverse --list 2>$null) -match "tcp:8080") {
        & $adb reverse --remove tcp:8080 2>$null | Out-Null
        Write-Host "Ponte USB antiga removida (era de um run -Target dev)." -ForegroundColor DarkGray
    }
    Write-Host "Sem ponte USB: o app vai direto para $prodUrl" -ForegroundColor Green
} else {
    & $adb reverse tcp:8080 "tcp:$BackendPort" | Out-Null
    Write-Host "Ponte USB: localhost:8080 do aparelho -> localhost:$BackendPort do PC" -ForegroundColor Green
}

& $adb shell am start -n $activity | Out-Null

Write-Host ""
Write-Host "App aberto no aparelho." -ForegroundColor Green
if ($isProd) {
    Write-Host "Apontando para PRODUCAO (Render). Nao precisa do backend local." -ForegroundColor Yellow
} else {
    if ($BackendPort -ne 8080) {
        Write-Host "Atencao: apontando para a porta $BackendPort (backend de E2E)." -ForegroundColor Yellow
    }
    Write-Host "Lembre de deixar o backend rodando, senao o login falha por rede." -ForegroundColor DarkGray
}
