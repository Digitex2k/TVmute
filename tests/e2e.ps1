param(
    [string]$AdbPath = 'C:\platform-tools\adb.exe',
    [string]$Serial = '192.168.10.180:5555'
)

$ErrorActionPreference = 'Stop'
$script:failed = 0
$script:node = $null

function A {
    param([string[]]$Cmd)
    & $AdbPath -s $Serial @Cmd
}

function Pass($name) {
    Write-Host "PASS: $name" -ForegroundColor Green
}

function Fail($name, $detail) {
    $script:failed++
    Write-Host "FAIL: $name -- $detail" -ForegroundColor Red
}

function Get-RemoteNode {
    $out = A @('shell', 'getevent -lp')
    $node = $null
    foreach ($line in $out) {
        $text = "$line"
        if ($text -match 'add device \d+:\s+(\S+)') {
            $node = $matches[1]
        } elseif ($text -match 'name:\s+"Xiaomi RC"') {
            if ($node) { return $node }
        }
    }
    return $null
}

function Send-Yellow {
    param([int]$HoldMs)
    $hold = ($HoldMs / 1000.0).ToString([System.Globalization.CultureInfo]::InvariantCulture)
    A @('shell', "sendevent $script:node 4 4 786540; sendevent $script:node 1 400 1; sendevent $script:node 0 0 0; sleep $hold; sendevent $script:node 4 4 786540; sendevent $script:node 1 400 0; sendevent $script:node 0 0 0") | Out-Null
}

function Send-Homepage {
    A @('shell', "sendevent $script:node 4 4 786979; sendevent $script:node 1 172 1; sendevent $script:node 0 0 0; sleep 0.1; sendevent $script:node 4 4 786979; sendevent $script:node 1 172 0; sendevent $script:node 0 0 0") | Out-Null
}

function Get-Muted {
    $out = @(A @('shell', 'dumpsys audio'))
    $idx = -1
    for ($i = 0; $i -lt $out.Count; $i++) {
        if ("$($out[$i])" -match '^- STREAM_MUSIC:') { $idx = $i; break }
    }
    if ($idx -ge 0) {
        for ($j = $idx; $j -lt [Math]::Min($idx + 4, $out.Count); $j++) {
            if ("$($out[$j])" -match 'Muted:\s*(true|false)') {
                return ($matches[1] -eq 'true')
            }
        }
    }
    return $false
}

function Get-Resumed {
    $out = A @('shell', 'dumpsys activity activities')
    $line = $out | Select-String -Pattern 'mResumedActivity' | Select-Object -First 1
    if ($line) { return $line.ToString() }
    return ''
}

function Get-TvMuteLog {
    $out = A @('logcat', '-d', '-s', 'TVMute')
    return @($out | Select-String -Pattern 'TVMute\s+:' | ForEach-Object { $_.ToString() })
}

function Ensure-Unmuted {
    if (Get-Muted) {
        A @('shell', 'input keyevent 164') | Out-Null
        Start-Sleep -Milliseconds 700
    }
}

Write-Host "E2E TV Mute: $Serial"

$script:node = Get-RemoteNode
if (-not $script:node) {
    Write-Host 'FAIL: пульт Xiaomi RC не найден. Разбудите пульт (нажмите любую кнопку) и повторите.' -ForegroundColor Red
    exit 1
}
Write-Host "Remote node: $script:node"

$acc = (A @('shell', 'dumpsys accessibility')) -join "`n"
if ($acc -notmatch 'label=TV Mute' -and $acc -notmatch 'ru\.digit\.tvmute') {
    Write-Host 'FAIL: служба TV Mute не включена.' -ForegroundColor Red
    exit 1
}
Pass 'служба TV Mute включена'

Ensure-Unmuted
A @('shell', 'input keyevent 3') | Out-Null
Start-Sleep -Seconds 2

$before = Get-Muted
A @('logcat', '-c') | Out-Null
Send-Yellow -HoldMs 150
Start-Sleep -Seconds 2
$after = Get-Muted
$resumed = Get-Resumed
if ($after -ne $before -and $resumed -notmatch 'BannerLogoB') {
    Pass 'короткое нажатие переключает mute'
} else {
    Fail 'короткое нажатие переключает mute' "before=$before after=$after resumed=$resumed"
}

Send-Yellow -HoldMs 150
Start-Sleep -Seconds 2
$back = Get-Muted
if ($back -eq $before) {
    Pass 'повторное нажатие возвращает звук'
} else {
    Fail 'повторное нажатие возвращает звук' "before=$before now=$back"
}

$muteBefore = Get-Muted
A @('logcat', '-c') | Out-Null
Send-Yellow -HoldMs 1200
Start-Sleep -Seconds 3
$resumed = Get-Resumed
$muteAfter = Get-Muted
$logs = @(Get-TvMuteLog)
if ($resumed -match 'BannerLogoB') { Pass 'удержание открывает PatchWall' }
else { Fail 'удержание открывает PatchWall' "resumed=$resumed" }
if ($muteAfter -eq $muteBefore) { Pass 'удержание не переключает звук' }
else { Fail 'удержание не переключает звук' "before=$muteBefore after=$muteAfter" }
if (($logs | Where-Object { $_ -match 'OPEN_PATCHWALL' }).Count -eq 1) { Pass 'удержание срабатывает один раз' }
else { Fail 'удержание срабатывает один раз' ($logs -join ' | ') }

A @('logcat', '-c') | Out-Null
Send-Homepage
Start-Sleep -Seconds 2
$resumed = Get-Resumed
$logs = @(Get-TvMuteLog)
if ($resumed -match 'tvlauncher') { Pass 'кнопка Домой работает' }
else { Fail 'кнопка Домой работает' "resumed=$resumed" }
if ($logs.Count -eq 0) { Pass 'чужие клавиши не логируются' }
else { Fail 'чужие клавиши не логируются' ($logs -join ' | ') }

# --- 5. native handler intact (framework injection bypasses accessibility) ---
A @('logcat', '-c') | Out-Null
A @('shell', 'input keyevent 191') | Out-Null
Start-Sleep -Seconds 3
$resumed = Get-Resumed
if ($resumed -match 'BannerLogoB') { Pass 'нативный обработчик кнопки жив (input)' }
else { Fail 'нативный обработчик кнопки жив (input)' "resumed=$resumed" }

if ($script:failed -eq 0) {
    Write-Host 'E2E: PASS' -ForegroundColor Green
    exit 0
} else {
    Write-Host "E2E: FAIL ($script:failed)" -ForegroundColor Red
    exit 1
}
