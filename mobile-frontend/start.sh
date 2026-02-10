#!/bin/bash

echo "=========================================="
echo "GD上传管理系统 - 移动端启动脚本"
echo "=========================================="

cd "$(dirname "$0")"

# 检查 node_modules 是否存在
if [ ! -d "node_modules" ]; then
    echo "首次运行，正在安装依赖..."
    npm install
    if [ $? -ne 0 ]; then
        echo "依赖安装失败！"
        exit 1
    fi
fi

echo ""
echo "启动开发服务器..."
echo "访问地址: http://localhost:5174"
echo "或手机访问: http://你的IP:5174"
echo ""
echo "按 Ctrl+C 停止服务"
echo "=========================================="

npm run dev
