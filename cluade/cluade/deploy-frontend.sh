cd /work/frontend
rm -rf dist

mv mv /home/s1067/dist/ ./


# 测试配置
nginx -t

if [ $? -eq 0 ]; then
    # 重启 Nginx
    systemctl restart nginx
    systemctl enable nginx
    echo ""
    echo "✅ Nginx 配置成功"
else
    echo ""
    echo "❌ Nginx 配置错误"
    exit 1
fi

ENDSSH

echo ""
echo "========================================="
echo "  部署完成！"
echo "========================================="
echo ""
echo "访问地址: http://104.251.122.51:8098"
echo ""
echo "如果无法访问，请检查："
echo "1. 防火墙是否开放 8098 端口"
echo "2. Nginx 状态: systemctl status nginx"
echo "3. 后端服务状态: ps aux | grep gdupload"
