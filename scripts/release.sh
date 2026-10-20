#!/usr/bin/env bash
#
# Pubblica una nuova release di Arcadio su GitHub.
#
#   scripts/release.sh [patch|minor|major|X.Y.Z] ["note di rilascio"]
#
# 1. aggiorna version.properties (nome versione + versionCode)
# 2. compila l'APK release firmato
# 3. crea il commit e il tag vX.Y.Z e li invia su GitHub
# 4. pubblica la release con l'APK allegato
# 5. elimina tutte le release (e i relativi tag) precedenti
#
set -euo pipefail

cd "$(dirname "$0")/.."

BUMP="${1:-patch}"
NOTES="${2:-}"
APK_NAME="Arcadio.apk"

red()   { printf '\033[31m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
step()  { printf '\n\033[36m▶ %s\033[0m\n' "$*"; }

# --- Controlli preliminari ---------------------------------------------------
command -v gh >/dev/null || { red "Serve la GitHub CLI (gh)."; exit 1; }
gh auth status >/dev/null 2>&1 || { red "Esegui prima: gh auth login"; exit 1; }
[ -f keystore.properties ] || {
  red "Manca keystore.properties: senza la chiave di release gli aggiornamenti non si installerebbero."
  exit 1
}
if [ -n "$(git status --porcelain)" ]; then
  red "Ci sono modifiche non salvate in git: fai commit prima di pubblicare."
  git status --short
  exit 1
fi

if [ -z "${JAVA_HOME:-}" ] && [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi

REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner)"

# --- Nuova versione ----------------------------------------------------------
CUR_NAME="$(grep '^VERSION_NAME=' version.properties | cut -d= -f2)"
CUR_CODE="$(grep '^VERSION_CODE=' version.properties | cut -d= -f2)"
IFS=. read -r MA MI PA <<<"$CUR_NAME"

case "$BUMP" in
  patch) NEW_NAME="$MA.$MI.$((PA + 1))" ;;
  minor) NEW_NAME="$MA.$((MI + 1)).0" ;;
  major) NEW_NAME="$((MA + 1)).0.0" ;;
  [0-9]*.[0-9]*.[0-9]*) NEW_NAME="$BUMP" ;;
  *) red "Argomento non valido: $BUMP (usa patch, minor, major o X.Y.Z)"; exit 1 ;;
esac
NEW_CODE=$((CUR_CODE + 1))
TAG="v$NEW_NAME"

if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  red "Il tag $TAG esiste già."
  exit 1
fi

step "Release $TAG (versionCode $NEW_CODE) su $REPO"

cat > version.properties <<EOF
# Versione dell'app: aggiornata automaticamente da scripts/release.sh
VERSION_NAME=$NEW_NAME
VERSION_CODE=$NEW_CODE
EOF

# Se qualcosa va storto prima del push, ripristina la versione precedente
restore() {
  red "Errore: ripristino version.properties"
  git checkout -- version.properties 2>/dev/null || true
}
trap restore ERR

# --- Build -------------------------------------------------------------------
step "Compilazione APK release"
./gradlew :app:assembleRelease --console=plain -q
mkdir -p build/release
cp app/build/outputs/apk/release/app-release.apk "build/release/$APK_NAME"
green "APK: build/release/$APK_NAME ($(du -h "build/release/$APK_NAME" | cut -f1))"

# --- Note di rilascio --------------------------------------------------------
PREV_TAG="$(git describe --tags --abbrev=0 2>/dev/null || true)"
if [ -z "$NOTES" ]; then
  RANGE="${PREV_TAG:+$PREV_TAG..}HEAD"
  NOTES="$(git log "$RANGE" --pretty='- %s' --no-merges | grep -v '^- Release v' || true)"
  [ -n "$NOTES" ] || NOTES="- Miglioramenti e correzioni"
fi
BODY="$NOTES

---
Scarica **$APK_NAME** qui sotto e installalo, oppure aggiorna direttamente dall'app: **Impostazioni → Aggiornamenti**."

# --- Commit, tag e push ------------------------------------------------------
step "Commit e tag $TAG"
git add version.properties
git commit -q -m "Release $TAG"
git tag -a "$TAG" -m "Arcadio $NEW_NAME"
trap - ERR
git push -q origin HEAD
git push -q origin "$TAG"

# --- Pubblicazione -----------------------------------------------------------
step "Pubblicazione della release"
gh release create "$TAG" "build/release/$APK_NAME" \
  --repo "$REPO" \
  --title "Arcadio $NEW_NAME" \
  --notes "$BODY" \
  --latest

# --- Pulizia delle release precedenti ----------------------------------------
step "Eliminazione delle release precedenti"
gh release list --repo "$REPO" --limit 200 --json tagName -q '.[].tagName' | while read -r OLD; do
  [ "$OLD" = "$TAG" ] && continue
  gh release delete "$OLD" --repo "$REPO" --yes --cleanup-tag
  git tag -d "$OLD" >/dev/null 2>&1 || true
  echo "  eliminata $OLD"
done

green "
✔ Pubblicata https://github.com/$REPO/releases/tag/$TAG"
