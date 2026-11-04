param([string]$RedisDirectory = 'D:\javaenv\Redis-x64-3.0.504')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$serverFile = Join-Path $RedisDirectory 'redis-server.exe'
$clientFile = Join-Path $RedisDirectory 'redis-cli.exe'
if (-not (Test-Path -LiteralPath $serverFile)) { throw 'Redis executable was not found.' }
try {
    $reply = & $clientFile -h 127.0.0.1 -p 6380 ping 2>$null
    if ($reply -eq 'PONG') { Write-Host 'Local Redis is already running on 6380.'; exit 0 }
} catch { }
$runtimeDirectory = Join-Path $projectRoot '.runtime\redis'
New-Item -ItemType Directory -Path $runtimeDirectory -Force | Out-Null
$dataPath = $runtimeDirectory.Replace('\','/')
$configFile = Join-Path $runtimeDirectory 'redis.conf'
[IO.File]::WriteAllText($configFile,"bind 127.0.0.1`nport 6380`nappendonly yes`ndir $dataPath`n",[Text.UTF8Encoding]::new($false))
Start-Process -FilePath $serverFile -ArgumentList $configFile -WorkingDirectory $runtimeDirectory -WindowStyle Hidden
Write-Host 'Local Redis startup requested on 6380.'
