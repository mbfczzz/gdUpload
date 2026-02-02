@echo off
echo ================================
echo   Emby 集成系统启动脚本
echo ================================
echo.

echo [1/3] 检查环境...
where node >nul 2>nul
if %errorlevel% neq 0 (
    echo [错误] 未找到 Node.js，请先安装 Node.js
    pause
    exit /b 1
)

where java >nul 2>nul
if %errorlevel% neq 0 (
    echo [错误] 未找到 Java，请先安装 Java 8+
    pause
    exit /b 1
)

echo [✓] 环境检查通过
echo.

echo [2/3] 启动后端服务...
cd backend
start "后端服务" cmd /k "mvn spring-boot:run"
echo [✓] 后端服务启动中...
echo.

timeout /t 5 /nobreak >nul

echo [3/3] 启动前端服务...
cd ..\frontend
start "前端服务" cmd /k "npm run dev"
echo [✓] 前端服务启动中...
echo.

echo ================================
echo   启动完成！
echo ================================
echo.
echo 后端地址: http://localhost:8099/api
echo 前端地址: http://localhost:3000
echo Emby管理: http://localhost:3000/emby
echo Emby测试: http://localhost:3000/emby-test
echo.
echo 按任意键退出...
pause >nul
