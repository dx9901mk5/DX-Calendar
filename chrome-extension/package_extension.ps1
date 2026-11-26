<#
.SYNOPSIS
  打包 DX日曆 Chrome 擴充功能為可直接上架 Chrome Web Store 的乾淨 .zip 套件。
.DESCRIPTION
  自動讀取 chrome-extension/manifest.json 版本號，排除開發測試與說明檔案，
  產出 chrome-extension/dist/DX-Calendar-Chrome-v<version>.zip（dist 已列入 .gitignore）。
#>
param(
  [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"
$ExtDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Manifest = Get-Content (Join-Path $ExtDir "manifest.json") -Raw -Encoding UTF8 | ConvertFrom-Json
$Version = $Manifest.version

if ([string]::IsNullOrWhiteSpace($OutDir)) {
  $OutDir = Join-Path $ExtDir "dist"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$ZipPath = Join-Path $OutDir "DX-Calendar-Chrome-v$Version.zip"
if (Test-Path $ZipPath) { Remove-Item $ZipPath -Force }

$StageDir = Join-Path ([System.IO.Path]::GetTempPath()) ("dx_chrome_stage_" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $StageDir | Out-Null

try {
  $IncludeFiles = @(
    "manifest.json",
    "popup.html",
    "styles.css",
    "lunar.js",
    "core.js",
    "nlp.js",
    "weather.js",
    "app.js",
    "background.js"
  )
  foreach ($f in $IncludeFiles) {
    Copy-Item (Join-Path $ExtDir $f) (Join-Path $StageDir $f) -Force
  }
  Copy-Item (Join-Path $ExtDir "icons") (Join-Path $StageDir "icons") -Recurse -Force

  Compress-Archive -Path (Join-Path $StageDir "*") -DestinationPath $ZipPath -CompressionLevel Optimal
  $SizeKb = [math]::Round((Get-Item $ZipPath).Length / 1KB, 1)
  Write-Host "Packaged Chrome Web Store ZIP: $ZipPath ($SizeKb KB)" -ForegroundColor Green
} finally {
  if (Test-Path $StageDir) { Remove-Item $StageDir -Recurse -Force }
}
