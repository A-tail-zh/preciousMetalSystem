# 本机 MySQL 8.4 配置

- 安装目录：`D:\mysql8.4\mysql-8.4.11-winx64`
- 原配置及数据目录均保留，my.ini已生成带时间戳的备份。
- MySQL5.7的MySQL57服务继续使用3306；MySQL8.4配置改为3308，字符集utf8mb4。
- 原MySQL84服务错误地指向MySQL5.7的mysqld；现已通过管理员修复脚本更正，并由服务接管运行，自动启动。

在**管理员PowerShell**中运行：

```powershell
powershell -File D:\leo\heavyMetalSystem\tools\repair-mysql84-service.ps1
```

脚本只修正服务注册，不初始化或删除数据。3308已有独立MySQL进程时，不重复启动服务；已有进程通过mysqladmin正常关闭后，再运行`Start-Service MySQL84`。

数据库项目采用独立空库`heavy_metal`，服务端账号优先使用专用项目账号，不让应用以root长期运行。root凭据只用于创建项目库和账号。

已使用用户提供的临时密码将root密码设置为用户指定值，完成heavy_metal数据库和专用应用账号创建，Flyway三项迁移成功。应用账号及随机密码位于项目忽略文件.env，应用不使用root。

当前连接：127.0.0.1:3308，MySQL8.4.11，MySQL84服务Running。原MySQL57继续使用3306。MySQL84服务路径指定此安装目录的mysqld.exe及my.ini。
