$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$sdkDir = Join-Path $root ".tools\android-sdk"
$javaHome = Join-Path $root ".tools\jdk21\jdk-21.0.11+10"

$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $sdkDir
$env:ANDROID_SDK_ROOT = $sdkDir
$env:Path = "$javaHome\bin;$sdkDir\platform-tools;$sdkDir\emulator;$env:Path"

$avdmanager = Join-Path $sdkDir "cmdline-tools\latest\bin\avdmanager.bat"
$emulator = Join-Path $sdkDir "emulator\emulator.exe"
$avdName = "AudioBooks_Test"
$systemImage = "system-images;android-35;google_apis;x86_64"

# Create AVD if it doesn't exist
$existing = & $avdmanager list avd -c 2>&1
if ($existing -notcontains $avdName) {
    Write-Host "Creating AVD: $avdName ..." -ForegroundColor Cyan
    echo "no" | & $avdmanager create avd `
        --name $avdName `
        --package $systemImage `
        --device "pixel_7" `
        --force
    Write-Host "AVD created." -ForegroundColor Green
} else {
    Write-Host "AVD '$avdName' already exists." -ForegroundColor Green
}

# Launch emulator
Write-Host "Starting emulator..." -ForegroundColor Cyan
Write-Host "(First boot takes 1-2 minutes, subsequent boots are faster)" -ForegroundColor Yellow
Start-Process -FilePath $emulator -ArgumentList "-avd", $avdName, "-gpu", "auto"
Write-Host ""
Write-Host "Emulator starting in background. Once booted, install APK with:" -ForegroundColor Green
Write-Host "  adb install app\build\outputs\apk\debug\*.apk" -ForegroundColor White
