<#
.SYNOPSIS
    Sobe o backend contra um banco dedicado a testes de ponta a ponta.

.DESCRIPTION
    O banco e zerado a cada execucao, entao todo teste de e2e parte sempre do
    mesmo estado. O banco de desenvolvimento nao e tocado.

    Roda numa porta diferente da de desenvolvimento de proposito: assim os dois
    backends coexistem, e voce escolhe qual o aparelho enxerga apenas trocando
    o mapeamento do adb reverse, sem rebuildar o app.

    O processo fica em primeiro plano esperando o Android rodar os testes.
    Ctrl+C encerra.

.EXAMPLE
    .\scripts\run-e2e.ps1
    .\scripts\run-e2e.ps1 -Port 8082
#>
param(
    [int]   $Port     = 8081,
    [string]$Database = "notifyshare_e2e"
)

$ErrorActionPreference = "Stop"
$backendRoot = Split-Path -Parent $PSScriptRoot

& "$PSScriptRoot\reset-db.ps1" -Database $Database

$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/$Database" +
                             "?user=postgres&password=root"
$env:SERVER_PORT = "$Port"

Write-Host ""
Write-Host "==================================================" -ForegroundColor Green
Write-Host " Backend E2E  ->  http://localhost:$Port" -ForegroundColor Green
Write-Host " Banco        ->  $Database (zerado agora)" -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Green
Write-Host ""
Write-Host " Para o aparelho enxergar ESTE backend, aponte a ponte:" -ForegroundColor Yellow
Write-Host "   adb reverse tcp:8080 tcp:$Port" -ForegroundColor Yellow
Write-Host ""
Write-Host " O app continua chamando localhost:8080 no proprio aparelho." -ForegroundColor DarkGray
Write-Host " Quem decide se isso vai para dev ou e2e e o mapeamento acima." -ForegroundColor DarkGray
Write-Host ""

Push-Location $backendRoot
try {
    & "$backendRoot\gradlew.bat" bootRun
} finally {
    Pop-Location
}
