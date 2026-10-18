#
# Pubblica una nuova release di Arcade Classici su GitHub (versione Windows di release.sh).
#
#   scripts\release.ps1 [patch|minor|major|X.Y.Z] ["note di rilascio"]
#
# 1. aggiorna version.properties (nome versione + versionCode)
# 2. compila l'APK release firmato
# 3. crea il commit e il tag vX.Y.Z e li invia su GitHub
# 4. pubblica la release con l'APK allegato
# 5. elimina tutte le release (e i relativi tag) precedenti
#
param(
    [string]$Bump = "patch",
    [string]$Notes = ""
)

# Gli errori dei comandi esterni sono gestiti con $LASTEXITCODE (vedi Run): con "Stop"
# PowerShell 5.1 tratterebbe come fatale qualsiasi riga su stderr (es. gli avvisi di Gradle)
$ErrorActionPreference = "Continue"
Set-Location (Split-Path -Parent $PSScriptRoot)

$ApkName = "ArcadeClassici.apk"

function Red($m)   { Write-Host $m -ForegroundColor Red }
function Green($m) { Write-Host $m -ForegroundColor Green }
function Step($m)  { Write-Host ""; Write-Host "> $m" -ForegroundColor Cyan }

# Esegue un comando esterno e si ferma se fallisce
function Run {
    param([Parameter(ValueFromRemainingArguments = $true)] $Cmd)
    $exe = $Cmd[0]
    $rest = @()
    if ($Cmd.Count -gt 1) { $rest = $Cmd[1..($Cmd.Count - 1)] }
    & $exe @rest
    if ($LASTEXITCODE -ne 0) { throw "Comando fallito: $($Cmd -join ' ')" }
}

# Cerca un eseguibile nel PATH o nei percorsi di installazione abituali
function Find-Tool($name, [string[]]$candidates) {
    $c = Get-Command $name -ErrorAction SilentlyContinue
    if ($c) { return $c.Source }
    foreach ($p in $candidates) {
        $hit = Get-ChildItem $p -ErrorAction SilentlyContinue | Sort-Object FullName | Select-Object -Last 1
        if ($hit) { return $hit.FullName }
    }
    return $null
}

# --- Controlli preliminari ---------------------------------------------------
$gh = Find-Tool "gh" @("$env:ProgramFiles\GitHub CLI\gh.exe", "$env:LOCALAPPDATA\Programs\GitHub CLI\gh.exe")
if (-not $gh) { Red "Serve la GitHub CLI (gh)."; exit 1 }
$git = Find-Tool "git" @("$env:ProgramFiles\Git\cmd\git.exe", "$env:LOCALAPPDATA\Programs\Git\cmd\git.exe",
    "$env:LOCALAPPDATA\GitHubDesktop\app-*\resources\app\git\cmd\git.exe")
if (-not $git) { Red "Serve git."; exit 1 }

& $gh auth status *> $null
if ($LASTEXITCODE -ne 0) { Red "Esegui prima: gh auth login"; exit 1 }
# Usa le credenziali di gh anche per git push
& $gh auth setup-git *> $null

if (-not (Test-Path keystore.properties)) {
    Red "Manca keystore.properties: senza la chiave di release gli aggiornamenti non si installerebbero."
    exit 1
}
$dirty = & $git status --porcelain
if ($dirty) {
    Red "Ci sono modifiche non salvate in git: fai commit prima di pubblicare."
    & $git status --short
    exit 1
}

if (-not $env:JAVA_HOME) {
    $jdk = @("$env:ProgramFiles\Android\Android Studio\jbr", "$env:LOCALAPPDATA\Programs\Android Studio\jbr") +
        @(Get-ChildItem "$env:ProgramFiles\Eclipse Adoptium\jdk-*" -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | ForEach-Object FullName) |
        Where-Object { Test-Path "$_\bin\java.exe" } | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk }
}
if (-not $env:ANDROID_HOME -and -not (Test-Path local.properties) -and (Test-Path "$env:LOCALAPPDATA\Android\Sdk")) {
    $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
}

$Repo = (& $gh repo view --json nameWithOwner -q .nameWithOwner).Trim()

# --- Nuova versione ----------------------------------------------------------
$props = Get-Content version.properties
$CurName = (($props | Select-String '^VERSION_NAME=').Line -split '=', 2)[1].Trim()
$CurCode = [int](($props | Select-String '^VERSION_CODE=').Line -split '=', 2)[1].Trim()
$Ma, $Mi, $Pa = $CurName.Split('.') | ForEach-Object { [int]$_ }

switch -Regex ($Bump) {
    '^patch$' { $NewName = "$Ma.$Mi.$($Pa + 1)"; break }
    '^minor$' { $NewName = "$Ma.$($Mi + 1).0"; break }
    '^major$' { $NewName = "$($Ma + 1).0.0"; break }
    '^\d+\.\d+\.\d+$' { $NewName = $Bump; break }
    default { Red "Argomento non valido: $Bump (usa patch, minor, major o X.Y.Z)"; exit 1 }
}
$NewCode = $CurCode + 1
$Tag = "v$NewName"

& $git rev-parse -q --verify "refs/tags/$Tag" *> $null
if ($LASTEXITCODE -eq 0) { Red "Il tag $Tag esiste già."; exit 1 }

Step "Release $Tag (versionCode $NewCode) su $Repo"

$utf8 = New-Object System.Text.UTF8Encoding $false
[IO.File]::WriteAllText("$PWD\version.properties",
    "# Versione dell'app: aggiornata automaticamente da scripts/release.sh`nVERSION_NAME=$NewName`nVERSION_CODE=$NewCode`n", $utf8)

try {
    # --- Build ---------------------------------------------------------------
    Step "Compilazione APK release"
    Run .\gradlew.bat :app:assembleRelease --console=plain -q
    New-Item -ItemType Directory -Force build\release | Out-Null
    Copy-Item app\build\outputs\apk\release\app-release.apk "build\release\$ApkName" -Force -ErrorAction Stop
    $size = "{0:N1} MB" -f ((Get-Item "build\release\$ApkName" -ErrorAction Stop).Length / 1MB)
    Green "APK: build\release\$ApkName ($size)"

    # --- Note di rilascio ----------------------------------------------------
    $PrevTag = (& $git describe --tags --abbrev=0 2>$null)
    if ($LASTEXITCODE -ne 0) { $PrevTag = "" }
    if (-not $Notes) {
        $range = if ($PrevTag) { "$PrevTag..HEAD" } else { "HEAD" }
        $Notes = (& $git log $range --pretty='- %s' --no-merges | Where-Object { $_ -notmatch '^- Release v' }) -join "`n"
        if (-not $Notes) { $Notes = "- Miglioramenti e correzioni" }
    }
    $Body = "$Notes`n`n---`nScarica **$ApkName** qui sotto e installalo, oppure aggiorna direttamente dall'app: **Impostazioni → Aggiornamenti**."

    # --- Commit e tag --------------------------------------------------------
    Step "Commit e tag $Tag"
    Run $git add version.properties
    Run $git commit -q -m "Release $Tag"
    Run $git tag -a $Tag -m "Arcade Classici $NewName"
} catch {
    # Se qualcosa va storto prima del push, ripristina la versione precedente
    Red "Errore: $($_.Exception.Message)"
    Red "Ripristino version.properties"
    & $git checkout -- version.properties 2>$null
    exit 1
}

Run $git push -q origin HEAD
Run $git push -q origin $Tag

# --- Pubblicazione -----------------------------------------------------------
Step "Pubblicazione della release"
$notesFile = [IO.Path]::GetTempFileName()
[IO.File]::WriteAllText($notesFile, $Body, $utf8)
try {
    Run $gh release create $Tag "build\release\$ApkName" --repo $Repo --title "Arcade Classici $NewName" --notes-file $notesFile --latest
} finally {
    Remove-Item $notesFile -ErrorAction SilentlyContinue
}

# --- Pulizia delle release precedenti ----------------------------------------
Step "Eliminazione delle release precedenti"
$old = & $gh release list --repo $Repo --limit 200 --json tagName -q '.[].tagName'
foreach ($o in $old) {
    if (-not $o -or $o -eq $Tag) { continue }
    Run $gh release delete $o --repo $Repo --yes --cleanup-tag
    & $git tag -d $o *> $null
    Write-Host "  eliminata $o"
}

Green ""
Green "✔ Pubblicata https://github.com/$Repo/releases/tag/$Tag"
