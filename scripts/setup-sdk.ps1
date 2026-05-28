$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$sdkDir = Join-Path $root ".tools\android-sdk"
$javaHome = Join-Path $root ".tools\jdk21\jdk-21.0.11+10"
$cmdlineToolsZip = Join-Path $root ".tools\commandlinetools-win.zip"
$cmdlineToolsDir = Join-Path $sdkDir "cmdline-tools"
$sdkmanager = Join-Path $cmdlineToolsDir "latest\bin\sdkmanager.bat"

$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $sdkDir
$env:ANDROID_SDK_ROOT = $sdkDir
$env:Path = "$javaHome\bin;$env:Path"

# ── 1. Download command-line tools ──────────────────────────────────
if (-not (Test-Path $sdkmanager)) {
    Write-Host "[1/4] Downloading Android command-line tools..." -ForegroundColor Cyan
    New-Item -ItemType Directory -Force -Path $sdkDir | Out-Null

    $url = "https://dl.google.com/android/repository/commandlinetools-win-14742923_latest.zip"
    if (-not (Test-Path $cmdlineToolsZip)) {
        Invoke-WebRequest -Uri $url -OutFile $cmdlineToolsZip -UseBasicParsing
    }

    # Extract to a temp location first, then move into the expected structure
    $tempExtract = Join-Path $root ".tools\cmdline-tools-temp"
    if (Test-Path $tempExtract) { Remove-Item -Recurse -Force $tempExtract }
    Expand-Archive -Path $cmdlineToolsZip -DestinationPath $tempExtract -Force

    # Google packages it as cmdline-tools/bin/... but sdkmanager expects cmdline-tools/latest/bin/...
    $extractedDir = Join-Path $tempExtract "cmdline-tools"
    if (-not (Test-Path $extractedDir)) {
        # Might be extracted directly without subdirectory
        $extractedDir = $tempExtract
    }
    $latestDir = Join-Path $cmdlineToolsDir "latest"
    New-Item -ItemType Directory -Force -Path $cmdlineToolsDir | Out-Null
    if (Test-Path $latestDir) { Remove-Item -Recurse -Force $latestDir }
    Move-Item -Path $extractedDir -Destination $latestDir -Force

    # Cleanup
    if (Test-Path $tempExtract) { Remove-Item -Recurse -Force $tempExtract }
    Remove-Item -Force $cmdlineToolsZip -ErrorAction SilentlyContinue

    Write-Host "    Command-line tools installed." -ForegroundColor Green
} else {
    Write-Host "[1/4] Command-line tools already present." -ForegroundColor Green
}

# ── 2. Accept all licenses ──────────────────────────────────────────
Write-Host "[2/4] Accepting SDK licenses..." -ForegroundColor Cyan
$licenseInput = "y`ny`ny`ny`ny`ny`ny`ny`ny`n"
$licenseInput | & $sdkmanager --licenses 2>&1 | Out-Null
Write-Host "    Licenses accepted." -ForegroundColor Green

# ── 3. Install required SDK packages ────────────────────────────────
Write-Host "[3/4] Installing SDK packages (this may take a few minutes)..." -ForegroundColor Cyan
$packages = @(
    "platform-tools",
    "build-tools;36.0.0",
    "platforms;android-36"
)
foreach ($pkg in $packages) {
    Write-Host "    Installing $pkg ..."
    & $sdkmanager --install $pkg 2>&1 | Select-String -Pattern "done|warning|error" -CaseSensitive:$false
}
Write-Host "    All packages installed." -ForegroundColor Green

# ── 4. (Optional) Install emulator + system image for local testing ─
Write-Host "[4/4] Installing emulator + x86_64 system image for local testing..." -ForegroundColor Cyan
$emulatorPackages = @(
    "emulator",
    "system-images;android-35;google_apis;x86_64"
)
foreach ($pkg in $emulatorPackages) {
    Write-Host "    Installing $pkg ..."
    & $sdkmanager --install $pkg 2>&1 | Select-String -Pattern "done|warning|error" -CaseSensitive:$false
}
Write-Host "    Emulator packages installed." -ForegroundColor Green

# ── Done ────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "===== Android SDK setup complete! =====" -ForegroundColor Green
Write-Host "SDK location: $sdkDir"
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "  1. Build:    .\scripts\build-debug.ps1"
Write-Host "  2. Emulator: Create a virtual device with:"
Write-Host "     & `"$sdkDir\emulator\emulator.exe`" -avd Pixel7_Test"
Write-Host "     (First create it via avdmanager or Android Studio)"
Write-Host ""
