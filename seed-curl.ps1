$base = "http://localhost:8080"
$dir = Join-Path $PSScriptRoot "seed-payloads"

function Post-File([string]$file, [string]$threadId, [string]$runId) {
    $path = Join-Path $dir $file
    $args = @(
        "-s", "-X", "POST", "$base/sendMessage",
        "-F", "text=<$path",
        "-F", "parse_mode=Markdown",
        "-F", "message_thread_id=$threadId"
    )
    if ($runId) { $args += "-F"; $args += "run_id=$runId" }
    $json = curl.exe @args | ConvertFrom-Json
    return $json.result.message_id
}

function Post-Text([string]$text, [string]$threadId, [string]$runId) {
    $tmp = [System.IO.Path]::GetTempFileName()
    [System.IO.File]::WriteAllText($tmp, $text, [System.Text.UTF8Encoding]::new($false))
    try {
        $args = @("-s", "-X", "POST", "$base/sendMessage", "-F", "text=<$tmp", "-F", "parse_mode=Markdown", "-F", "message_thread_id=$threadId")
        if ($runId) { $args += "-F"; $args += "run_id=$runId" }
        $json = curl.exe @args | ConvertFrom-Json
        return $json.result.message_id
    } finally { Remove-Item $tmp -Force -ErrorAction SilentlyContinue }
}

Write-Host "Seeding via curl (UTF-8)..."

$api = Post-File "api-summary.txt" "2" $null
Post-Text "========================`n*Упавшие тесты с ошибкой: \n*`n========================`n✅ [GET] Get projects`n" "2" $api | Out-Null
Post-Text "========================`n*Успешно пройденные тесты: \n*`n========================`n✅ [GET] Get projects`n✅ [POST] Create scan`n✅ [DELETE] Delete project`n" "2" $api | Out-Null
Write-Host "OK API $api"

$ui = Post-File "ui-summary.txt" "4" $null
Post-Text "========================`n*Упавшие тесты с ошибкой: \n*`n========================`n❌ [Login] Wrong password`n❌ [Projects] Filter failed`n❌ [Projects] Download timeout`n❌ [Admin] Duplicate user`n❌ [Scan] Button N/A`n" "4" $ui | Out-Null
Post-Text "========================`n*Успешно пройденные тесты: \n*`n========================`n✅ [Home] Dashboard`n✅ [Projects] Watch page`n" "4" $ui | Out-Null
Post-Text "========================`n*Тесты, которые не были запущены из-за системной ошибки: \n*`n========================`n➡️ [DAST] Stand unavailable`n➡️ [OSA] License expired`n" "4" $ui | Out-Null
Write-Host "OK UI $ui"

$ex = Post-File "express-summary.txt" "2154" $null
Post-Text "========================`n*Упавшие тесты с ошибкой: \n*`n========================`n❌ [DelphiFull] CustomMenu`n❌ [Python] sample`n❌ [Go] vulnerable`n❌ [Ruby] rails`n" "2154" $ex | Out-Null
Post-Text "========================`n*Успешно пройденные тесты: \n*`n========================`n✅ [C++] cmake`n✅ [C#] dotnet8`n✅ [Erlang] otp`n" "2154" $ex | Out-Null
Write-Host "OK Express $ex"

Post-File "healthy-summary.txt" "2152" $null | Out-Null
Post-File "release-summary.txt" "2913" $null | Out-Null
Post-File "updates-summary.txt" "2148" $null | Out-Null
Post-File "ai-summary.txt" "2158" $null | Out-Null
Post-File "general-summary.txt" "1" $null | Out-Null
Post-File "summary-daily.txt" "2156" $null | Out-Null

$all = Invoke-RestMethod "$base/api/runs"
Write-Host "Total runs:" $all.Count
$d = Invoke-RestMethod "$base/api/runs/$ex"
Write-Host "Express: total=$($d.totalTests) fail=$($d.failedTests) failedList=$($d.failedTestsList.Count)"
