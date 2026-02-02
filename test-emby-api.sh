#!/bin/bash

# Emby API Key 测试脚本

echo "================================"
echo "  Emby API Key 测试工具"
echo "================================"
echo ""

# 提示输入信息
read -p "请输入 Emby 服务器地址（如 http://192.168.1.100:8096）: " SERVER_URL
read -p "请输入 API Key: " API_KEY

echo ""
echo "正在测试连接..."
echo ""

# 测试 API Key
RESPONSE=$(curl -s -w "\n%{http_code}" -H "X-Emby-Token: $API_KEY" "$SERVER_URL/emby/System/Info")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "200" ]; then
    echo "✅ 连接成功！"
    echo ""
    echo "服务器信息："
    echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
    echo ""
    echo "API Key 有效，可以使用！"
else
    echo "❌ 连接失败！"
    echo ""
    echo "HTTP 状态码: $HTTP_CODE"
    echo "响应内容: $BODY"
    echo ""
    echo "请检查："
    echo "1. 服务器地址是否正确"
    echo "2. API Key 是否有效"
    echo "3. 网络连接是否正常"
fi

echo ""
echo "================================"
