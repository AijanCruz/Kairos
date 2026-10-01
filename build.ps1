param([string[]]$Tasks = @('assembleDebug'))
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = Split-Path (Split-Path (Get-Command javac.exe).Source)
$env:ANDROID_HOME = Join-Path $PSScriptRoot '.toolchain\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.toolchain\gradle-home'
Push-Location $PSScriptRoot
try {
    & "$PSScriptRoot\gradlew.bat" @Tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle terminó con código $LASTEXITCODE" }
    if ($Tasks -contains 'assembleDebug') {
        $dist = Join-Path $PSScriptRoot 'dist'
        New-Item -ItemType Directory -Force $dist | Out-Null
        $apk = Join-Path $dist 'Kairos-1.0.0-debug.apk'
        Copy-Item "$PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk" $apk -Force
        (Get-FileHash $apk -Algorithm SHA256).Hash.ToLowerInvariant() | Set-Content "$apk.sha256" -Encoding ASCII
        Write-Output "APK: $apk"
    }
} finally { Pop-Location }
