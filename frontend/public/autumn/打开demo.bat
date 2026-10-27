@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo.
echo  落叶与阳光 · HTML Demo
echo  正在启动本地预览服务...
echo.
start "" "http://127.0.0.1:8765/"
python -m http.server 8765
pause
