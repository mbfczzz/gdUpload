@echo off
chcp 65001 >nul
echo ================================
echo   Emby API Key 测试工具
echo ================================
echo.

set /p SERVER_URL="请输入 Emby 服务器地址（如 http://192.168.1.100:8096）: "
set /p API_KEY="请输入 API Key: "

echo.
echo 正在测试连接...
echo.

curl -s -H "X-Emby-Token: %API_KEY%" "%SERVER_URL%/emby/System/Info" > temp_response.json

if %errorlevel% equ 0 (
    echo ✅ 连接成功！
    echo.
    echo 服务器信息：
    type temp_response.json
    echo.
    echo.
    echo API Key 有效，可以使用！
) else (
    echo ❌ 连接失败！
    echo.
    echo 请检查：
    echo 1. 服务器地址是否正确
    echo 2. API Key 是否有效
    echo 3. 网络连接是否正常
    echo 4. curl 是否已安装
)

if exist temp_response.json del temp_response.json

echo.
echo ================================
pause
