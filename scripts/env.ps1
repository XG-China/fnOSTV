$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$ToolingRoot = Join-Path $ProjectRoot ".tooling"
$JdkRoot = Join-Path $ToolingRoot "jdk\jdk-11.0.31+11"
if (-not (Test-Path $JdkRoot)) {
    $JdkCandidate = Get-ChildItem (Join-Path $ToolingRoot "jdk") -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName "bin\javac.exe") } |
        Select-Object -First 1
    if ($JdkCandidate) { $JdkRoot = $JdkCandidate.FullName }
}
$GradleRoot = Join-Path $ToolingRoot "gradle\gradle-6.7.1"
$AndroidSdkRoot = Join-Path $ToolingRoot "android-sdk"
$AndroidAvdRoot = Join-Path $ToolingRoot "avd"

if (-not (Test-Path $JdkRoot)) {
    throw "Missing JDK at $JdkRoot. Run scripts\setup-android-env.ps1 first."
}
if (-not (Test-Path $GradleRoot)) {
    throw "Missing Gradle at $GradleRoot. Run scripts\setup-android-env.ps1 first."
}
if (-not (Test-Path $AndroidSdkRoot)) {
    throw "Missing Android SDK at $AndroidSdkRoot. Run scripts\setup-android-env.ps1 first."
}

$env:JAVA_HOME = (Resolve-Path $JdkRoot).Path
$env:ANDROID_HOME = (Resolve-Path $AndroidSdkRoot).Path
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
New-Item -ItemType Directory -Force $AndroidAvdRoot | Out-Null
$env:ANDROID_AVD_HOME = (Resolve-Path $AndroidAvdRoot).Path
$AndroidTools = "$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:ANDROID_HOME\emulator;$env:ANDROID_HOME\platform-tools"
$env:Path = "$env:JAVA_HOME\bin;$GradleRoot\bin;$AndroidTools;$env:Path"

$localProperties = Join-Path $ProjectRoot "local.properties"
$escapedSdk = $env:ANDROID_HOME.Replace("\", "\\").Replace(":", "\:")
[System.IO.File]::WriteAllText($localProperties, "sdk.dir=$escapedSdk`r`n", [System.Text.Encoding]::ASCII)
