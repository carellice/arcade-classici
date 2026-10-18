@echo off
rem Doppio clic per pubblicare una nuova release (versione Windows del .command per Mac).
rem Da terminale accetta gli stessi argomenti di scripts\release.ps1:
rem   release.cmd [patch|minor|major|X.Y.Z] ["note di rilascio"]
chcp 65001 >nul
cd /d "%~dp0"
if "%~1"=="" (
  set "BUMP="
  set /p "BUMP=Tipo di release [patch / minor / major / X.Y.Z] (Invio = patch): "
) else (
  set "BUMP=%~1"
)
if not defined BUMP set "BUMP=patch"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\release.ps1" "%BUMP%" "%~2"
echo.
pause
