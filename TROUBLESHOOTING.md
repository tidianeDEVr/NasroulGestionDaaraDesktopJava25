# Guide de Dépannage - NasroulGestion

Ce guide vous aide à diagnostiquer et résoudre les problèmes courants avec l'application.

## Problème : L'installateur se ferme immédiatement avec un son d'erreur

### Diagnostic

1. **Vérifier les logs Windows**
   - Ouvrez l'Observateur d'événements Windows (Event Viewer)
   - Allez dans `Windows Logs` > `Application`
   - Cherchez les erreurs récentes liées à "NasroulGestion" ou "jpackage"

2. **Nettoyer complètement avant de rebuilder**
   ```powershell
   # Supprimer les anciens fichiers
   Remove-Item -Path dist -Recurse -Force -ErrorAction SilentlyContinue
   Remove-Item -Path target -Recurse -Force -ErrorAction SilentlyContinue

   # Rebuilder
   .\build-exe.ps1
   ```

3. **Vérifier que le JAR est correct**
   Après la compilation, vérifiez que le script affiche :
   ```
   Main-Class dans le JAR: com.nasroul.Launcher
   ```

   Si ce n'est pas le cas, il y a un problème avec la compilation Maven.

4. **Tester le JAR directement**
   Avant de créer l'installateur, testez si le JAR fonctionne :
   ```powershell
   cd target
   java -jar AssociationManager-1.0-SNAPSHOT.jar
   ```

   Si le JAR ne fonctionne pas, le problème est dans le code, pas dans l'installateur.

### Solutions

#### Solution 1 : Vérifier Java et jpackage

Assurez-vous d'utiliser la bonne version de Java :
```powershell
java -version
# Doit afficher Java 17 ou supérieur

jpackage --version
# Doit afficher une version
```

#### Solution 2 : Désinstaller l'ancienne version

Si une ancienne version est installée :
1. Ouvrez `Paramètres` > `Applications`
2. Cherchez "NasroulGestion"
3. Désinstallez complètement
4. Redémarrez votre ordinateur
5. Réinstallez avec le nouveau `.exe`

#### Solution 3 : Exécuter l'installateur en tant qu'administrateur

1. Clic droit sur le fichier `.exe` dans le dossier `dist`
2. Sélectionnez "Exécuter en tant qu'administrateur"

#### Solution 4 : Vérifier l'antivirus

Votre antivirus pourrait bloquer l'installateur :
1. Désactivez temporairement l'antivirus
2. Essayez d'installer à nouveau
3. Si cela fonctionne, ajoutez une exception pour NasroulGestion

## Problème : L'application s'installe mais ne se lance pas

### Diagnostic

1. **Chercher les logs de l'application**
   L'application devrait créer des logs dans :
   - `C:\Users\[VotreNom]\AppData\Local\NasroulGestion\logs\`
   - Ou dans le dossier d'installation

2. **Lancer depuis la ligne de commande**
   ```cmd
   cd "C:\Program Files\NasroulGestion"
   .\NasroulGestion.exe
   ```
   Cela pourrait afficher des messages d'erreur utiles.

### Solutions

#### Solution 1 : Vérifier les dépendances

Assurez-vous que tous les fichiers sont présents dans :
```
C:\Program Files\NasroulGestion\
```

Vous devriez voir :
- `NasroulGestion.exe`
- Dossier `app/` avec des fichiers JAR
- Dossier `runtime/` avec Java

#### Solution 2 : Réinstaller complètement

1. Désinstaller l'application
2. Supprimer manuellement le dossier `C:\Program Files\NasroulGestion\`
3. Supprimer `C:\Users\[VotreNom]\AppData\Local\NasroulGestion\`
4. Redémarrer
5. Réinstaller

## Problème : Erreurs de compilation Maven

### Vérifier votre connexion Internet

Maven a besoin de télécharger des dépendances :
```powershell
mvn dependency:resolve
```

### Nettoyer le cache Maven

```powershell
mvn dependency:purge-local-repository
mvn clean install -P windows
```

## Obtenir de l'aide

Si les solutions ci-dessus ne fonctionnent pas :

1. Exécutez le script et sauvegardez la sortie complète :
   ```powershell
   .\build-exe.ps1 > build-log.txt 2>&1
   ```

2. Vérifiez le fichier `jpackage-output.log` s'il existe

3. Collectez les informations système :
   ```powershell
   java -version > system-info.txt
   mvn -version >> system-info.txt
   systeminfo >> system-info.txt
   ```

4. Cherchez dans l'Observateur d'événements Windows pour les erreurs
