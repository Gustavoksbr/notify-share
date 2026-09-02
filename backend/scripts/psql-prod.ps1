<#
.SYNOPSIS
    Abre um psql no banco de PRODUCAO (Supabase), lendo a string do backend/.env.

.DESCRIPTION
    Le a variavel DATABASE_URL do .env, extrai host/porta/usuario/senha da URL
    JDBC e conecta com sslmode=require. Nao guarda senha em lugar nenhum.

    CUIDADO: e o banco de verdade. \dt lista tabelas, \q sai.
#>
$ErrorActionPreference = "Stop"
$backendRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $backendRoot ".env"

if (-not (Test-Path $envFile)) { throw "backend/.env nao encontrado." }

$line = Select-String -Path $envFile -Pattern '^\s*DATABASE_URL\s*=\s*(\S+)' |
    Select-Object -First 1
if (-not $line) { throw "DATABASE_URL nao esta definido no .env." }
$url = $line.Matches.Groups[1].Value

# jdbc:postgresql://HOST:PORT/DB?user=USER&password=PASS&sslmode=require
if ($url -notmatch 'postgresql://([^:/]+):(\d+)/([^?]+)\?(.+)$') {
    throw "DATABASE_URL nao esta no formato esperado: $url"
}
$pgHost = $Matches[1]; $pgPort = $Matches[2]; $pgDb = $Matches[3]
$params = @{}
foreach ($kv in $Matches[4].Split('&')) {
    $k, $v = $kv.Split('=', 2)
    $params[$k] = $v
}

$env:PGPASSWORD = $params['password']
$ssl = if ($params.ContainsKey('sslmode')) { $params['sslmode'] } else { 'require' }

Write-Host "PRODUCAO -> $pgHost`:$pgPort/$pgDb  (usuario $($params['user']))" -ForegroundColor Yellow
& psql "host=$pgHost port=$pgPort dbname=$pgDb user=$($params['user']) sslmode=$ssl"
