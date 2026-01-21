#!/bin/bash

echo "========================================="
echo "  快速部署修复版本"
echo "========================================="

cd F:/cluade/backend

echo ""
echo "[1/4] 编译后端..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ 编译失败！"
    exit 1
fi

echo "✅ 编译成功"

echo ""
echo "[2/4] 停止旧服务..."
if [ -f /work/app.pid ]; then
    OLD_PID=$(cat /work/app.pid)
    if ps -p $OLD_PID > /dev/null 2>&1; then
        kill $OLD_PID
        echo "✅ 已停止旧服务 (PID: $OLD_PID)"
        sleep 3
    fi
fi

echo ""
echo "[3/4] 部署新版本..."
cp target/*.jar /work/gdupload.jar

echo ""
echo "[4/4] 启动服务..."
cd /work
nohup java -Dfile.encoding=UTF-8 -Xms512m -Xmx2048m -jar gdupload.jar > nohup.out 2>&1 &
echo $! > app.pid

echo "✅ 服务已启动 (PID: $(cat app.pid))"

echo ""
echo "========================================="
echo "  部署完成！"
echo "========================================="
echo ""
echo "查看日志: tail -f /work/nohup.out"
echo ""
echo "等待 10 秒后显示启动日志..."
sleep 10
tail -30 /work/nohup.out
