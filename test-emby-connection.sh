#!/bin/bash

echo "================================"
echo "  Emby 连接测试工具"
echo "================================"
echo ""

# 从用户输入或使用默认值
SERVER_URL="${1:-http://209.146.116.4:8096}"
USERNAME="${2:-mbfczzzz}"
PASSWORD="${3:-mbfczzzz@123}"

echo "服务器地址: $SERVER_URL"
echo "用户名: $USERNAME"
echo "密码: ${PASSWORD:0:3}***"
echo ""

echo "[测试 1/3] 测试服务器连接..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$SERVER_URL/emby/System/Info" --connect-timeout 10)

if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "401" ]; then
    echo "✅ 服务器可访问 (HTTP $HTTP_CODE)"
else
    echo "❌ 服务器不可访问 (HTTP $HTTP_CODE)"
    echo ""
    echo "可能的原因："
    echo "1. 服务器地址错误"
    echo "2. 服务器未启动"
    echo "3. 网络连接问题"
    echo "4. 防火墙阻止"
    exit 1
fi

echo ""
echo "[测试 2/3] 测试用户名密码登录..."

LOGIN_RESPONSE=$(curl -s -X POST "$SERVER_URL/emby/Users/AuthenticateByName" \
  -H "Content-Type: application/json" \
  -H "X-Emby-Authorization: MediaBrowser Client=\"Test\", Device=\"CLI\", DeviceId=\"test\", Version=\"1.0\"" \
  -d "{\"Username\":\"$USERNAME\",\"Pw\":\"$PASSWORD\"}" \
  --connect-timeout 10)

if echo "$LOGIN_RESPONSE" | grep -q "AccessToken"; then
    echo "✅ 登录成功"
    ACCESS_TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"AccessToken":"[^"]*"' | cut -d'"' -f4)
    USER_ID=$(echo "$LOGIN_RESPONSE" | grep -o '"Id":"[^"]*"' | head -1 | cut -d'"' -f4)
    echo "   Access Token: ${ACCESS_TOKEN:0:20}..."
    echo "   User ID: $USER_ID"
else
    echo "❌ 登录失败"
    echo ""
    echo "响应内容:"
    echo "$LOGIN_RESPONSE" | head -5
    echo ""
    echo "可能的原因："
    echo "1. 用户名或密码错误"
    echo "2. 用户被禁用"
    echo "3. 服务器配置问题"
    exit 1
fi

echo ""
echo "[测试 3/3] 测试 API 访问..."

if [ -n "$ACCESS_TOKEN" ]; then
    API_RESPONSE=$(curl -s "$SERVER_URL/emby/System/Info" \
      -H "X-Emby-Token: $ACCESS_TOKEN" \
      --connect-timeout 10)

    if echo "$API_RESPONSE" | grep -q "ServerName"; then
        echo "✅ API 访问成功"
        SERVER_NAME=$(echo "$API_RESPONSE" | grep -o '"ServerName":"[^"]*"' | cut -d'"' -f4)
        VERSION=$(echo "$API_RESPONSE" | grep -o '"Version":"[^"]*"' | cut -d'"' -f4)
        echo "   服务器名称: $SERVER_NAME"
        echo "   版本: $VERSION"
    else
        echo "❌ API 访问失败"
        echo "响应内容:"
        echo "$API_RESPONSE" | head -5
    fi
fi

echo ""
echo "================================"
echo "  测试完成"
echo "================================"
echo ""
echo "如果所有测试都通过，说明配置正确。"
echo "如果测试失败，请根据提示检查配置。"
