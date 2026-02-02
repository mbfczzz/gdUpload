#!/bin/bash

echo "================================"
echo "  Emby 集成系统启动脚本"
echo "================================"
echo ""

echo "[1/3] 检查环境..."
if ! command -v node &> /dev/null; then
    echo "[错误] 未找到 Node.js，请先安装 Node.js"
    exit 1
fi

if ! command -v java &> /dev/null; then
    echo "[错误] 未找到 Java，请先安装 Java 8+"
    exit 1
fi

echo "[✓] 环境检查通过"
echo ""

echo "[2/3] 启动后端服务..."
cd backend
gnome-terminal -- bash -c "mvn spring-boot:run; exec bash" 2>/dev/null || \
xterm -e "mvn spring-boot:run" 2>/dev/null || \
osascript -e 'tell app "Terminal" to do script "cd '$(pwd)' && mvn spring-boot:run"' 2>/dev/null || \
(mvn spring-boot:run &)

echo "[✓] 后端服务启动中..."
echo ""

sleep 5

echo "[3/3] 启动前端服务..."
cd ../frontend
gnome-terminal -- bash -c "npm run dev; exec bash" 2>/dev/null || \
xterm -e "npm run dev" 2>/dev/null || \
osascript -e 'tell app "Terminal" to do script "cd '$(pwd)' && npm run dev"' 2>/dev/null || \
(npm run dev &)

echo "[✓] 前端服务启动中..."
echo ""

echo "================================"
echo "  启动完成！"
echo "================================"
echo ""
echo "后端地址: http://localhost:8099/api"
echo "前端地址: http://localhost:3000"
echo "Emby管理: http://localhost:3000/emby"
echo "Emby测试: http://localhost:3000/emby-test"
echo ""
