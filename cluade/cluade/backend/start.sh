#!/bin/bash

# GD Upload Manager 启动脚本
# 确保使用UTF-8字符编码

# 设置Java环境变量
export JAVA_OPTS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Xms512m -Xmx2048m"

# 设置系统locale为UTF-8
export LC_ALL=en_US.UTF-8
export LANG=en_US.UTF-8

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 查找jar文件
JAR_FILE=$(find target -name "*.jar" -type f | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo "错误: 未找到jar文件，请先执行 mvn clean package"
    exit 1
fi

echo "启动应用: $JAR_FILE"
echo "Java选项: $JAVA_OPTS"

# 启动应用
java $JAVA_OPTS -jar "$JAR_FILE"
