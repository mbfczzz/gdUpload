@echo off
chcp 65001
set JAVA_OPTS=-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Xms512m -Xmx2048m

cd /d %~dp0

for /f "delims=" %%i in ('dir /b /s target\*.jar') do set JAR_FILE=%%i

if "%JAR_FILE%"=="" (
    echo 错误: 未找到jar文件，请先执行 mvn clean package
    pause
    exit /b 1
)

echo 启动应用: %JAR_FILE%
echo Java选项: %JAVA_OPTS%

java %JAVA_OPTS% -jar "%JAR_FILE%"
pause
