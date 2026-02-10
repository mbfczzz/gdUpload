#!/bin/bash

echo "=========================================="
echo "检查后端服务权限问题"
echo "=========================================="
echo ""

# 1. 检查后端服务运行的用户
echo "1. 检查后端服务运行的用户"
ps aux | grep gdupload | grep -v grep
echo ""

# 2. 检查目录权限
echo "2. 检查 /backdata/done 目录权限"
ls -ld /backdata/done
echo ""

# 3. 检查子目录权限
echo "3. 检查子目录权限（前5个）"
ls -ld /backdata/done/*/ | head -5
echo ""

# 4. 测试读取权限
echo "4. 测试当前用户读取权限"
if [ -r /backdata/done ]; then
    echo "✓ 当前用户可以读取 /backdata/done"
else
    echo "✗ 当前用户无法读取 /backdata/done"
fi
echo ""

# 5. 测试列出目录
echo "5. 测试列出目录内容"
ls /backdata/done/ > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "✓ 可以列出目录内容"
    echo "子目录数量: $(ls -d /backdata/done/*/ 2>/dev/null | wc -l)"
else
    echo "✗ 无法列出目录内容"
fi
echo ""

# 6. 检查 Java 进程的用户
echo "6. 检查 Java 进程的用户"
ps aux | grep java | grep gdupload | awk '{print "用户: " $1 ", PID: " $2}'
echo ""

# 7. 建议的修复命令
echo "=========================================="
echo "如果是权限问题，执行以下命令修复："
echo "=========================================="
echo ""
echo "# 方案1：给所有用户读取权限"
echo "sudo chmod -R 755 /backdata/done/"
echo ""
echo "# 方案2：修改目录所有者为后端服务用户"
echo "# 先查看后端服务运行的用户（上面第6步的输出）"
echo "# 假设用户是 'user'，执行："
echo "sudo chown -R user:user /backdata/done/"
echo ""
echo "# 方案3：将后端服务用户添加到 root 组"
echo "# 假设用户是 'user'，执行："
echo "sudo usermod -a -G root user"
echo ""
