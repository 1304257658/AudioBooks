$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$libsDir = Join-Path $root "app\libs"
$toolsDir = Join-Path $root ".tools"

New-Item -ItemType Directory -Force -Path $libsDir, $toolsDir | Out-Null

$aar = Join-Path $libsDir "sherpa-onnx-1.13.2.aar"
if (-not (Test-Path $aar)) {
    gh release download v1.13.2 --repo k2-fsa/sherpa-onnx --pattern sherpa-onnx-1.13.2.aar --dir $libsDir --clobber
}
