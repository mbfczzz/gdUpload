#!/bin/bash

echo "================================"
echo "  检查 Emby 配置修复"
echo "================================"
echo ""

echo "[1/3] 检查 EmbyProperties 引用..."
cd /f/cluade2/backend
REFS=$(grep -r "EmbyProperties" --include="*.java" src/main/java/com/gdupload/service/ src/main/java/com/gdupload/controller/ 2>/dev/null | grep -v "^Binary" | wc -l)

if [ "$REFS" -eq "0" ]; then
    echo "✅ 没有发现 EmbyProperties 的错误引用"
else
    echo "❌ 发现 $REFS 处 EmbyProperties 引用"
    grep -r "EmbyProperties" --include="*.java" src/main/java/com/gdupload/service/ src/main/java/com/gdupload/controller/ 2>/dev/null
fi

echo ""
echo "[2/3] 检查 EmbyAuthService 引用..."
AUTH_REFS=$(grep -r "embyAuthService" --include="*.java" src/main/java/com/gdupload/service/impl/EmbyServiceImpl.java 2>/dev/null | wc -l)

if [ "$AUTH_REFS" -gt "0" ]; then
    echo "✅ EmbyServiceImpl 正确使用 EmbyAuthService ($AUTH_REFS 处)"
else
    echo "❌ EmbyServiceImpl 没有使用 EmbyAuthService"
fi

echo ""
echo "[3/3] 检查数据库配置服务..."
CONFIG_SERVICE=$(grep -r "IEmbyConfigService" --include="*.java" src/main/java/com/gdupload/service/impl/EmbyAuthService.java 2>/dev/null | wc -l)

if [ "$CONFIG_SERVICE" -gt "0" ]; then
    echo "✅ EmbyAuthService 正确使用 IEmbyConfigService"
else
    echo "❌ EmbyAuthService 没有使用 IEmbyConfigService"
fi

echo ""
echo "================================"
echo "  检查完成"
echo "================================"
