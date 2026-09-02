<#
.SYNOPSIS
    Roda a suite de testes contra um Postgres local, sem Docker/Testcontainers.

.DESCRIPTION
    Zera um banco dedicado (notifyshare_test) e aponta os testes para ele via
    SPRING_DATASOURCE_URL. O build detecta essa variavel e desliga o container
    (notifyshare.test.use-container=false), entao nada de Docker.

    Use quando o Docker Desktop estiver fora do ar. Com Docker, o normal e so
    `.\gradlew.bat test`.

.EXAMPLE
    .\scripts\run-tests-no-docker.ps1
    .\scripts\run-tests-no-docker.ps1 -Database notifyshare_test
#>
param(
    [string]$Database   = "notifyshare_test",
    [string]$PgUser     = "postgres",
    [string]$PgPassword = "root",
    [string]$PgServer   = "localhost",
    [int]   $PgPort     = 5432
)

$ErrorActionPreference = "Stop"
$backendRoot = Split-Path -Parent $PSScriptRoot
$env:PGPASSWORD = $PgPassword

Write-Host "Zerando o banco '$Database'..." -ForegroundColor Cyan
psql -U $PgUser -h $PgServer -p $PgPort -tc `
    "SELECT 'CREATE DATABASE $Database' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname='$Database')" |
    psql -U $PgUser -h $PgServer -p $PgPort -q
psql -U $PgUser -h $PgServer -p $PgPort -d $Database -q -c `
    "DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;"

$env:SPRING_DATASOURCE_URL =
    "jdbc:postgresql://${PgServer}:${PgPort}/${Database}?user=${PgUser}&password=${PgPassword}"

Write-Host "Rodando os testes contra $Database (sem Docker)..." -ForegroundColor Cyan
Push-Location $backendRoot
try {
    & "$backendRoot\gradlew.bat" test --rerun-tasks
} finally {
    Pop-Location
    Remove-Item Env:\SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue
}
