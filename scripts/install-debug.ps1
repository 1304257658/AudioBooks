$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = Join-Path $root ".tools\jdk21\jdk-21.0.11+10"
$env:ANDROID_HOME = Join-Path $root ".tools\android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"

& (Join-Path $root "scripts\setup-models.ps1")
& (Join-Path $root "gradlew.bat") ":app:assembleDebug"
& (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe") "install" "-r" (Join-Path $root "app\build\outputs\apk\debug\app-debug.apk")
