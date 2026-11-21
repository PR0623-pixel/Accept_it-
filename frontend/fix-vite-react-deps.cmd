@echo off
setlocal
REM Run from any working directory. The script expects to be inside project/frontend.
pushd "%~dp0" || exit /b 1

if not exist "package.json" (
  echo [ERROR] frontend/package.json is missing.
  goto :fail
)

where node >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Node.js is not installed or is not on PATH.
  goto :fail
)
where npm >nul 2>&1
if errorlevel 1 (
  echo [ERROR] npm is not installed or is not on PATH.
  goto :fail
)

echo [1/5] Checking Node.js and npm versions...
call node --version
call npm --version
node -e "const x=process.versions.node.split('.').map(Number);if(!((x[0]===20&&x[1]>=19)||(x[0]>=22&&(x[0]>22||x[1]>=12)))){console.error('Use Node.js 20.19+ or 22.12+ (not Node 21).');process.exit(1)}"
if errorlevel 1 goto :fail

echo [2/5] Installing Vite 8 compatible React plugin and updating package-lock.json...
REM Keep Vite at 8.x. npm regenerates the lockfile; no --force / --legacy-peer-deps.
call npm install --save-dev "@vitejs/plugin-react@^6.0.3"
if errorlevel 1 goto :fail

echo [3/5] Verifying a clean install from package-lock.json...
call npm ci
if errorlevel 1 goto :fail

echo [4/5] Building frontend (TypeScript + Vite)...
call npm run build
if errorlevel 1 goto :fail

echo [5/5] Checking installed dependency tree...
call npm ls vite @vitejs/plugin-react --depth=0
if errorlevel 1 goto :fail

echo.
echo [SUCCESS] Dependency installation and frontend build passed.
echo Commit BOTH frontend/package.json and frontend/package-lock.json.
popd
exit /b 0

:fail
echo.
echo [FAILED] Fix or review the error above. Do not commit a partial lockfile.
popd
exit /b 1
