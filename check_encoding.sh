#!/bin/bash

# 文件名编码验证脚本
# 用于检查下载的文件名是否正确显示中文

echo "=========================================="
echo "文件名编码验证"
echo "=========================================="
echo ""

# 1. 检查系统环境
echo "1. 系统环境检查"
echo "----------------------------------------"
echo "LANG: $LANG"
echo "LC_ALL: $LC_ALL"
locale | grep -E "LANG|LC_ALL"
echo ""

# 2. 检查 /data/emby 目录
echo "2. 检查 /data/emby 目录"
echo "----------------------------------------"
if [ -d "/data/emby" ]; then
    echo "目录存在"
    ls -la /data/emby/ | head -20
else
    echo "目录不存在"
fi
echo ""

# 3. 测试创建中文文件
echo "3. 测试创建中文文件"
echo "----------------------------------------"
cd /data/emby
TEST_FILE="测试中文文件_$(date +%s).txt"
touch "$TEST_FILE"
if [ -f "$TEST_FILE" ]; then
    echo "✓ 创建成功"
    ls -la "$TEST_FILE"
    echo "文件名显示: $(ls | grep 测试)"
    rm "$TEST_FILE"
else
    echo "✗ 创建失败"
fi
echo ""

# 4. 检查最近下载的文件
echo "4. 最近下载的文件（最新5个）"
echo "----------------------------------------"
ls -lt /data/emby/*.mp4 2>/dev/null | head -5
echo ""

# 5. 检查文件名的十六进制编码
echo "5. 文件名编码检查（十六进制）"
echo "----------------------------------------"
echo "中文'第'的UTF-8编码应该是: e7 ac ac"
echo "中文'集'的UTF-8编码应该是: e9 9b 86"
echo ""
echo "实际文件名编码:"
ls /data/emby/*.mp4 2>/dev/null | head -1 | od -An -tx1 | head -1
echo ""

# 6. 检查应用日志中的编码信息
echo "6. 应用编码配置"
echo "----------------------------------------"
if [ -f "/work/nohup.out" ]; then
    grep "JVM 默认编码" /work/nohup.out | tail -1
    grep "系统默认字符集" /work/nohup.out | tail -1
else
    echo "日志文件不存在"
fi
echo ""

# 7. 检查最近的下载日志
echo "7. 最近的下载日志"
echo "----------------------------------------"
if [ -f "/work/nohup.out" ]; then
    echo "查找最近的文件名日志..."
    grep -A 3 "原始文件名" /work/nohup.out | tail -20
else
    echo "日志文件不存在"
fi
echo ""

echo "=========================================="
echo "验证完成"
echo "=========================================="
echo ""
echo "如何判断："
echo "1. 如果 ls 显示正确的中文 → 文件名正确"
echo "2. 如果 ls 显示 ???? → 文件名乱码"
echo "3. 如果测试文件创建失败 → 系统环境问题"
echo ""
