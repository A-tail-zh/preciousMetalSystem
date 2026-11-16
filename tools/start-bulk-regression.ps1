$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
Set-Location $projectRoot
foreach($line in Get-Content .env){if($line -match '^([A-Z_]+)=(.*)$'){[Environment]::SetEnvironmentVariable($Matches[1],$Matches[2].Trim("'"),'Process')}}
$bulkRoot=Join-Path $projectRoot '.runtime/audit-repair-2026-10-23/bulk'
New-Item -ItemType Directory -Path $bulkRoot -Force | Out-Null
$env:MYSQL_PWD=''
& 'D:/mysql8.4/mysql-8.4.11-winx64/bin/mysql.exe' --no-defaults --no-login-paths --protocol=TCP -h 127.0.0.1 -P 3310 -u root -e 'CREATE DATABASE audit_bulk_20261023 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci'
if($LASTEXITCODE-ne 0){throw 'Refusing to replace an existing test schema'}
# Explicit user authorization applies only to initializing this newly created isolated database.
$env:ADMIN_PASSWORD_HASH=(& node --input-type=module -e "import bcrypt from './frontend/node_modules/bcryptjs/index.js'; console.log(bcrypt.hashSync(process.env.ADMIN_LOGIN_PASSWORD,10));")
$redisSnapshotName='bulk-'+[guid]::NewGuid().ToString()+'.rdb'
Start-Process 'D:/javaenv/Redis-x64-3.0.504/redis-server.exe' -ArgumentList @('--port','6382','--bind','127.0.0.1','--dir',$bulkRoot,'--dbfilename',$redisSnapshotName,'--appendonly','no') -WindowStyle Hidden -RedirectStandardOutput "$bulkRoot/redis.log" -RedirectStandardError "$bulkRoot/redis-error.log"
Copy-Item -LiteralPath 'backend/ruoyi-admin/target/ruoyi-admin.jar' -Destination "$bulkRoot/backend.jar" -Force
$env:MYSQL_URL='jdbc:mysql://127.0.0.1:3310/audit_bulk_20261023?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai';$env:MYSQL_USER='root';$env:MYSQL_PASSWORD='';$env:REDIS_PORT='6382';$env:REDIS_HOST='127.0.0.1';$env:REDIS_PASSWORD=''
Start-Process java -ArgumentList @('-jar',"$bulkRoot/backend.jar",'--spring.profiles.active=druid,metal','--server.port=18080','--spring.datasource.druid.master.password=','--spring.flyway.password=',"--ruoyi.profile=$bulkRoot/uploads") -WorkingDirectory $bulkRoot -WindowStyle Hidden -RedirectStandardOutput "$bulkRoot/backend.log" -RedirectStandardError "$bulkRoot/backend-error.log"
for($i=0;$i-lt 40;$i++){Start-Sleep -Seconds 1;try{$r=Invoke-WebRequest -UseBasicParsing http://127.0.0.1:18080/captchaImage;if($r.StatusCode-eq 200){Write-Output 'Independent bulk backend healthy';exit 0}}catch{}}
throw 'Independent test backend did not start'
