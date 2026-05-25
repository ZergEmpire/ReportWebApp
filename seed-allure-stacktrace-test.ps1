# Тест stacktrace: launch 59211, testresult 117352 (id из /api/launch/59211/unresolved)
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

$summary = Read-Utf8 "allure-stacktrace-summary.txt"
$r = Post-Message $summary "2154" $null
$runId = $r.result.message_id

$failed = Read-Utf8 "allure-stacktrace-failed.txt"
Post-Message $failed "2154" $runId | Out-Null

Write-Host "OK runId=$runId"
Write-Host "UI: $base/report/$runId"
Write-Host "API: $base/api/allure/testresult/117352"
Write-Host "Allure launch: https://dersecur.testops.cloud/launch/59211"
