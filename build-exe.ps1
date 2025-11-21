# build-exe-pwsh.ps1 - PowerShell script (meilleure gestion des erreurs)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Write-Output "========================================"
Write-Output "Creation de l'executable NasroulGestion"
Write-Output "========================================`n"

# Vérifier Java
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Error "ERREUR: java n'est pas installe ou pas dans le PATH."
    exit 1
}
java --version

# Vérifier jpackage
$jpackage = Get-Command jpackage -ErrorAction SilentlyContinue
if (-not $jpackage) {
    Write-Error "ERREUR: jpackage n'est pas disponible dans le PATH. Installez JDK 17+ et ajoutez-le au PATH."
    exit 1
}
Write-Output "jpackage detecte: $($jpackage.Path)`n"

# Vérifier mvn
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Error "ERREUR: Maven (mvn) introuvable."
    exit 1
}
mvn -v

# Build Maven
Write-Output "[1/3] Compilation Maven..."
& mvn clean package -P windows
if ($LASTEXITCODE -ne 0) {
    Write-Error "ERREUR: La compilation Maven a échoué."
    exit $LASTEXITCODE
}

# Trouver le JAR
$targetDir = Join-Path (Get-Location) 'target'
$jar = Get-ChildItem -Path $targetDir -Filter *.jar | Where-Object { $_.Name -notmatch '(-sources|-javadoc)' } | Select-Object -First 1
if (-not $jar) {
    Write-Error "ERREUR: Aucun JAR trouvé dans $targetDir"
    exit 1
}
Write-Output "JAR utilise: $($jar.FullName)`n"

# Préparer dist
$destDir = Join-Path (Get-Location) 'dist'
if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Path $destDir | Out-Null }

# Appel jpackage (log)
Write-Output "[2/3] Creation de l'executable Windows..."
$log = Join-Path (Get-Location) 'jpackage-output.log'
& jpackage `
  --input $targetDir `
  --name 'NasroulGestion' `
  --main-jar $jar.Name `
  --main-class 'com.nasroul.Launcher' `
  --type exe `
  --icon (Join-Path (Get-Location) 'src\main\resources\images\icon.ico') `
  --dest $destDir `
  --win-shortcut `
  --win-menu `
  --app-version '1.0' `
  --vendor 'Nasroul' `
  --description 'Gestionnaire d''Association Nasroul' `
  --java-options '--enable-native-access=javafx.graphics,ALL-UNNAMED' *>&1 | Tee-Object -FilePath $log

if ($LASTEXITCODE -ne 0) {
    Write-Error "ERREUR: jpackage a échoué (code $LASTEXITCODE)."
    if (Test-Path $log) {
        Write-Output "Voir le log: $log"
        Get-Content $log -Tail 200
    } else {
        Write-Warning "Le fichier de log n'a pas pu être créé: $log"
    }
    exit $LASTEXITCODE
}

Write-Output "`n[3/3] Terminé !"
Write-Output "========================================"
Write-Output "SUCCES !"
Write-Output "Fichiers dans dist:"
Get-ChildItem -Path $destDir -Force
Write-Output "`nDernieres lignes du log jpackage:"
if (Test-Path $log) {
    Get-Content $log -Tail 200
} else {
    Write-Warning "Le fichier de log n'est pas disponible: $log"
    Write-Output "Cela peut être normal si jpackage n'a pas généré de log."
}
