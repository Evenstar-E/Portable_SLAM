param(
    [string[]]$GradleArgs = @(':app:assembleDebug', ':app:testDebugUnitTest')
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$wrapper = Join-Path $projectRoot 'gradlew.bat'

$jdkHome = $env:JAVA_HOME
if ([string]::IsNullOrWhiteSpace($jdkHome)) {
    $jdkHome = Join-Path $projectRoot '.toolchains\jdk17\jdk-17.0.21.0.2+1'
}

$sdkHome = $env:ANDROID_HOME
if ([string]::IsNullOrWhiteSpace($sdkHome)) {
    $sdkHome = $env:ANDROID_SDK_ROOT
}
if ([string]::IsNullOrWhiteSpace($sdkHome)) {
    $localProperties = Join-Path $projectRoot 'local.properties'
    if (Test-Path $localProperties) {
        $sdkLine = Get-Content $localProperties |
            Where-Object { $_ -match '^\s*sdk\.dir\s*=' } |
            Select-Object -First 1
        if ($null -ne $sdkLine) {
            $sdkHome = ($sdkLine -split '=', 2)[1].Trim()
        }
    }
}
if ([string]::IsNullOrWhiteSpace($sdkHome)) {
    $sdkHome = Join-Path $projectRoot '.toolchains\android-sdk'
}

$gradleUserHome = $env:GRADLE_USER_HOME
if ([string]::IsNullOrWhiteSpace($gradleUserHome)) {
    $gradleUserHome = Join-Path $projectRoot '.toolchains\gradle-home'
}

$java = Join-Path $jdkHome 'bin\java.exe'
foreach ($path in @($java, $sdkHome, $wrapper)) {
    if (-not (Test-Path $path)) {
        throw "Required build path is missing: $path. Set JAVA_HOME and ANDROID_HOME, or see docs/SETUP_OTHER_COMPUTER.md."
    }
}

$env:JAVA_HOME = $jdkHome
$env:ANDROID_HOME = $sdkHome
$env:ANDROID_SDK_ROOT = $sdkHome
$env:GRADLE_USER_HOME = $gradleUserHome
$env:PATH = "$jdkHome\bin;$sdkHome\platform-tools;$env:PATH"

"JAVA_HOME=$jdkHome"
"ANDROID_HOME=$sdkHome"
"GRADLE_USER_HOME=$gradleUserHome"

& $wrapper --project-dir $projectRoot --no-daemon @GradleArgs
if ($LASTEXITCODE -ne 0) {
    throw "Gradle failed with exit code $LASTEXITCODE"
}
