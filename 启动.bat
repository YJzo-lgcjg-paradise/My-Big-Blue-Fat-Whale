@echo off
chcp 65001 >nul
title My Big Blue Fat Whale
echo.
echo   My Big Blue Fat Whale - starting
echo.

start "MBFW - backend" /D "%~dp0backend" cmd /k run.bat
timeout /t 3 >nul

if not exist "%~dp0frontend\node_modules" (
  echo   首次运行，正在安装前端依赖...
  start "MBFW - install" /D "%~dp0frontend" cmd /k npm install
  echo   装完之后，重新双击本文件。
  pause
  exit /b
)

start "MBFW - frontend" /D "%~dp0frontend" cmd /k run.bat
timeout /t 4 >nul
start "" http://localhost:5173

echo   后端 http://127.0.0.1:8787
echo   前端 http://localhost:5173
echo.
