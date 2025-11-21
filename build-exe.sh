#!/usr/bin/env bash
# Script de génération d'exécutable Windows pour NasroulGestion
# Fonctionne si jpackage et Java sont dans le PATH (Windows, WSL, Mac/Linux avec JDK)
set -o pipefail

echo "========================================"
echo "Creation de l'executable NasroulGestion"
echo "========================================"
echo ""

# --- Configuration ---
APP_NAME="NasroulGestion"
MAIN_CLASS="com.nasroul.AssociationApp"
ICON_PATH="src/main/resources/images/icon.ico"
OUTPUT_DIR="dist"
TARGET_DIR="target"
PROFILE="windows"
EXPECTED_JAR="AssociationManager-1.0-SNAPSHOT.jar"
APP_VERSION="1.0"
VENDOR="Nasroul"
DESCRIPTION="Gestionnaire d'Association Nasroul"
# ---------------------

# Vérifier Maven
if ! command -v mvn &> /dev/null; then
    echo "ERREUR: Maven (mvn) est introuvable."
    exit 1
fi

# Vérifier Java
if ! command -v java &> /dev/null; then
    echo "ERREUR: Java n'est pas installé ou pas dans le PATH."
    exit 1
fi

# Vérifier jpackage
if ! command -v jpackage &> /dev/null; then
    echo "ERREUR: jpackage n'est pas accessible dans le PATH."
    echo "Assurez-vous que le JDK est correctement installé."
    exit 1
fi

echo "Java détecté."
echo "jpackage détecté."
echo ""

# Compilation du JAR
echo "[1/3] Compilation du projet..."
mvn clean package -P "$PROFILE"
if [ $? -ne 0 ]; then
    echo "ERREUR: Compilation échouée."
    exit 1
fi

# Vérifier le JAR
JAR_PATH="$TARGET_DIR/$EXPECTED_JAR"
if [ ! -f "$JAR_PATH" ]; then
    echo "Jar attendu non trouvé : $JAR_PATH"
    echo "Recherche automatique..."
    found=$(ls "$TARGET_DIR"/*.jar | grep -v 'sources' | head -n 1)
    if [ -z "$found" ]; then
        echo "ERREUR: Aucun JAR trouvé dans target/"
        exit 1
    fi
    JAR_PATH="$found"
    echo "Jar trouvé : $JAR_PATH"
fi

# Créer dossier dist
mkdir -p "$OUTPUT_DIR"

# Création de l'exécutable
echo ""
echo "[2/3] Création du fichier EXE..."

jpackage \
    --input "$TARGET_DIR" \
    --name "$APP_NAME" \
    --main-jar "$(basename "$JAR_PATH")" \
    --main-class "$MAIN_CLASS" \
    --type exe \
    --icon "$ICON_PATH" \
    --dest "$OUTPUT_DIR" \
    --win-shortcut \
    --win-menu \
    --app-version "$APP_VERSION" \
    --vendor "$VENDOR" \
    --description "$DESCRIPTION" \
    --java-options "--enable-native-access=javafx.graphics,ALL-UNNAMED"

if [ $? -ne 0 ]; then
    echo "ERREUR: jpackage a échoué."
    exit 1
fi

echo ""
echo "[3/3] Terminé !"
echo ""
echo "========================================"
echo "SUCCÈS !"
echo "========================================"
echo "L'exécutable est disponible dans '$OUTPUT_DIR/'"
ls -l "$OUTPUT_DIR"
echo ""
