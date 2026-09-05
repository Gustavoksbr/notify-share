<#
.SYNOPSIS
    Compila e instala o app em um ou varios aparelhos, apontado para dev ou producao.

.DESCRIPTION
    -Target dev  (padrao): o app fala com http://localhost:8080 (isso e fixo no
        build de dev). O `adb reverse` liga esse 8080 do aparelho a porta do
        backend no PC — 8085 por padrao. O passo que quase sempre esquecemos e o
        `adb reverse`; por isso ele esta aqui dentro, feito para CADA aparelho.
        -BackendPort troca so a ponta no PC (8081 = backend de E2E). Se voce
        mudar a porta do BACKEND DEV, mude aqui tambem (ou no terminals.json).

    -Target prod: app fala com https://notify-share.onrender.com. Nao precisa de
        ponte USB — vai direto pela internet. E o build "release" (pacote
        com.notifyshare, "Notify Share"), que CONVIVE com o dev
        (com.notifyshare.debug, "Notify Share DEV") no mesmo aparelho.

    Varios aparelhos:
      - sem -Serial e sem -AllDevices, com 1 so conectado: usa ele.
      - sem -Serial e sem -AllDevices, com 2+ conectados: lista e para (escolha).
      - -Serial <id>: so nesse aparelho.
      - -AllDevices: em todos os conectados (compila 1x, instala em cada um).

.EXAMPLE
    .\scripts\run.ps1                          # dev, 1 aparelho
    .\scripts\run.ps1 -AllDevices              # dev, todos os aparelhos
    .\scripts\run.ps1 -Serial 6baca47f        # dev, so esse
    .\scripts\run.ps1 -Target prod -AllDevices # producao, todos
    .\scripts\run.ps1 -SkipBuild -AllDevices   # so reinstala o APK que ja existe
#>
param(
    [ValidateSet("dev", "prod")]
    [string]$Target = "dev",
    # Porta do backend de dev NO PC (o aparelho sempre fala 8080; o adb reverse
    # redireciona). Precisa bater com o SERVER_PORT do BACKEND DEV. 8081 = E2E.
    [int]   $BackendPort = 8085,
    [string]$Serial,
    [switch]$AllDevices,
    [switch]$SkipBuild,
    # So compila o APK e copia para a raiz do repo. Nao precisa de aparelho.
    # Use com -Target prod para gerar um APK para instalar na mao noutro celular.
    [switch]$BuildOnly
)

$ErrorActionPreference = "Stop"
$mobileRoot = Split-Path -Parent $PSScriptRoot

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    throw "adb nao encontrado em $adb. Ajuste o caminho ou instale o platform-tools."
}

$prodUrl = "https://notify-share.onrender.com/"
$isProd = $Target -eq "prod"

if ($isProd) {
    $package = "com.notifyshare"
    $gradleTask = ":app:assembleRelease"
    $apk = Join-Path $mobileRoot "app\build\outputs\apk\release\app-release.apk"
} else {
    $package = "com.notifyshare.debug"
    $gradleTask = ":app:assembleDebug"
    $apk = Join-Path $mobileRoot "app\build\outputs\apk\debug\app-debug.apk"
}
$activity = "$package/com.notifyshare.MainActivity"

# --- so compilar (para instalar na mao noutro aparelho) -------------------

if ($BuildOnly) {
    Write-Host "Compilando o APK ($Target)..." -ForegroundColor Cyan
    Push-Location $mobileRoot
    try {
        & "$mobileRoot\gradlew.bat" $gradleTask
        if ($LASTEXITCODE -ne 0) { throw "Compilacao falhou." }
    } finally {
        Pop-Location
    }
    $repoRoot = Split-Path -Parent (Split-Path -Parent $mobileRoot)
    $stamp = Get-Date -Format "yyyyMMdd-HHmm"
    $dest = Join-Path $repoRoot "notify-share-$Target-$stamp.apk"
    Copy-Item $apk $dest -Force
    Write-Host ""
    Write-Host "APK pronto:" -ForegroundColor Green
    Write-Host "  $dest" -ForegroundColor Green
    Write-Host ""
    Write-Host "Pacote: $package  (aponta para $(if ($isProd) { $prodUrl } else { "localhost:$BackendPort" }))" -ForegroundColor DarkGray
    Write-Host "Para instalar noutro celular: copie o .apk para o aparelho (cabo/Drive/WhatsApp)" -ForegroundColor DarkGray
    Write-Host "e abra pelo gerenciador de arquivos (permita 'instalar apps desconhecidos')." -ForegroundColor DarkGray
    exit 0
}

# --- quais aparelhos --------------------------------------------------------

# "<serial>`tdevice" -> pega so os prontos (ignora 'unauthorized' / 'offline').
$connected = @(
    & $adb devices | Select-Object -Skip 1 |
        Where-Object { $_ -match "^(\S+)\s+device$" } |
        ForEach-Object { ($_ -split "\s+")[0] }
)

if ($connected.Count -eq 0) {
    Write-Host ""
    Write-Host "Nenhum aparelho pronto para depuracao." -ForegroundColor Red
    & $adb devices -l
    Write-Host "  - autorize a depuracao USB no aparelho (o pop-up RSA)" -ForegroundColor DarkGray
    Write-Host "  - em Xiaomi/MIUI, ligue tambem 'Instalar via USB' e 'Depuracao USB (config. de seguranca)'" -ForegroundColor DarkGray
    Write-Host "  - troque o modo USB de 'MTP/arquivos' para um que permita depuracao" -ForegroundColor DarkGray
    exit 1
}

if ($Serial) {
    if ($connected -notcontains $Serial) {
        Write-Host "Aparelho '$Serial' nao esta na lista:" -ForegroundColor Red
        $connected | ForEach-Object { Write-Host "  $_" }
        exit 1
    }
    $targets = @($Serial)
} elseif ($AllDevices) {
    $targets = $connected
} elseif ($connected.Count -eq 1) {
    $targets = $connected
} else {
    Write-Host ""
    Write-Host "$($connected.Count) aparelhos conectados. Escolha:" -ForegroundColor Yellow
    & $adb devices -l | Select-Object -Skip 1 | Where-Object { $_ -match "\sdevice " } | ForEach-Object { Write-Host "  $_" }
    Write-Host ""
    Write-Host "  .\scripts\run.ps1 -Serial <id>     # um aparelho" -ForegroundColor DarkGray
    Write-Host "  .\scripts\run.ps1 -AllDevices      # todos" -ForegroundColor DarkGray
    exit 1
}

Write-Host "Alvo: $Target $(if ($isProd) { "($prodUrl)" } else { "(localhost:$BackendPort pelo cabo)" })" -ForegroundColor Cyan
Write-Host "Aparelhos: $($targets -join ', ')" -ForegroundColor Cyan

# --- build (uma vez so) ----------------------------------------------------

if (-not $SkipBuild) {
    Write-Host "Compilando o APK ($gradleTask)..." -ForegroundColor Cyan
    Push-Location $mobileRoot
    try {
        & "$mobileRoot\gradlew.bat" $gradleTask
        if ($LASTEXITCODE -ne 0) { throw "Compilacao falhou." }
    } finally {
        Pop-Location
    }
}
if (-not (Test-Path $apk)) { throw "APK nao existe em $apk. Rode sem -SkipBuild." }

# --- instala e abre em cada aparelho -------------------------------------

foreach ($t in $targets) {
    Write-Host ""
    Write-Host "== $t ==" -ForegroundColor Green
    & $adb -s $t install -r $apk
    if ($LASTEXITCODE -ne 0) { Write-Host "  instalacao falhou em $t" -ForegroundColor Red; continue }

    if ($isProd) {
        if ((& $adb -s $t reverse --list 2>$null) -match "tcp:8080") {
            & $adb -s $t reverse --remove tcp:8080 2>$null | Out-Null
        }
        Write-Host "  sem ponte USB: vai direto para $prodUrl" -ForegroundColor DarkGray
    } else {
        & $adb -s $t reverse tcp:8080 "tcp:$BackendPort" | Out-Null
        Write-Host "  ponte: localhost:8080 (aparelho) -> localhost:$BackendPort (PC)" -ForegroundColor DarkGray
    }

    & $adb -s $t shell am start -n $activity | Out-Null
    Write-Host "  app aberto." -ForegroundColor Green
}

Write-Host ""
if ($isProd) {
    Write-Host "Producao (Render). Nao precisa do backend local." -ForegroundColor Yellow
} else {
    Write-Host "Deixe o backend rodando em localhost:$BackendPort, senao o login falha por rede." -ForegroundColor DarkGray
}
