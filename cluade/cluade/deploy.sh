#!/bin/bash
# 快速部署脚本
cd /work


mv /home/s1067/gd-upload-manager-1.0.0.jar ./

echo "========================================="
echo "  开始部署 GD Upload Manager"
echo "========================================="

# 2. 停止服务
echo ""
echo "[2/4] 停止旧服务..."
if [ -f /work/app.pid ]; then
    OLD_PID=$(cat /work/app.pid)
    if ps -p $OLD_PID > /dev/null 2>&1; then
        kill $OLD_PID
        echo "✅ 已停止旧服务 (PID: $OLD_PID)"
        sleep 2
    else
        echo "⚠️  旧服务已停止"
    fi
else
    echo "⚠️  未找到 PID 文件"
fi

# 启动服务
echo ""
echo "启动新服务..."
cd /work
nohup java -Dfile.encoding=UTF-8 -Xms512m -Xmx2048m -jar gd-upload-manager-1.0.0.jar > nohup.out 2>&1 &
echo $! > app.pid

echo "✅ 服务已启动 (PID: $(cat app.pid))"

# 等待服务启动
echo ""
echo "等待服务启动..."
sleep 5

# 检查服务状态
if ps -p $(cat app.pid) > /dev/null 2>&1; then
    echo "✅ 服务运行正常"
    echo ""
    echo "========================================="
    echo "  部署完成！"
    echo "========================================="
    echo ""
    echo "查看日志: tail -f /work/nohup.out"
    echo "停止服务: kill \$(cat /work/app.pid)"
else
    echo "❌ 服务启动失败，请查看日志"
    tail -20 /work/nohup.out
    exit 1
fi
