#!/bin/bash

# 快速检查脚本 - 检查 /backdata/done/ 目录

echo "=========================================="
echo "检查 /backdata/done/ 目录"
echo "=========================================="
echo ""

# 1. 检查目录是否存在
echo "1. 检查目录是否存在"
if [ -d "/backdata/done/" ]; then
    echo "✓ 目录存在"
else
    echo "✗ 目录不存在"
    exit 1
fi
echo ""

# 2. 检查目录权限
echo "2. 检查目录权限"
ls -ld /backdata/done/
echo ""

# 3. 统计子目录数量
echo "3. 统计子目录数量"
dir_count=$(find /backdata/done/ -maxdepth 1 -type d | wc -l)
echo "子目录数量: $((dir_count - 1))"
echo ""

# 4. 列出所有子目录
echo "4. 列出所有子目录"
ls -d /backdata/done/*/
echo ""

# 5. 统计所有视频文件
echo "5. 统计所有视频文件（mp4 和 mkv）"
mp4_count=$(find /backdata/done/ -type f -name "*.mp4" | wc -l)
mkv_count=$(find /backdata/done/ -type f -name "*.mkv" | wc -l)
total_count=$((mp4_count + mkv_count))
echo "mp4 文件: $mp4_count"
echo "mkv 文件: $mkv_count"
echo "总计: $total_count"
echo ""

# 6. 统计每个子目录的视频文件
echo "6. 每个子目录的视频文件数量"
for dir in /backdata/done/*/; do
    if [ -d "$dir" ]; then
        mp4=$(find "$dir" -maxdepth 1 -type f -name "*.mp4" 2>/dev/null | wc -l)
        mkv=$(find "$dir" -maxdepth 1 -type f -name "*.mkv" 2>/dev/null | wc -l)
        total=$((mp4 + mkv))
        dirname=$(basename "$dir")
        echo "  $dirname: $total 个视频文件 (mp4: $mp4, mkv: $mkv)"
    fi
done
echo ""

# 7. 统计所有文件类型
echo "7. 文件类型统计（前10种）"
find /backdata/done/ -type f | sed 's/.*\.//' | sort | uniq -c | sort -rn | head -10
echo ""

# 8. 显示前5个视频文件示例
echo "8. 视频文件示例（前5个）"
find /backdata/done/ -type f \( -name "*.mp4" -o -name "*.mkv" \) | head -5
echo ""

echo "=========================================="
echo "检查完成"
echo "=========================================="
