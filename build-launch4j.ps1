# build-launch4j.ps1 - Genere NasroulGestion.exe avec Launch4j + un runtime Java embarque
#
# Usage :  .\build-launch4j.ps1
#          .\build-launch4j.ps1 -AppVersion 2.0.1
#          .\build-launch4j.ps1 -SkipRuntime      (exe sans runtime : Java requis sur le poste)
#
# Resultat : dist\NasroulGestion.exe + dist\runtime\  -> distribuer les DEUX ensemble
#            (ou zipper le dossier dist).

param(
    [string]$AppVersion = '2.0.0',
    [switch]$SkipRuntime
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root    = Split-Path -Parent $MyInvocation.MyCommand.Path
$destDir = Join-Path $root 'dist'
$runtime = Join-Path $destDir 'runtime'

Write-Output "========================================"
Write-Output "NasroulGestion - build Launch4j $AppVersion"
Write-Output "========================================`n"

# --- Java ---
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Error "ERREUR: java introuvable dans le PATH (installez un JDK 17+)."
    exit 1
}
java --version

# --- Launch4j (launch4jc.exe) ---
$l4j = Get-Command launch4jc -ErrorAction SilentlyContinue
if (-not $l4j) {
    $candidates = @(
        'C:\Program Files (x86)\Launch4j\launch4jc.exe',
        'C:\Program Files\Launch4j\launch4jc.exe'
    ) | Where-Object { Test-Path $_ }
    if ($candidates) {
        $l4jPath = $candidates[0]
    } else {
        Write-Error "ERREUR: launch4jc.exe introuvable. Installez Launch4j ou ajoutez-le au PATH."
        exit 1
    }
} else {
    $l4jPath = $l4j.Path
}
Write-Output "Launch4j: $l4jPath`n"

# --- Maven (wrapper prioritaire) ---
$wrapper = Join-Path $root 'mvnw.cmd'
if (Test-Path $wrapper)                              { $mvnCmd = $wrapper }
elseif (Get-Command mvn -ErrorAction SilentlyContinue) { $mvnCmd = 'mvn' }
else { Write-Error "ERREUR: ni mvnw.cmd ni mvn trouves."; exit 1 }

Write-Output "[1/4] Compilation Maven..."
& $mvnCmd clean package -P windows
if ($LASTEXITCODE -ne 0) { Write-Error "ERREUR: compilation Maven echouee."; exit $LASTEXITCODE }

# --- JAR produit ---
$targetDir = Join-Path $root 'target'
$jar = Get-ChildItem -Path $targetDir -Filter *.jar |
       Where-Object { $_.Name -notmatch '(-sources|-javadoc|^original-)' } |
       Select-Object -First 1
if (-not $jar) { Write-Error "ERREUR: aucun JAR dans $targetDir"; exit 1 }
Write-Output "JAR: $($jar.Name)`n"

# --- dist propre ---
if (Test-Path $destDir) { Remove-Item -Path $destDir -Recurse -Force }
New-Item -ItemType Directory -Path $destDir | Out-Null

# --- Runtime Java embarque (jlink) ---
if ($SkipRuntime) {
    Write-Output "[2/4] Runtime ignore (-SkipRuntime) : Java 17+ devra etre installe sur chaque poste.`n"
} else {
    Write-Output "[2/4] Generation du runtime Java embarque (jlink)..."
    # Le PATH pointe souvent vers le JRE Oracle (javapath) qui ne contient que java.exe :
    # on cherche jlink dans le PATH, puis dans JAVA_HOME.
    $jlinkPath = $null
    $cmd = Get-Command jlink -ErrorAction SilentlyContinue
    if ($cmd) { $jlinkPath = $cmd.Path }
    if (-not $jlinkPath -and $env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME 'bin\jlink.exe'
        if (Test-Path $candidate) { $jlinkPath = $candidate }
    }
    if (-not $jlinkPath) {
        Write-Error "ERREUR: jlink introuvable. Il faut un JDK 17+ complet (pas un JRE) et JAVA_HOME correctement defini."
        exit 1
    }
    Write-Output "jlink: $jlinkPath"
    & $jlinkPath `
      --add-modules java.se,jdk.unsupported,jdk.crypto.ec,jdk.charsets,jdk.localedata `
      --output $runtime `
      --no-header-files --no-man-pages --strip-debug
    if ($LASTEXITCODE -ne 0) { Write-Error "ERREUR: jlink a echoue."; exit $LASTEXITCODE }
    Write-Output "Runtime genere: $runtime`n"
}

# --- Config Launch4j (copie temporaire avec la bonne version / le bon JAR) ---
Write-Output "[3/4] Preparation de la configuration Launch4j..."
$cfgSrc = Join-Path $root 'launch4j.xml'
if (-not (Test-Path $cfgSrc)) { Write-Error "ERREUR: launch4j.xml introuvable."; exit 1 }

[xml]$cfg = Get-Content $cfgSrc -Encoding UTF8
$cfg.launch4jConfig.jar     = "target\$($jar.Name)"
$cfg.launch4jConfig.outfile = "dist\NasroulGestion.exe"
if ($SkipRuntime) {
    # Pas de runtime embarque : on retire le chemin pour forcer la recherche d'un Java installe
    $cfg.launch4jConfig.jre.path = ''
}
$vi = $cfg.launch4jConfig.versionInfo
$vi.fileVersion       = "$AppVersion.0"
$vi.txtFileVersion    = $AppVersion
$vi.productVersion    = "$AppVersion.0"
$vi.txtProductVersion = $AppVersion

$cfgTmp = Join-Path $root 'launch4j.generated.xml'
$cfg.Save($cfgTmp)

# --- Build de l'exe ---
Write-Output "[4/4] Creation de l'executable..."
& $l4jPath $cfgTmp
$rc = $LASTEXITCODE
Remove-Item $cfgTmp -Force -ErrorAction SilentlyContinue
if ($rc -ne 0) { Write-Error "ERREUR: Launch4j a echoue (code $rc)."; exit $rc }

Write-Output "`n========================================"
Write-Output "SUCCES ! Contenu de dist :"
Get-ChildItem -Path $destDir -Force | Select-Object Name, Length
if (-not $SkipRuntime) {
    Write-Output "`nATTENTION: distribuez NasroulGestion.exe AVEC le dossier runtime\ (zippez dist\)."
}
