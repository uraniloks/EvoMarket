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

# Bootstrap Gradle in the user's local cache instead of shipping wrapper files.
$gradleVersion = '9.7.0'
$archiveName = "gradle-$gradleVersion-bin.zip"
$distributionUrl = "https://services.gradle.org/distributions/$archiveName"
$expectedSha256 = '84fbba45c7f4c64abc77460e1c00f541e9f960e3c7ed2538f1ede19eacd873ae'
$cacheRoot = Join-Path $env:LOCALAPPDATA 'EvoMarket\gradle'
$gradleHome = Join-Path $cacheRoot "gradle-$gradleVersion"
$gradleExe = Join-Path $gradleHome 'bin\gradle.bat'

if (-not (Test-Path $gradleExe)) {
    New-Item -ItemType Directory -Force -Path $cacheRoot | Out-Null
    $archivePath = Join-Path $env:TEMP $archiveName
    Write-Host "Gradle $gradleVersion is not cached. Downloading it..." -ForegroundColor Cyan
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $distributionUrl -OutFile $archivePath -UseBasicParsing

    $actualSha256 = (Get-FileHash -Path $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualSha256 -ne $expectedSha256) {
        Remove-Item $archivePath -Force -ErrorAction SilentlyContinue
        throw 'Gradle archive SHA-256 verification failed. The archive was not extracted.'
    }

    Expand-Archive -Path $archivePath -DestinationPath $cacheRoot -Force
    Remove-Item $archivePath -Force
}

if (-not (Test-Path $gradleExe)) {
    throw "Gradle executable not found after setup: $gradleExe"
}

Write-Host 'Downloading dependencies and building EvoMarket...' -ForegroundColor Cyan
& $gradleExe --no-daemon clean build
$gradleExitCode = $LASTEXITCODE
if ($gradleExitCode -ne 0) {
    exit $gradleExitCode
}

$versionLine = Get-Content (Join-Path $PSScriptRoot 'gradle.properties') | Where-Object { $_ -match '^mod_version=' }
$modVersion = ($versionLine -split '=', 2)[1].Trim()
$jarPath = Join-Path $PSScriptRoot "build\libs\evomarket-$modVersion.jar"
if (-not (Test-Path $jarPath)) {
    Write-Host '[ERROR] Gradle finished, but the expected JAR was not found.' -ForegroundColor Red
    exit 2
}
Write-Host "SUCCESS: $jarPath" -ForegroundColor Green
