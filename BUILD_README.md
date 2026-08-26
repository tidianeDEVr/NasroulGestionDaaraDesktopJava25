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

3. **WiX Toolset 3.x** (requis pour l'installateur `.exe` / `.msi`)
   - ⚠️ Il faut la branche **3.x**. WiX **4, 5 et 7 ne conviennent pas** : ils ont remplacé
     `candle.exe` et `light.exe` par un unique `wix.exe`, que le `jpackage` du JDK 21 ne sait
     pas piloter. Symptôme : `Can not find WiX tools (light.exe, candle.exe)`.
   - Installation sans dépendance .NET 3.5 (archive binaire) :
     ```batch
     powershell -Command "Invoke-WebRequest -Uri https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip -OutFile $env:TEMP\wix314.zip"
     powershell -Command "Expand-Archive -Path $env:TEMP\wix314.zip -DestinationPath C:\wix314 -Force"
     ```
     `build-exe.ps1` détecte `C:\wix314` automatiquement.
   - WiX 3.14 peut cohabiter avec une version 4+ déjà installée.
   - **Sans WiX** : `build-exe.bat 2.0.0 portable` produit une application portable (dossier).

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

   Sans WiX (application portable dans `dist\NasroulGestion\`, pas d'installateur) :
   ```batch
   build-exe.bat 2.0.0 portable
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

## Alternative : Launch4j

Launch4j est une autre façon de produire un `.exe`, mais avec une différence majeure :

> **Launch4j n'embarque pas Java.** L'exe généré est un simple lanceur qui cherche un JRE
> sur la machine. Si aucun Java n'est trouvé, il affiche
> **« This application requires a Java Runtime Environment. »**

C'est exactement le sens de cette erreur : le JAR est correct, c'est le runtime qui manque
(ou que Launch4j n'arrive pas à localiser).

### Solution recommandée : embarquer un runtime

Le script [build-launch4j.ps1](build-launch4j.ps1) génère un runtime Java avec `jlink`
et le place à côté de l'exe :

```batch
powershell -ExecutionPolicy Bypass -File build-launch4j.ps1
```

Résultat dans `dist/` :

```
dist\NasroulGestion.exe
dist\runtime\          <- runtime Java embarque
```

⚠️ Les deux doivent être distribués **ensemble** (zippez le dossier `dist`). L'exe seul
redonnera la même erreur.

### Sans runtime embarqué

```batch
powershell -ExecutionPolicy Bypass -File build-launch4j.ps1 -SkipRuntime
```

L'exe fait alors ~50 Mo et fonctionne seulement si un **JDK/JRE 17 ou supérieur** est installé
sur le poste. Dans l'interface Launch4j, l'équivalent est l'onglet **JRE** :

| Champ | Valeur |
|---|---|
| JRE paths / Bundled JRE path | `runtime` (ou vide si aucun runtime embarqué) |
| Min JRE version | `17` |
| Requires 64-bit | coché |
| JVM options | `--enable-native-access=javafx.graphics,ALL-UNNAMED` |

Laisser l'onglet **JRE** entièrement vide est la cause la plus fréquente de l'erreur.

### Configuration réutilisable

[launch4j.xml](launch4j.xml) contient la configuration complète (icône, version, options JVM).
Ouvrable dans l'interface Launch4j, ou en ligne de commande :

```batch
launch4jc.exe launch4j.xml
```

### Launch4j ou jpackage ?

| | jpackage ([build-exe.ps1](build-exe.ps1)) | Launch4j |
|---|---|---|
| Java embarqué | oui, automatiquement | non (sauf runtime `jlink` ajouté à la main) |
| Résultat | **installateur** `.exe` | exe portable + dossier `runtime` |
| Mise à jour des postes | gérée par Windows via la version + l'upgrade UUID | remplacement manuel des fichiers |
| Raccourcis Menu Démarrer / Bureau | oui | non |

Pour la diffusion aux hôtes, **jpackage reste la méthode conseillée** : c'est la seule qui gère
la mise à jour automatique d'une installation existante.


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

### Erreur : "Can not find WiX tools (light.exe, candle.exe)"

WiX 4/5/7 est installé, mais jpackage exige **WiX 3.x**. Voir les [Prérequis](#prérequis) :
installez `wix314-binaries.zip` dans `C:\wix314`, ou générez une application portable avec
`build-exe.bat 2.0.0 portable`.

### Erreur : "jpackage n'est pas reconnu" alors que java et javac fonctionnent

Le `PATH` pointe vers le raccourci Oracle `C:\Program Files\Common Files\Oracle\Java\javapath`,
qui ne contient que `java.exe`, `javaw.exe` et `javac.exe`. Localisez le vrai JDK puis ajoutez son
`bin` au PATH :

```batch
where /R "C:\Program Files" jpackage.exe
set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.12.1"
set "PATH=%JAVA_HOME%\bin;%PATH%"
```

### Erreur : "This application requires a Java Runtime Environment." (exe Launch4j)

L'exe Launch4j ne contient pas Java et n'en a trouvé aucun sur la machine. Voir la section
[Alternative : Launch4j](#alternative--launch4j) — utilisez `build-launch4j.ps1` pour embarquer
un runtime, ou renseignez l'onglet **JRE** (Min JRE version `17`).

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
