$base = "http://localhost:8080"

function Send-Report {
    param(
        [string]$ThreadId,
        [string]$Summary,
        [string[]]$Failed = @(),
        [string[]]$Passed = @(),
        [string[]]$Skipped = @()
    )
    $enc = [uri]::EscapeDataString($Summary)
    $r = Invoke-RestMethod "$base/sendMessage?text=$enc&parse_mode=Markdown&message_thread_id=$ThreadId"
    $runId = $r.result.message_id

    if ($Failed.Count -gt 0) {
        $body = "========================`n*Упавшие тесты с ошибкой: \n*`n========================`n"
        $body += ($Failed | ForEach-Object { [char]0x274C + " $_`n" }) -join ""
        $encF = [uri]::EscapeDataString($body)
        Invoke-RestMethod "$base/sendMessage?text=$encF&parse_mode=Markdown&message_thread_id=$ThreadId&run_id=$runId" | Out-Null
    }
    if ($Passed.Count -gt 0) {
        $body = "========================`n*Успешно пройденные тесты: \n*`n========================`n"
        $body += ($Passed | ForEach-Object { [char]0x2705 + " $_`n" }) -join ""
        $encP = [uri]::EscapeDataString($body)
        Invoke-RestMethod "$base/sendMessage?text=$encP&parse_mode=Markdown&message_thread_id=$ThreadId&run_id=$runId" | Out-Null
    }
    if ($Skipped.Count -gt 0) {
        $body = "========================`n*Тесты, которые не были запущены из-за системной ошибки: \n*`n========================`n"
        $body += ($Skipped | ForEach-Object { [char]0x27A1 + [char]0xFE0F + " $_`n" }) -join ""
        $encS = [uri]::EscapeDataString($body)
        Invoke-RestMethod "$base/sendMessage?text=$encS&parse_mode=Markdown&message_thread_id=$ThreadId&run_id=$runId" | Out-Null
    }
    return $runId
}

Write-Host "Seeding demo reports..."

# API - all passed
$s1 = @'
☑️ Результаты тестирования приложения appScreener.
========================
*Стенд, где проходил автотест: \n*
`https://api-stand.dev.example.com`
*Название набора: \n*
`ApiSuiteStart`
*Значение переменной CI_SUITES для запуска Pipeline:\n*
`Patch/ApiSuiteStart.xml`
========================
*Статистика:\n*
Всего тестов: 24
Успешных: 24
Проваленных (ошибка): 0
Не запущенных (системная ошибка): 0
Время прохождения всех тестов: 00:45:12

[🔗 Ссылка на Pipeline](https://gitlab.example.com/jobs/1001)
[📊 Ссылка на Allure TestOps](https://dersecur.testops.cloud/launch/2001)
'@
Send-Report -ThreadId "2" -Summary $s1 -Passed @('[GET] Get projects','[POST] Create scan','[DELETE] Delete project') | Out-Null
Write-Host "OK API"

# UI - mixed
$s2 = @'
☑️ Результаты тестирования приложения appScreener.
========================
*Стенд, где проходил автотест: \n*
`https://ui-stand.test.example.com`
*Название набора: \n*
`ProjectsPageSmokeTestSuite`
*Значение переменной CI_SUITES для запуска Pipeline:\n*
`Release/ProjectsPageSmokeTestSuite.xml`
========================
*Статистика:\n*
Всего тестов: 18
Успешных: 11
Проваленных (ошибка): 5
Не запущенных (системная ошибка): 2
Время прохождения всех тестов: 01:12:33

[🔗 Ссылка на Pipeline](https://gitlab.example.com/jobs/1002)
[📊 Ссылка на Allure TestOps](https://dersecur.testops.cloud/launch/2002)
'@
Send-Report -ThreadId "4" -Summary $s2 -Failed @('Login wrong password','Filter by rating','Download report timeout','Create user duplicate','Scan button N/A') -Passed @('Open dashboard','Watch project page') -Skipped @('DAST stand unavailable','OSA license expired') | Out-Null
Write-Host "OK UI"

# Express
$s3 = @'
☑️ Результаты тестирования приложения appScreener.
========================
*Стенд, где проходил автотест: \n*
`https://appscreener-ui01d.ast.rt-solar.ru`
*Название набора: \n*
`Набор для проверки анализа OSA в экспресс формате`
*Значение переменной CI_SUITES для запуска Pipeline:\n*
`Express/ExpressOsa.xml`
========================
*Статистика:\n*
Всего тестов: 17
Успешных: 13
Проваленных (ошибка): 4
Не запущенных (системная ошибка): 0
Время прохождения всех тестов: 00:23:03

[🔗 Ссылка на Pipeline](https://gitlab.example.com/jobs/1003)
[📊 Ссылка на Allure TestOps](https://dersecur.testops.cloud/launch/2003)
'@
Send-Report -ThreadId "2154" -Summary $s3 -Failed @('DelphiFull CustomMenu','Python sample','Go vulnerable','Ruby rails') -Passed @('C++ cmake','C# dotnet8','Erlang otp') | Out-Null
Write-Host "OK Express"

# Healthy
$s4 = @'
☑️ Результаты тестирования приложения appScreener.
========================
*Стенд, где проходил автотест: \n*
`https://health-prod.example.com`
*Название набора: \n*
`HealthCheckSuite`
*Значение переменной CI_SUITES для запуска Pipeline:\n*
`WeekTests/HealthMonitor.xml`
========================
*Статистика:\n*
Всего тестов: 6
Успешных: 5
Проваленных (ошибка): 1
Не запущенных (системная ошибка): 0
Время прохождения всех тестов: 00:05:41

[🔗 Ссылка на Pipeline](https://gitlab.example.com/jobs/1004)
[📊 Ссылка на Allure TestOps](https://dersecur.testops.cloud/launch/2004)
'@
Send-Report -ThreadId "2152" -Summary $s4 -Failed @('DB connection pool exhausted') -Passed @('API ping','Disk space check') | Out-Null
Write-Host "OK Healthy"

# Release - many failures
$s5 = @'
☑️ Результаты тестирования приложения appScreener.
========================
*Стенд, где проходил автотест: \n*
`https://release-candidate.example.com`
*Название набора: \n*
`ReleaseSet`
*Значение переменной CI_SUITES для запуска Pipeline:\n*
`Release/ReleaseSet.xml`
========================
*Статистика:\n*
Всего тестов: 42
Успешных: 8
Проваленных (ошибка): 28
Не запущенных (системная ошибка): 6
Время прохождения всех тестов: 02:58:17

[🔗 Ссылка на Pipeline](https://gitlab.example.com/jobs/1005)
[📊 Ссылка на Allure TestOps](https://dersecur.testops.cloud/launch/2005)
'@
Send-Report -ThreadId "2913" -Summary $s5 -Failed @('SAST regression','OSA full scan failed','Rights in projects') -Skipped @('DAST no token','IC check skipped') | Out-Null
Write-Host "OK Release"

# Updates - all passed
$s6 = @'
☑️ Результаты тестирования приложения appScreener.
========================
*Стенд, где проходил автотест: \n*
`https://updates-stand.example.com`
*Название набора: \n*
`ScaDatabaseUpdateSuite`
*Значение переменной CI_SUITES для запуска Pipeline:\n*
`Patch/ScaUpdateCheck.xml`
========================
*Статистика:\n*
Всего тестов: 12
Успешных: 12
Проваленных (ошибка): 0
Не запущенных (системная ошибка): 0
Время прохождения всех тестов: 00:18:55

[🔗 Ссылка на Pipeline](https://gitlab.example.com/jobs/1006)
[📊 Ссылка на Allure TestOps](https://dersecur.testops.cloud/launch/2006)
'@
Send-Report -ThreadId "2148" -Summary $s6 -Passed @('CVE database sync','Ruleset import v2.4') | Out-Null
Write-Host "OK Updates"

# AI
$s7 = @'
☑️ Результаты тестирования приложения appScreener.
========================
*Стенд, где проходил автотест: \n*
`https://ai-lab.example.com`
*Название набора: \n*
`AiAnalyseExperimental`
*Значение переменной CI_SUITES для запуска Pipeline:\n*
`Experimental/AiSuite.xml`
========================
*Статистика:\n*
Всего тестов: 9
Успешных: 6
Проваленных (ошибка): 3
Не запущенных (системная ошибка): 0
Время прохождения всех тестов: 00:31:08

[🔗 Ссылка на Pipeline](https://gitlab.example.com/jobs/1007)
[📊 Ссылка на Allure TestOps](https://dersecur.testops.cloud/launch/2007)
'@
Send-Report -ThreadId "2158" -Summary $s7 -Failed @('Model inference timeout','False positive threshold','Prompt injection case 7') -Passed @('Baseline scan completed') | Out-Null
Write-Host "OK AI"

# General - minimal
$s8 = @'
☑️ Результаты тестирования приложения appScreener.
========================
*Название набора: \n*
`LocalSmoke`
========================
*Статистика:\n*
Всего тестов: 4
Успешных: 2
Проваленных (ошибка): 2
Не запущенных (системная ошибка): 0
Время прохождения всех тестов: 00:02:15
'@
Send-Report -ThreadId "1" -Summary $s8 -Failed @('Config load error','Driver mismatch') -Passed @('Open app','Logout') | Out-Null
Write-Host "OK General"

# Daily Summary
$sd = @'
📝 *SUMMARY* (19.05.2026)

Всего пройденных тестов: 156
Всего проваленных тестов: 23
Всего пропущенных тестов: 8
Время прохождения всех наборов: 08:42:19
'@
$encD = [uri]::EscapeDataString($sd)
Invoke-RestMethod "$base/sendMessage?text=$encD&parse_mode=Markdown&message_thread_id=2156" | Out-Null
Write-Host "OK Summary daily"

$all = Invoke-RestMethod "$base/api/runs"
Write-Host ""
Write-Host "Total runs:" $all.Count
$all | Group-Object categoryCode | ForEach-Object { Write-Host "  $($_.Name): $($_.Count)" }
Write-Host "Open: $base/"
