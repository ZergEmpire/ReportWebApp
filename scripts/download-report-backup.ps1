# Скачивает history-*.json.gz с Report Web App (логин по ключу доступа).
# $env:REPORT_WEB_APP_URL, $env:REPORT_ACCESS_KEY; опционально $env:OUT_DIR (по умолчанию backups).
# Использует curl.exe (не следует редиректу после логина — важно для Timeweb/Caddy).

$ErrorActionPreference = "Stop"

$base = $env:REPORT_WEB_APP_URL
if (-not $base) { throw "Set REPORT_WEB_APP_URL (e.g. http://localhost:8080)" }
$base = $base.TrimEnd("/")

$key = $env:REPORT_ACCESS_KEY
if (-not $key) { throw "Set REPORT_ACCESS_KEY" }

$outDir = if ($env:OUT_DIR) { $env:OUT_DIR } else { "backups" }
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$curl = Get-Command curl.exe -ErrorAction SilentlyContinue
if (-not $curl) { throw "curl.exe not found (Windows 10+ or Git for Windows)" }

$jar = Join-Path $env:TEMP "report-web-app-cookies-$PID.txt"

try {
    Write-Host "Logging in to $base ..."
  # Без -L: после логина прокси может редиректить на http:// — cookie уже в jar.
    & curl.exe -fsS -c $jar -b $jar -X POST "$base/auth/access" -d "accessKey=$key" -o NUL

    $statusJson = & curl.exe -fsS -b $jar "$base/api/backup"
    $status = $statusJson | ConvertFrom-Json
    if (-not $status.enabled) {
        throw "Login failed or backup API is disabled. Check REPORT_ACCESS_KEY."
    }

    Write-Host "Creating backup on server ..."
    $createJson = & curl.exe -fsS -b $jar -X POST "$base/api/backup"
    $create = $createJson | ConvertFrom-Json

    $fileName = if ($create.skipped) {
        Write-Host "Server skipped new archive: $($create.message)"
        (& curl.exe -fsS -b $jar "$base/api/backup" | ConvertFrom-Json).backups[0].fileName
    } else {
        $create.backup.fileName
    }

    if (-not $fileName) { throw "No backup file name in API response." }

    $dest = Join-Path $outDir $fileName
    Write-Host "Downloading $fileName ..."
    & curl.exe -fsS -b $jar -o $dest "$base/api/backup/$fileName/download"

    $size = (Get-Item $dest).Length
    Write-Host "Saved $dest ($size bytes)"
}
finally {
    Remove-Item $jar -ErrorAction SilentlyContinue
}
