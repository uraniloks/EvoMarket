# ASCII-only script for compatibility with Windows PowerShell 5.1.
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
Set-Location $PSScriptRoot

# Minecraft 26.2 requires JDK 25 or newer.
$javaOutput = & $env:ComSpec /d /c 'java -version 2>&1'
$javaExitCode = $LASTEXITCODE
$javaOutput = $javaOutput -join [Environment]::NewLine
if ($javaExitCode -ne 0) {
    Write-Host '[ERROR] Could not run Java from PATH.' -ForegroundColor Red
    Write-Host $javaOutput
    Write-Host 'Install JDK 25 or newer and make sure Java is on PATH.'
    exit 1
}

$versionMatch = [regex]::Match($javaOutput, 'version "([0-9]+)(?:\.[^"]*)?"')
if (-not $versionMatch.Success) {
    $versionMatch = [regex]::Match($javaOutput, 'openjdk ([0-9]+)(?:\.[^\s]*)?')
}
if (-not $versionMatch.Success -or [int]$versionMatch.Groups[1].Value -lt 25) {
    Write-Host '[ERROR] JDK 25 or newer is required.' -ForegroundColor Red
    Write-Host 'Download JDK 25: https://adoptium.net/temurin/releases/?version=25'
    exit 1
}
Write-Host "Java detected: $($versionMatch.Groups[1].Value)" -ForegroundColor Green

$gradleVersion = '9.7.0'
$cacheRoot = Join-Path $env:LOCALAPPDATA 'EvoMarketBuild'
$gradleHome = Join-Path $cacheRoot "gradle-$gradleVersion"
$gradleExe = Join-Path $gradleHome 'bin\gradle.bat'

if (-not (Test-Path $gradleExe)) {
    New-Item -ItemType Directory -Force -Path $cacheRoot | Out-Null
    $zipPath = Join-Path $cacheRoot "gradle-$gradleVersion-bin.zip"
    $url = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
    Write-Host "Downloading Gradle $gradleVersion (only needed for the first build)..." -ForegroundColor Cyan
    Invoke-WebRequest -Uri $url -OutFile $zipPath
    Expand-Archive -Path $zipPath -DestinationPath $cacheRoot -Force
}

Write-Host 'Downloading dependencies and building EvoMarket...' -ForegroundColor Cyan
& $gradleExe --no-daemon clean build
$gradleExitCode = $LASTEXITCODE
if ($gradleExitCode -ne 0) {
    exit $gradleExitCode
}

$jarPath = Join-Path $PSScriptRoot 'build\libs\evomarket-0.1.18.jar'
if (-not (Test-Path $jarPath)) {
    Write-Host '[ERROR] Gradle finished, but the expected JAR was not found.' -ForegroundColor Red
    exit 2
}
Write-Host "SUCCESS: $jarPath" -ForegroundColor Green
