@echo off
rem Publica o servidor (API + painel) e o APK de release no VPS. Duplo clique no Explorer.
rem Roda o deploy/deploy.sh pelo Git Bash, sem precisar colar comandos no terminal.
cd /d "%~dp0.."
set CHERE_INVOKING=1
if not exist "android\app\build\outputs\apk\release\app-universal-release.apk" (
  echo APK de release nao encontrado. Gere o build antes de publicar.
  pause
  exit /b 1
)
"C:\Program Files\Git\bin\bash.exe" -lc "./deploy/deploy.sh %* --apk android/app/build/outputs/apk/release/app-universal-release.apk"
echo.
if errorlevel 1 (echo *** O deploy FALHOU. Copie a saida acima e mande para o suporte. ***) else (echo Deploy concluido.)
pause
