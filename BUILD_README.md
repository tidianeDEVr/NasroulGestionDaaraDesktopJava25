# Guide de Génération de l'Exécutable Windows

Ce guide explique comment créer un fichier .exe pour l'application NasroulGestion sur Windows.

## Prérequis

1. **Java JDK 17 ou supérieur** (pas JRE, il faut le JDK complet)
   - Télécharger depuis [Oracle](https://www.oracle.com/java/technologies/downloads/) ou [Adoptium](https://adoptium.net/)
   - Vérifier l'installation : `java -version`

2. **Maven**
   - Télécharger depuis [Maven Apache](https://maven.apache.org/download.cgi)
   - Vérifier l'installation : `mvn -version`

3. **WiX Toolset** (optionnel, pour l'installateur MSI)
   - Télécharger depuis [WiX Toolset](https://wixtoolset.org/releases/)

## Génération de l'Exécutable

### Méthode Simple (Script Automatique)

1. Ouvrir le terminal Windows (cmd ou PowerShell) dans le dossier du projet
2. Exécuter le script :
   ```batch
   build-exe.bat
   ```

3. L'exécutable sera créé dans le dossier `dist/` avec le nom `NasroulGestion-1.0.exe`

### Méthode Manuelle

Si vous préférez exécuter les commandes manuellement :

1. **Compiler le projet et créer le JAR** :
   ```batch
   mvn clean package -P windows
   ```

2. **Créer l'exécutable avec jpackage** :
   ```batch
   jpackage --input target ^
       --name NasroulGestion ^
       --main-jar AssociationManager-1.0-SNAPSHOT.jar ^
       --main-class com.nasroul.Launcher ^
       --type exe ^
       --icon src/main/resources/images/icon.ico ^
       --dest dist ^
       --win-shortcut ^
       --win-menu ^
       --app-version 1.0 ^
       --vendor "Nasroul" ^
       --description "Gestionnaire d'Association Nasroul" ^
       --java-options "--enable-native-access=javafx.graphics,ALL-UNNAMED"
   ```

## Résultat

Le script génère :
- **Un installateur EXE** : `dist/NasroulGestion-1.0.exe`
- **Des raccourcis automatiques** dans le Menu Démarrer
- **Option de raccourci** sur le Bureau lors de l'installation

## Options Supplémentaires

### Créer un MSI au lieu d'un EXE

Remplacer `--type exe` par `--type msi` dans la commande jpackage (nécessite WiX Toolset).

### Personnaliser la Version

Modifier `--app-version 1.0` avec votre numéro de version souhaité.

### Ajouter plus de mémoire Java

Modifier `--java-options` pour ajouter des paramètres comme :
```
--java-options "-Xmx2048m --enable-native-access=javafx.graphics,ALL-UNNAMED"
```

## Dépannage

### Erreur : "jpackage n'est pas reconnu"

- Vérifiez que vous utilisez un JDK (pas JRE)
- Vérifiez que `JAVA_HOME` pointe vers le JDK
- Vérifiez que `%JAVA_HOME%\bin` est dans votre PATH

### Erreur lors de la compilation Maven

- Vérifiez que Maven est correctement installé : `mvn -version`
- Vérifiez votre connexion internet (Maven télécharge les dépendances)

### L'icône ne s'affiche pas

- Vérifiez que le fichier `src/main/resources/images/icon.ico` existe
- Le fichier doit être au format .ico Windows (pas .png)

## Distribution

Une fois l'exécutable créé, vous pouvez :
1. Distribuer directement le fichier `NasroulGestion-1.0.exe`
2. Les utilisateurs l'exécutent pour installer l'application
3. L'application sera installée dans `C:\Program Files\NasroulGestion\`
4. Un raccourci sera créé dans le Menu Démarrer

## Notes Importantes

- L'exécutable inclut déjà Java, pas besoin d'installer Java séparément
- La taille de l'installateur sera d'environ 100-150 MB (inclut JavaFX et toutes les dépendances)
- L'application fonctionne uniquement sur Windows
