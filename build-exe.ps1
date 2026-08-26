# build-exe.ps1 - Génère l'installateur Windows NasroulGestion
# Usage :  .\build-exe.ps1              (version par défaut)
#          .\build-exe.ps1 -AppVersion 2.0.1
#          .\build-exe.ps1 -Portable      (dossier portable, sans WiX)
param(
    [string]$AppVersion = '2.0.0',
    # -Portable : produit une application portable (dossier) au lieu d'un installateur.
    #             Ne necessite pas WiX, mais pas de raccourcis ni de mise a jour automatique.
    [switch]$Portable
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Write-Output "========================================"
Write-Output "Creation de l'executable NasroulGestion"
Write-Output "Version : $AppVersion"
Write-Output "========================================`n"

# Vérifier Java
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Error "ERREUR: java n'est pas installe ou pas dans le PATH."
    exit 1
}
java --version

# Vérifier jpackage.
# Piege frequent : le PATH pointe vers le JRE Oracle (javapath) qui ne contient
# que java.exe. On cherche donc aussi dans JAVA_HOME puis dans les JDK installes.
$jpackagePath = $null

$cmd = Get-Command jpackage -ErrorAction SilentlyContinue
if ($cmd) { $jpackagePath = $cmd.Path }

if (-not $jpackagePath -and $env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
    if (Test-Path $candidate) { $jpackagePath = $candidate }
}

if (-not $jpackagePath) {
    $searchRoots = @(
        'C:\Program Files\Java',
        'C:\Program Files\Eclipse Adoptium',
        'C:\Program Files\Microsoft',
        'C:\Program Files\Amazon Corretto',
        'C:\Program Files\Zulu'
    ) | Where-Object { Test-Path $_ }

    foreach ($rootDir in $searchRoots) {
        $found = Get-ChildItem -Path $rootDir -Filter 'jpackage.exe' -Recurse -ErrorAction SilentlyContinue |
                 Select-Object -First 1
        if ($found) { $jpackagePath = $found.FullName; break }
    }
}

if (-not $jpackagePath) {
    Write-Error @"
ERREUR: jpackage introuvable.
jpackage fait partie du JDK 17+ (il n'existe pas dans un JRE).
 - Verifiez que vous avez un JDK et non un simple JRE : dir "C:\Program Files\Java"
 - Sinon installez un JDK : https://adoptium.net/ (cochez "Set JAVA_HOME" et "Add to PATH")
 - Puis rouvrez le terminal.
"@
    exit 1
}

# S'assurer que le bin du JDK est prioritaire dans le PATH de ce script
$jdkBin = Split-Path -Parent $jpackagePath
$env:PATH = "$jdkBin;$env:PATH"
Write-Output "jpackage detecte: $jpackagePath`n"

# Vérifier WiX 3.x (requis par jpackage --type exe/msi ; inutile en mode -Portable).
# ATTENTION : WiX 4/5/7 ne conviennent PAS -- ils ont remplace candle.exe/light.exe
# par un unique wix.exe que le jpackage du JDK 21 ne sait pas piloter.
if ($Portable) {
    Write-Output "Mode portable : WiX non requis.`n"
} elseif (-not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
    $wixDirs = @(
        'C:\wix314',
        'C:\Program Files (x86)\WiX Toolset v3.14\bin',
        'C:\Program Files (x86)\WiX Toolset v3.11\bin',
        'C:\Program Files\WiX Toolset v3.14\bin',
        'C:\Program Files\WiX Toolset v3.11\bin'
    ) | Where-Object { Test-Path (Join-Path $_ 'candle.exe') }

    if ($wixDirs) {
        $env:PATH = "$($wixDirs[0]);$env:PATH"
        Write-Output "WiX 3.x detecte: $($wixDirs[0])`n"
    } else {
        Write-Error @"
ERREUR: WiX 3.x introuvable (candle.exe / light.exe).
jpackage --type exe exige WiX 3.x ; les versions 4, 5 et 7 ne conviennent PAS
(elles n'ont plus candle.exe ni light.exe).

Installation rapide :
  powershell -Command "Invoke-WebRequest -Uri https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip -OutFile `$env:TEMP\wix314.zip"
  powershell -Command "Expand-Archive -Path `$env:TEMP\wix314.zip -DestinationPath C:\wix314 -Force"

Puis relancez ce script (il trouvera C:\wix314 automatiquement).

Sans WiX, vous pouvez produire une application portable (dossier, sans installateur) :
  .\build-exe.ps1 -Portable
"@
        exit 1
    }
} else {
    Write-Output "WiX detecte: $((Get-Command candle.exe).Path)`n"
}

# Maven : on privilegie le wrapper mvnw.cmd (aucune installation requise),
# sinon on retombe sur un mvn present dans le PATH.
$wrapper = Join-Path (Get-Location) 'mvnw.cmd'
if (Test-Path $wrapper) {
    $mvnCmd = $wrapper
    Write-Output "Maven wrapper detecte: $wrapper"
} elseif (Get-Command mvn -ErrorAction SilentlyContinue) {
    $mvnCmd = 'mvn'
} else {
    Write-Error "ERREUR: ni mvnw.cmd ni Maven (mvn) introuvables."
    exit 1
}
& $mvnCmd -v

# Nettoyer le dossier dist s'il existe
$destDir = Join-Path (Get-Location) 'dist'
if (Test-Path $destDir) {
    Write-Output "Nettoyage du dossier dist..."
    Remove-Item -Path $destDir -Recurse -Force -ErrorAction SilentlyContinue
}

# Build Maven
Write-Output "[1/3] Compilation Maven..."
& $mvnCmd clean package -P windows
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
if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Path $destDir | Out-Null }

# Appel jpackage (log)
if ($Portable) {
    Write-Output "[2/3] Creation de l'application portable (app-image)..."
} else {
    Write-Output "[2/3] Creation de l'installateur Windows..."
}
$log = Join-Path (Get-Location) 'jpackage-output.log'

$jpArgs = @(
  '--input',       $targetDir
  '--name',        'NasroulGestion'
  '--main-jar',    $jar.Name
  '--main-class',  'com.nasroul.Launcher'
  '--icon',        (Join-Path (Get-Location) 'src\main\resources\images\icon.ico')
  '--dest',        $destDir
  '--app-version', $AppVersion
  '--vendor',      'Nasroul'
  '--description', "Gestionnaire d'Association Nasroul"
  '--java-options','--enable-native-access=javafx.graphics,ALL-UNNAMED'
)

if ($Portable) {
    # app-image : dossier autonome, aucune dependance a WiX
    $jpArgs += @('--type', 'app-image')
} else {
    # exe : installateur Windows. L'upgrade-uuid doit rester identique d'une
    # version a l'autre, sinon Windows installe une 2e application au lieu
    # de mettre a jour l'existante.
    $jpArgs += @(
      '--type',             'exe'
      '--win-shortcut'
      '--win-menu'
      '--win-upgrade-uuid', '8a4f3c7b-1d2e-4f6a-9b8c-3e5d7a1c9f4b'
    )
}

& $jpackagePath @jpArgs *>&1 | Tee-Object -FilePath $log

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
