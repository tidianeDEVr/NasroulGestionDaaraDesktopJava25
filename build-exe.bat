@echo off
REM Script de generation d'executable Windows pour NasroulGestion
REM Prerequis: Java JDK 17+ avec jpackage

echo ========================================
echo Creation de l'executable NasroulGestion
echo ========================================
echo.

REM Verification de Java et jpackage
echo Verification de Java...
java -version 2>nul
if errorlevel 1 (
    echo ERREUR: Java n'est pas installe ou pas dans le PATH
    pause
    exit /b 1
)

REM Trouver jpackage
where jpackage >nul 2>nul
if errorlevel 1 (
    echo AVERTISSEMENT: jpackage n'est pas dans le PATH
    echo Tentative de localisation via JAVA_HOME...
    if "%JAVA_HOME%"=="" (
        echo JAVA_HOME non defini, utilisation du chemin par defaut...
        set "JPACKAGE_CMD=C:\Program Files\Java\jdk-25\bin\jpackage.exe"
    ) else (
        set "JPACKAGE_CMD=%JAVA_HOME%\bin\jpackage.exe"
    )
) else (
    set "JPACKAGE_CMD=jpackage"
)

REM Verification que jpackage existe
if not exist "%JPACKAGE_CMD%" (
    if "%JPACKAGE_CMD%"=="jpackage" (
        echo ERREUR: jpackage introuvable
    ) else (
        echo ERREUR: jpackage introuvable a: %JPACKAGE_CMD%
    )
    echo.
    echo Solutions:
    echo 1. Ajoutez C:\Program Files\Java\jdk-25\bin au PATH
    echo 2. Ou definissez JAVA_HOME=C:\Program Files\Java\jdk-25
    echo 3. Ou modifiez le script avec le bon chemin vers votre JDK
    pause
    exit /b 1
)

echo Java detecte. Utilisation de: %JPACKAGE_CMD%
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
%JPACKAGE_CMD% ^
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
