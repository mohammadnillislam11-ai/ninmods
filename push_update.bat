@echo off
chcp 65001 >nul
title NINMODS - Main App Cloud Push
cd /d "%~dp0"

echo ===================================================
echo     NINMODS Main App - Push to GitHub Cloud
echo ===================================================
echo.

git status -s
echo.

set /p msg="Commit message likhun (khali rakhle auto message hobe): "
if "%msg%"=="" (
    set msg=Auto update: %date% %time%
)

echo.
echo [1/3] File gulo stage kora hocche...
git add .

echo [2/3] Commit kora hocche...
git commit -m "%msg%"

echo [3/3] GitHub-e push kora hocche...
git push origin main

if %errorlevel% equ 0 (
    echo.
    echo ===================================================
    echo  [SUCCESS] Shofolbhabe GitHub-e Push hoyeche!
    echo  GitHub Actions Cloud Build shuru hoye geche:
    echo  https://github.com/mohammadnillislam11-ai/ninmods/actions
    echo ===================================================
) else (
    echo.
    echo [ERROR] Push korte shomossha hoyeche. Internet connection ba git status check korun.
)

echo.
pause
