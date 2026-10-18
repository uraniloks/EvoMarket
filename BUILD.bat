@echo off
setlocal
cd /d "%~dp0"
echo ==============================================
echo EvoMarket 0.1.18 - Minecraft 26.2 build
echo ==============================================
echo.
where java >nul 2>&1
if errorlevel 1 goto nojava
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1"
set "RESULT=%ERRORLEVEL%"
echo.
if "%RESULT%"=="0" goto success
echo BUILD FAILED. Copy all error text and send it to ChatGPT.
goto finish
:nojava
echo Java was not found in PATH.
echo Install JDK 25: https://adoptium.net/temurin/releases/?version=25
echo During installation, enable adding Java to PATH.
set "RESULT=1"
goto finish
:success
echo BUILD SUCCESSFUL.
echo Expected JAR: %~dp0build\libs\evomarket-0.1.18.jar
echo Copy it into the mods folder of your Minecraft 26.2 Fabric profile.
set "RESULT=0"
:finish
echo.
pause
exit /b %RESULT%
