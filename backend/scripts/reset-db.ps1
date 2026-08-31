<#
.SYNOPSIS
    Garante que o banco existe e devolve o schema public vazio.

.DESCRIPTION
    Nao roda migracao: apenas limpa. O Flyway recria tudo do zero na proxima
    subida da aplicacao. Use quando uma migracao mudou e voce nao quer
    empilhar uma migracao de conserto so para desenvolver.

.EXAMPLE
    .\scripts\reset-db.ps1
    .\scripts\reset-db.ps1 -Database notifyshare_e2e
#>
param(
    [string]$Database   = "notifyshare",
    [string]$PgUser     = "postgres",
    [string]$PgPassword = "root",
    [string]$PgServer   = "localhost",
    [int]   $PgPort     = 5432
)

$ErrorActionPreference = "Stop"
$env:PGPASSWORD = $PgPassword

Write-Host "Garantindo que o banco '$Database' existe..." -ForegroundColor Cyan
# CREATE DATABASE nao aceita IF NOT EXISTS nem roda dentro de transacao,
# entao geramos o comando so quando ele for necessario e mandamos para o psql.
psql -U $PgUser -h $PgServer -p $PgPort -tc `
    "SELECT 'CREATE DATABASE $Database' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname='$Database')" |
    psql -U $PgUser -h $PgServer -p $PgPort -q

Write-Host "Limpando o schema public de '$Database'..." -ForegroundColor Cyan
psql -U $PgUser -h $PgServer -p $PgPort -d $Database -q -c `
    "DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;"

Write-Host "Pronto. O Flyway recria o schema na proxima subida." -ForegroundColor Green
