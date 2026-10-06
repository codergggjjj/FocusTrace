[CmdletBinding()]
param(
    [ValidateSet('Build', 'Device', 'Full')][string]$Scope = 'Build',
    [string]$Tests,
    [string]$Serial,
    [ValidateRange(30, 1800)][int]$TimeoutSeconds = 180,
    [switch]$Plan
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $root
if ($Scope -eq 'Device' -and [string]::IsNullOrWhiteSpace($Tests)) {
    throw 'Device 必须指定 -Tests 类名或 类名#方法名；全量测试请显式选择 -Scope Full。'
}
if ($Tests -and $Scope -ne 'Device') { throw '-Tests 仅用于 Device。' }
if ($Tests -and $Tests -notmatch '^com\.focustrace\.\w+(#\w+)?(,com\.focustrace\.\w+(#\w+)?)*$') {
    throw '测试名格式不正确；多个测试用英文逗号分隔。'
}
if ($Serial -and $Serial -notmatch '^[\w.:-]+$') { throw '设备序列号格式不正确。' }
$tasks = @(':app:assembleDebug', ':app:lintDebug')
if ($Scope -ne 'Build') { $tasks += ':app:assembleDebugAndroidTest' }
Write-Host "范围：$Scope；构建：$($tasks -join ' ')"
if ($Scope -ne 'Build') { Write-Host "设备测试：$(if ($Tests) { $Tests } else { '全部（显式请求）' })；超时：$TimeoutSeconds 秒；不自动重试" }
if ($Plan) { return }

# Preflight before spending time building. Never launch or close an emulator.
if ($Scope -ne 'Build') {
    $adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
    if (-not $adb) {
        $sdk = $env:ANDROID_HOME
        if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
        if (-not $sdk -and (Test-Path -LiteralPath 'local.properties')) {
            $line = Get-Content -LiteralPath 'local.properties' | Where-Object { $_ -match '^sdk.dir=' } | Select-Object -First 1
            if ($line) { $sdk = $line.Substring(8).Replace('\:', ':').Replace('\\', '\') }
        }
        if ($sdk) { $adb = Join-Path $sdk 'platform-tools/adb.exe' }
    }
    if (-not $adb -or -not (Test-Path -LiteralPath $adb)) { throw '找不到 adb，请配置 Android SDK。' }
    $devices = @(& $adb devices)
    if ($LASTEXITCODE -ne 0) { throw '无法读取设备列表。' }
    $ready = @($devices | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
    if (-not $Serial) {
        if ($ready.Count -ne 1) { throw '需要一个已连接设备；多个设备时请指定 -Serial。不会自动启动模拟器。' }
        $Serial = $ready[0]
    }
    if ($Serial -notin $ready) { throw '指定设备未连接或未就绪。' }
    Write-Host "目标设备：$Serial；请使用专用测试设备，仪器测试可能修改应用数据。"
}
& (Join-Path $root 'gradlew.bat') @tasks --console=plain
if ($LASTEXITCODE -ne 0) { throw '构建失败，停止执行，不自动重试。' }
if ($Scope -eq 'Build') { return }

function Invoke-AdbChecked([string[]]$Arguments) {
    & $adb -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "设备操作失败：$($Arguments -join ' ')" }
}
$logDir = Join-Path $root ('app/build/test-runs/' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $logDir -Force | Out-Null
$installed = $false
try {
    Invoke-AdbChecked -Arguments @('install', '-r', 'app/build/outputs/apk/debug/app-debug.apk')
    $installed = $true
    Invoke-AdbChecked -Arguments @('install', '-r', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
    Invoke-AdbChecked -Arguments @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    Invoke-AdbChecked -Arguments @('shell', 'wm', 'dismiss-keyguard')
    $arguments = @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-r')
    if ($Tests) { $arguments += @('-e', 'class', $Tests) }
    $arguments += 'com.focustrace.test/androidx.test.runner.AndroidJUnitRunner'
    $stdout = Join-Path $logDir 'instrumentation.txt'
    $stderr = Join-Path $logDir 'instrumentation-error.txt'
    $process = Start-Process -FilePath $adb -ArgumentList $arguments -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    $elapsed = [Diagnostics.Stopwatch]::StartNew()
    while (-not $process.WaitForExit(10000)) {
        Write-Host ("测试运行中：{0:N0} 秒；日志：{1}" -f $elapsed.Elapsed.TotalSeconds, $stdout)
        if ($elapsed.Elapsed.TotalSeconds -ge $TimeoutSeconds) {
            $process.Kill()
            & $adb -s $Serial shell am force-stop com.focustrace
            throw "测试超时，已停止本次测试；保留日志，不自动重跑。$stdout"
        }
    }
    $process.WaitForExit()
    $result = Get-Content -LiteralPath $stdout -Raw
    Write-Host $result
    # adb may exit 0 even when JUnit fails. Require an actual, nonempty JUnit success result.
    if ($process.ExitCode -ne 0 -or $result -notmatch 'OK \([1-9][0-9]* tests?\)' -or
        $result -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=') {
        throw "测试未通过或没有执行测试；查看 $logDir"
    }
} finally {
    if ($installed) { & $adb -s $Serial shell am start -n com.focustrace/.MainActivity }
    Write-Host "保留模拟器与 App；日志目录：$logDir"
}
