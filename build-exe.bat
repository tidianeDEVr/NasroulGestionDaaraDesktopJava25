@echo off
REM Script de generation d'executable Windows pour NasroulGestion
REM Prerequis: Java JDK 17+ avec jpackage

echo ========================================
echo Creation de l'executable NasroulGestion
echo ========================================
echo.

REM 1. Compilation et creation du fat JAR
echo [1/3] Compilation et creation du JAR...
call mvn clean package -P windows
if errorlevel 1 (
    echo ERREUR: La compilation a echoue
    pause
    exit /b 1
)

REM 2. Verification que le JAR existe
if not exist "target\AssociationManager-1.0-SNAPSHOT.jar" (
    echo ERREUR: Le fichier JAR n'a pas ete cree
    pause
    exit /b 1
)

REM 3. Creation du repertoire de sortie
if not exist "dist" mkdir dist

REM 4. Creation de l'executable avec jpackage
echo.
echo [2/3] Creation de l'executable Windows...
jpackage ^
    --input target ^
    --name NasroulGestion ^
    --main-jar AssociationManager-1.0-SNAPSHOT.jar ^
    --main-class com.nasroul.AssociationApp ^
    --type exe ^
    --icon src/main/resources/images/icon.ico ^
    --dest dist ^
    --win-shortcut ^
    --win-menu ^
    --app-version 1.0 ^
    --vendor "Nasroul" ^
    --description "Gestionnaire d'Association Nasroul" ^
    --java-options "--enable-native-access=javafx.graphics,ALL-UNNAMED"

if errorlevel 1 (
    echo ERREUR: La creation de l'executable a echoue
    echo Verifiez que vous utilisez Java JDK 17+ avec jpackage
    pause
    exit /b 1
)

echo.
echo [3/3] Nettoyage...
echo.
echo ========================================
echo SUCCES!
echo ========================================
echo L'executable a ete cree dans le dossier 'dist'
echo Fichier: dist\NasroulGestion-1.0.exe
echo.
echo L'installateur cree egalement des raccourcis dans:
echo - Menu Demarrer
echo - Bureau (si demande)
echo.
pause
