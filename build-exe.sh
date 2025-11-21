#!/bin/bash
# Script de generation d'executable Windows pour NasroulGestion
# Peut etre execute depuis Mac/Linux pour creer un EXE Windows
# Prerequis: Java JDK 17+ avec jpackage

echo "========================================"
echo "Creation de l'executable NasroulGestion"
echo "========================================"
echo ""

# Verification de Java
echo "Verification de Java..."
if ! command -v java &> /dev/null; then
    echo "ERREUR: Java n'est pas installe ou pas dans le PATH"
    exit 1
fi

# Trouver jpackage
JPACKAGE_CMD=""
if command -v jpackage &> /dev/null; then
    JPACKAGE_CMD="jpackage"
elif [ -n "$JAVA_HOME" ] && [ -f "$JAVA_HOME/bin/jpackage" ]; then
    JPACKAGE_CMD="$JAVA_HOME/bin/jpackage"
else
    # Chemin par defaut Windows (Git Bash convertit automatiquement)
    JPACKAGE_WIN='C:\Program Files\Java\jdk-25\bin\jpackage.exe'
    if [ -f "$JPACKAGE_WIN" ]; then
        JPACKAGE_CMD="$JPACKAGE_WIN"
    else
        echo "ERREUR: jpackage introuvable"
        echo ""
        echo "Solutions:"
        echo "1. Ajoutez le JDK bin au PATH"
        echo "2. Ou definissez JAVA_HOME vers votre JDK"
        echo "3. Ou modifiez le script avec le bon chemin"
        exit 1
    fi
fi

echo "Java detecte. Utilisation de: $JPACKAGE_CMD"
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
"$JPACKAGE_CMD" \
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
