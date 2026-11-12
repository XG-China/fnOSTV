# 打包源码压缩包（包含签名/密钥，便于自己继续开发）
#
# 用法： scripts\package-source.cmd
# 产物： docs\fnOSTV-source-<日期>.zip
#
# 只按白名单复制源码/配置/文档/签名，避免把 .tooling、build 等本机产物带进去。
# 注意：包内含 signing/ 与 keystore.properties，请勿公开分发。

$ErrorActionPreference = "Stop"

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$Stamp = Get-Date -Format "yyyyMMdd"
$OutDir = Join-Path $ProjectRoot "docs"
$StageDir = Join-Path $env:TEMP ("fnOSTV-source-" + $Stamp)
$ZipPath = Join-Path $OutDir "fnOSTV-source-$Stamp.zip"

if (Test-Path $StageDir) { Remove-Item $StageDir -Recurse -Force }
if (Test-Path $ZipPath) { Remove-Item $ZipPath -Force }
New-Item -ItemType Directory -Path $StageDir -Force | Out-Null

function Copy-Tree($relative) {
    $source = Join-Path $ProjectRoot $relative
    if (-not (Test-Path $source)) { return }
    $target = Join-Path $StageDir $relative
    New-Item -ItemType Directory -Path $target -Force | Out-Null
    robocopy $source $target /E /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "robocopy failed for $relative (code $LASTEXITCODE)" }
}

function Copy-FileSafe($relative) {
    $source = Join-Path $ProjectRoot $relative
    if (-not (Test-Path $source)) { return }
    $target = Join-Path $StageDir $relative
    New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
    Copy-Item $source $target -Force
}

# 源码与构建配置
Copy-Tree "app\src"
Copy-FileSafe "app\build.gradle"
Copy-FileSafe "app\proguard-rules.pro"
Copy-FileSafe "app\proguard-debug.pro"
Copy-FileSafe "build.gradle"
Copy-FileSafe "settings.gradle"
Copy-FileSafe ".gitignore"

# 签名与密钥（自己开发，完整保留）
Copy-Tree "signing"
Copy-FileSafe "keystore.properties"
Copy-FileSafe "local.properties"

# 脚本与文档
Copy-Tree "scripts"
Copy-Tree "docs"

Copy-FileSafe "README.md"
Copy-FileSafe "RELEASE_NOTES.md"
Copy-FileSafe "OPEN_SOURCE.md"
Copy-FileSafe "NOTICE"
Copy-FileSafe "LICENSE"

# 去掉文档目录里历史生成的源码包与成品 APK，避免包里有包、体积膨胀
Get-ChildItem $StageDir -Recurse -Include "fnOSTV-source-*.zip", "*.apk" -ErrorAction SilentlyContinue |
    Remove-Item -Force

# 开发计划/设计草稿（docs/superpowers）不进发布包
$plansDir = Join-Path $StageDir "docs\superpowers"
if (Test-Path $plansDir) { Remove-Item $plansDir -Recurse -Force }

$files = Get-ChildItem $StageDir -Recurse -File
if ($files.Count -lt 50) {
    throw "staging looks incomplete ($($files.Count) files)"
}

Compress-Archive -Path $StageDir -DestinationPath $ZipPath -CompressionLevel Optimal -Force
Remove-Item $StageDir -Recurse -Force

$sizeMb = [math]::Round((Get-Item $ZipPath).Length / 1MB, 2)
Write-Host "Source package: $ZipPath ($sizeMb MB, $($files.Count) files)"
