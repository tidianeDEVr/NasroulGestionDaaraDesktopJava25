#!/bin/bash
# Script de generation d'executable Windows pour NasroulGestion
# Peut etre execute depuis Mac/Linux pour creer un EXE Windows
# Prerequis: Java JDK 17+ avec jpackage

echo "========================================"
echo "Creation de l'executable NasroulGestion"
echo "========================================"
echo ""

# 1. Compilation et creation du fat JAR
echo "[1/3] Compilation et creation du JAR..."
mvn clean package -P windows
if [ $? -ne 0 ]; then
    echo "ERREUR: La compilation a echoue"
    exit 1
fi

# 2. Verification que le JAR existe
if [ ! -f "target/AssociationManager-1.0-SNAPSHOT.jar" ]; then
    echo "ERREUR: Le fichier JAR n'a pas ete cree"
    exit 1
fi

# 3. Creation du repertoire de sortie
mkdir -p dist

# 4. Creation de l'executable avec jpackage
echo ""
echo "[2/3] Creation de l'executable Windows..."
jpackage \
    --input target \
    --name NasroulGestion \
    --main-jar AssociationManager-1.0-SNAPSHOT.jar \
    --main-class com.nasroul.AssociationApp \
    --type exe \
    --icon src/main/resources/images/icon.ico \
    --dest dist \
    --win-shortcut \
    --win-menu \
    --app-version 1.0 \
    --vendor "Nasroul" \
    --description "Gestionnaire d'Association Nasroul" \
    --java-options "--enable-native-access=javafx.graphics,ALL-UNNAMED"

if [ $? -ne 0 ]; then
    echo "ERREUR: La creation de l'executable a echoue"
    echo "Note: jpackage peut avoir des limitations pour creer des EXE Windows depuis Mac/Linux"
    echo "Pour de meilleurs resultats, executez ce script sur Windows"
    exit 1
fi

echo ""
echo "[3/3] Termine!"
echo ""
echo "========================================"
echo "SUCCES!"
echo "========================================"
echo "L'executable a ete cree dans le dossier 'dist'"
echo "Fichier: dist/NasroulGestion-1.0.exe"
echo ""
echo "L'installateur cree egalement des raccourcis dans:"
echo "- Menu Demarrer"
echo "- Bureau (si demande)"
echo ""
