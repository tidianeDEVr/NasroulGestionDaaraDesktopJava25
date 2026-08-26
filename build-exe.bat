@echo off
REM build-exe.bat - Lance la generation de l'executable Windows depuis cmd.exe
REM Usage :  build-exe.bat                 (installateur, version par defaut)
REM          build-exe.bat 2.0.1           (installateur, version personnalisee)
REM          build-exe.bat 2.0.1 portable  (application portable, sans WiX)

setlocal
set "APPVER=%~1"
if "%APPVER%"=="" set "APPVER=2.0.0"

set "EXTRA="
if /i "%~2"=="portable" set "EXTRA=-Portable"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-exe.ps1" -AppVersion %APPVER% %EXTRA%
set "RC=%ERRORLEVEL%"

if not "%RC%"=="0" (
  echo.
  echo ERREUR: la generation a echoue ^(code %RC%^).
)
pause
exit /b %RC%
