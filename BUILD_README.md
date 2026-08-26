# Guide de Génération de l'Exécutable Windows

Ce guide explique comment créer un fichier .exe pour l'application NasroulGestion sur Windows.

## Prérequis

1. **Java JDK 17 ou supérieur** (pas JRE, il faut le JDK complet)
   - Télécharger depuis [Oracle](https://www.oracle.com/java/technologies/downloads/) ou [Adoptium](https://adoptium.net/)
   - Vérifier l'installation : `java -version`

2. **Maven** — *facultatif*
   - Le projet embarque le **wrapper Maven** (`mvnw.cmd`) : aucune installation n'est nécessaire.
   - ⚠️ `mvnw.cmd` seul ne fait rien : il faut **toujours lui passer un goal**, sinon Maven répond
     `No goals have been specified for this build`. Exemple correct : `mvnw.cmd clean package -P windows`.
   - Si vous préférez un Maven installé : [Maven Apache](https://maven.apache.org/download.cgi), vérifier avec `mvn -version`

3. **WiX Toolset** (optionnel, pour l'installateur MSI)
   - Télécharger depuis [WiX Toolset](https://wixtoolset.org/releases/)

## Génération de l'Exécutable

### Méthode Simple (Script Automatique)

1. Ouvrir le terminal Windows dans le dossier du projet
2. Exécuter le script :
   ```batch
   REM depuis cmd.exe
   build-exe.bat

   REM ou depuis PowerShell
   .\build-exe.ps1
   ```

   Pour forcer un autre numéro de version :
   ```batch
   build-exe.bat 2.0.1
   ```

3. L'exécutable sera créé dans le dossier `dist/` avec le nom `NasroulGestion-2.0.0.exe`

Le script utilise automatiquement `mvnw.cmd` s'il est présent, sinon `mvn` du PATH.

### Méthode Manuelle

Si vous préférez exécuter les commandes manuellement :

1. **Compiler le projet et créer le JAR** :
   ```batch
   mvnw.cmd clean package -P windows
   ```
   (ou `mvn clean package -P windows` si Maven est installé globalement)

2. **Créer l'exécutable avec jpackage** :
   ```batch
   jpackage --input target ^
       --name NasroulGestion ^
       --main-jar AssociationManager-2.0.0.jar ^
       --main-class com.nasroul.Launcher ^
       --type exe ^
       --icon src/main/resources/images/icon.ico ^
       --dest dist ^
       --win-shortcut ^
       --win-menu ^
       --win-upgrade-uuid 8a4f3c7b-1d2e-4f6a-9b8c-3e5d7a1c9f4b ^
       --app-version 2.0.0 ^
       --vendor "Nasroul" ^
       --description "Gestionnaire d'Association Nasroul" ^
       --java-options "--enable-native-access=javafx.graphics,ALL-UNNAMED"
   ```

## Résultat

Le script génère :
- **Un installateur EXE** : `dist/NasroulGestion-2.0.0.exe`
- **Des raccourcis automatiques** dans le Menu Démarrer
- **Option de raccourci** sur le Bureau lors de l'installation

## Options Supplémentaires

### Créer un MSI au lieu d'un EXE

Remplacer `--type exe` par `--type msi` dans la commande jpackage (nécessite WiX Toolset).

### Personnaliser la Version (IMPORTANT pour les mises à jour)

La version courante est **2.0.0**. Elle est définie à trois endroits qui doivent rester cohérents :

| Endroit | Rôle |
|---|---|
| `pom.xml` → `<version>` | nom du JAR produit (`AssociationManager-2.0.0.jar`) |
| `build-exe.ps1` → `-AppVersion` | version de l'installateur Windows |
| `src/main/resources/fxml/MainView.fxml` | version affichée dans l'application |

**Règle de mise à jour des postes déjà installés :** Windows n'applique une mise à jour que si
le nouvel installateur a un **numéro de version strictement supérieur** à celui déjà installé,
**et** le même `--win-upgrade-uuid` (`8a4f3c7b-1d2e-4f6a-9b8c-3e5d7a1c9f4b`, à ne jamais modifier).
Si vous rediffusez un EXE avec la même version, l'installation existante n'est pas remplacée.

Donc à chaque livraison : incrémentez la version (2.0.0 → 2.0.1 → 2.1.0 …) avant de builder.
Format imposé par Windows : `majeur.mineur.correctif` en chiffres uniquement (pas de `-SNAPSHOT`,
majeur ≤ 255).

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

### `mvnw.cmd` affiche « No goals have been specified for this build »

Ce n'est pas une erreur du wrapper : Maven fonctionne, mais aucune tâche ne lui a été demandée.
Ajoutez le goal : `mvnw.cmd clean package -P windows`.

### Erreur lors de la compilation Maven

- Utilisez le wrapper : `mvnw.cmd clean package -P windows` (pas besoin d'installer Maven)
- Si vous utilisez Maven global, vérifiez : `mvn -version`
- Vérifiez votre connexion internet (Maven télécharge les dépendances)

### L'icône ne s'affiche pas

- Vérifiez que le fichier `src/main/resources/images/icon.ico` existe
- Le fichier doit être au format .ico Windows (pas .png)

## Distribution

Une fois l'exécutable créé, vous pouvez :
1. Distribuer directement le fichier `NasroulGestion-2.0.0.exe`
2. Les utilisateurs l'exécutent pour installer l'application
3. L'application sera installée dans `C:\Program Files\NasroulGestion\`
4. Un raccourci sera créé dans le Menu Démarrer

## Notes Importantes

- L'exécutable inclut déjà Java, pas besoin d'installer Java séparément
- La taille de l'installateur sera d'environ 100-150 MB (inclut JavaFX et toutes les dépendances)
- L'application fonctionne uniquement sur Windows
