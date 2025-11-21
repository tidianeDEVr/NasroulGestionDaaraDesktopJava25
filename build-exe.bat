@echo off
setlocal enabledelayedexpansion
REM build-exe-native.bat - version native sans tee

echo ========================================
echo Creation de l'executable NasroulGestion
echo ========================================
echo.

REM Vérifier java
where java >nul 2>nul
if errorlevel 1 (
  echo ERREUR: Java n'est pas installe ou pas dans le PATH.
  pause
  exit /b 1
)
java -version
echo.

REM Detecter jpackage
set "JPACKAGE_CMD="
for /f "usebackq delims=" %%i in (`where jpackage 2^>nul ^| findstr /I /R "jpackage"`) do (
  set "JPACKAGE_CMD=%%~i"
  goto :jpackage_found
)
:jpackage_found
if "%JPACKAGE_CMD%"=="" (
  echo ERREUR: jpackage n'est pas disponible dans le PATH.
  pause
  exit /b 1
)
echo jpackage detecte: "%JPACKAGE_CMD%"
echo.

REM Vérifier mvn
where mvn >nul 2>nul
if errorlevel 1 (
  echo ERREUR: Maven (mvn) introuvable.
  pause
  exit /b 1
)
mvn -v
echo.

REM Build maven
echo [1/3] Compilation Maven...
call mvn clean package -P windows
if errorlevel 1 (
  echo ERREUR: La compilation Maven a échoue.
  pause
  exit /b 1
)

REM Trouver le JAR
set "TARGET_DIR=%CD%\target"
set "JAR_FILE="
for %%f in ("%TARGET_DIR%\*.jar") do (
  echo %%~nxf | findstr /I /C:"-sources" /C:"-javadoc" >nul
  if errorlevel 1 (
    if not defined JAR_FILE set "JAR_FILE=%%~nxf"
  )
)
if not defined JAR_FILE (
  echo ERREUR: Aucun JAR trouve dans %TARGET_DIR%.
  pause
  exit /b 1
)
echo JAR utilise: %TARGET_DIR%\%JAR_FILE%
echo.

REM Préparer dist
set "DEST_DIR=%CD%\dist"
if not exist "%DEST_DIR%" mkdir "%DEST_DIR%"

REM Appel jpackage (redirection vers log)
echo [2/3] Creation de l'executable Windows...
"%JPACKAGE_CMD%" ^
  --input "%TARGET_DIR%" ^
  --name "NasroulGestion" ^
  --main-jar "%JAR_FILE%" ^
  --main-class "com.nasroul.AssociationApp" ^
  --type exe ^
  --icon "%CD%\src\main\resources\images\icon.ico" ^
  --dest "%DEST_DIR%" ^
  --win-shortcut ^
  --win-menu ^
  --app-version "1.0" ^
  --vendor "Nasroul" ^
  --description "Gestionnaire d'Association Nasroul" ^
  --java-options "--enable-native-access=javafx.graphics,ALL-UNNAMED" > jpackage-output.log 2>&1

if errorlevel 1 (
  echo.
  echo ERREUR: La creation de l'executable a echoue. Voir jpackage-output.log pour les details.
  type jpackage-output.log | more
  pause
  exit /b 1
)

echo.
echo [3/3] Terminé !
echo ========================================
echo SUCCES !
echo ========================================
echo L'executable a ete cree dans '%DEST_DIR%'
dir "%DEST_DIR%"
echo.
type jpackage-output.log | more
pause
endlocal
exit /b 0
