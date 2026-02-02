@echo off
echo 正在编译后端项目...
cd /d F:\cluade2\backend
call mvn clean compile > compile_output.txt 2>&1

echo.
echo 编译完成，查看错误信息：
echo.

findstr /C:"错误" /C:"error" /C:"ERROR" /C:"cannot find symbol" compile_output.txt

echo.
echo 完整日志已保存到: compile_output.txt
pause
