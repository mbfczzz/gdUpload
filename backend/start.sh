#!/bin/bash

# GD Upload 启动脚本
# 用途：启动后端服务，确保使用 UTF-8 编码

# 设置环境变量
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8

# 设置 JVM 参数
JAVA_OPTS="-Dfile.encoding=UTF-8"
JAVA_OPTS="$JAVA_OPTS -Dsun.jnu.encoding=UTF-8"
JAVA_OPTS="$JAVA_OPTS -Xms512m"
JAVA_OPTS="$JAVA_OPTS -Xmx2g"
JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC"
JAVA_OPTS="$JAVA_OPTS -XX:MaxGCPauseMillis=200"

# 应用参数
APP_OPTS="--spring.profiles.active=prod"

# 应用 JAR 文件路径（请根据实际情况修改）
APP_JAR="gdupload-backend.jar"

# 日志文件
LOG_FILE="app.log"

# 检查 JAR 文件是否存在
if [ ! -f "$APP_JAR" ]; then
    echo "错误: 找不到 JAR 文件: $APP_JAR"
    echo "请确保在正确的目录下运行此脚本，或修改 APP_JAR 变量"
    exit 1
fi

# 检查是否已经在运行
PID=$(pgrep -f "$APP_JAR")
if [ -n "$PID" ]; then
    echo "应用已经在运行，PID: $PID"
    echo "如需重启，请先执行: ./stop.sh"
    exit 1
fi

# 显示配置信息
echo "=========================================="
echo "GD Upload 启动配置"
echo "=========================================="
echo "环境变量:"
echo "  LANG=$LANG"
echo "  LC_ALL=$LC_ALL"
echo ""
echo "JVM 参数:"
echo "  $JAVA_OPTS"
echo ""
echo "应用参数:"
echo "  $APP_OPTS"
echo ""
echo "JAR 文件: $APP_JAR"
echo "日志文件: $LOG_FILE"
echo "=========================================="

# 启动应用
echo "正在启动应用..."
nohup java $JAVA_OPTS -jar "$APP_JAR" $APP_OPTS > "$LOG_FILE" 2>&1 &

# 等待启动
sleep 2

# 检查是否启动成功
PID=$(pgrep -f "$APP_JAR")
if [ -n "$PID" ]; then
    echo "✓ 应用启动成功！"
    echo "  PID: $PID"
    echo "  查看日志: tail -f $LOG_FILE"
    echo "  停止应用: ./stop.sh 或 kill $PID"
    echo ""
    echo "检查编码设置:"
    echo "  grep 'JVM 默认编码' $LOG_FILE"
else
    echo "✗ 应用启动失败！"
    echo "  请查看日志: cat $LOG_FILE"
    exit 1
fi
