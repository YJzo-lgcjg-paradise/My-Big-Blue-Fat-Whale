@echo off
chcp 65001 >nul
cd /d "%~dp0"

if defined JAVA_HOME (
  set "JAVA=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA=java"
)

echo   正在构建后端（首次会慢一点）...
call mvn -o -q -DskipTests package
if errorlevel 1 (
  echo.
  echo   构建失败：请确认已安装 JDK 21 与 Maven，并且依赖已经下载过。
  pause
  exit /b 1
)

echo   后端启动中：http://127.0.0.1:8787
"%JAVA%" -jar target\dayufish-backend-0.1.0.jar
pause
