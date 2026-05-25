$base = "http://localhost:8080"
$dir = Join-Path $PSScriptRoot "seed-payloads"

function Read-Utf8([string]$name) {
    Get-Content (Join-Path $dir $name) -Raw -Encoding UTF8
}

function Post-Message([string]$text, [string]$threadId, [string]$runId) {
    $parts = [System.Collections.Generic.List[string]]::new()
    $parts.Add("text=" + [uri]::EscapeDataString($text))
    $parts.Add("parse_mode=Markdown")
    $parts.Add("message_thread_id=$threadId")
    if ($runId) { $parts.Add("run_id=$runId") }
    $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes(($parts -join "&"))
    $resp = Invoke-WebRequest -Uri "$base/sendMessage" -Method Post -Body $bodyBytes -ContentType "application/x-www-form-urlencoded; charset=UTF-8" -UseBasicParsing
    $resp.Content | ConvertFrom-Json
}

function Send-FullRun([string]$name, [string]$threadId, [string]$summaryFile, [string[]]$failed, [string[]]$passed, [string[]]$skipped) {
    $summary = Read-Utf8 $summaryFile
    $r = Post-Message $summary $threadId $null
    $runId = $r.result.message_id

    if ($failed.Count) {
        $t = "========================`n*Упавшие тесты с ошибкой: \n*`n========================`n"
        $t += ($failed | ForEach-Object { "❌ $_`n" }) -join ""
        Post-Message $t $threadId $runId | Out-Null
    }
    if ($passed.Count) {
        $t = "========================`n*Успешно пройденные тесты: \n*`n========================`n"
        $t += ($passed | ForEach-Object { "✅ $_`n" }) -join ""
        Post-Message $t $threadId $runId | Out-Null
    }
    if ($skipped.Count) {
        $t = "========================`n*Тесты, которые не были запущены из-за системной ошибки: \n*`n========================`n"
        $t += ($skipped | ForEach-Object { "➡️ $_`n" }) -join ""
        Post-Message $t $threadId $runId | Out-Null
    }
    Write-Host "OK $name -> $runId"
}

Write-Host "Seeding (UTF-8 POST)..."

Send-FullRun "API" "2" "api-summary.txt" @() @(
    "[GET] Get projects list", "[POST] Create scan", "[DELETE] Delete project"
) @()

Send-FullRun "UI" "4" "ui-summary.txt" @(
    "[Login] Wrong password", "[Projects] Filter by rating", "[Projects] Download report timeout",
    "[Admin] Create user duplicate", "[Scan] Start scan N/A"
) @("[Home] Open dashboard", "[Projects] Watch project page") @(
    "[DAST] Stand unavailable", "[OSA] License expired"
)

Send-FullRun "Express" "2154" "express-summary.txt" @(
    "[DelphiFull] CustomMenu_main_with_dep.zip", "[Python] sample_project.zip",
    "[Go] module_vulnerable.zip", "[Ruby] rails_legacy.zip"
) @("[C++] cmake_project.zip", "[C#] dotnet8_app.zip", "[Erlang] otp_release.zip") @()

Send-FullRun "Healthy" "2152" "healthy-summary.txt" @("[Health] DB pool exhausted") @(
    "[Health] API ping", "[Health] Disk space"
) @()

Send-FullRun "Release" "2913" "release-summary.txt" @(
    "[Release] SAST regression", "[Release] OSA full scan", "[Release] Rights in projects"
) @("[Release] Authorisation smoke") @("[Release] DAST no token", "[Release] IC check skipped")

Send-FullRun "Updates" "2148" "updates-summary.txt" @() @(
    "[SCA] CVE database sync", "[SCS] Ruleset import v2.4"
) @()

Send-FullRun "AI" "2158" "ai-summary.txt" @(
    "[AI] Inference timeout", "[AI] FP rate threshold", "[AI] Prompt injection #7"
) @("[AI] Baseline scan OK") @()

Send-FullRun "General" "1" "general-summary.txt" @(
    "[Local] Config load error", "[Local] Driver mismatch"
) @("[Local] Open app", "[Local] Logout") @()

Post-Message (Read-Utf8 "summary-daily.txt") "2156" $null | Out-Null
Write-Host "OK Summary daily"

$all = Invoke-RestMethod "$base/api/runs"
Write-Host "`nTotal runs:" $all.Count
$all | Group-Object categoryCode | Sort-Object Name | ForEach-Object { Write-Host "  $($_.Name): $($_.Count)" }
$sample = Invoke-RestMethod "$base/api/runs?category=express" | Select-Object -First 1
if ($sample) {
    Write-Host "`nExpress sample: total=$($sample.totalTests) failed=$($sample.failedTests) failedList=$($sample.failedTestsList.Count)"
}
Write-Host "Dashboard: $base/"
