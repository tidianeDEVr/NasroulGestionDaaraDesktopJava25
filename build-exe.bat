@echo off
REM build-exe.bat - Lance la generation de l'installateur Windows depuis cmd.exe
REM Usage :  build-exe.bat            (version par defaut)
REM          build-exe.bat 2.0.1      (version personnalisee)

setlocal
set "APPVER=%~1"
if "%APPVER%"=="" set "APPVER=2.0.0"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-exe.ps1" -AppVersion %APPVER%
set "RC=%ERRORLEVEL%"

if not "%RC%"=="0" (
  echo.
  echo ERREUR: la generation a echoue ^(code %RC%^).
)
pause
exit /b %RC%
