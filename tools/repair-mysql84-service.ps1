# Run from an Administrator PowerShell. Existing data is never reinitialized.
$ErrorActionPreference = 'Stop'
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw '请在以管理员身份运行的PowerShell中执行此脚本'
}
$mysqlRoot = 'D:\mysql8.4\mysql-8.4.11-winx64'
$configFile = Join-Path $mysqlRoot 'my.ini'
$serverFile = Join-Path $mysqlRoot 'bin\mysqld.exe'
if (-not (Test-Path -LiteralPath $configFile) -or -not (Test-Path -LiteralPath $serverFile)) {
    throw 'MySQL8.4配置或程序文件不存在'
}
$service = Get-Service MySQL84 -ErrorAction SilentlyContinue
if ($service -and $service.Status -ne 'Stopped') {
    throw 'MySQL84服务正在运行，请先确认其路径并停止该服务后重试'
}
$binaryPath = '"' + $serverFile + '" --defaults-file="' + $configFile + '" MySQL84'
if ($service) {
    & sc.exe config MySQL84 binPath= $binaryPath
} else {
    & sc.exe create MySQL84 binPath= $binaryPath start= auto DisplayName= MySQL84
}
if ($LASTEXITCODE -ne 0) { throw '服务注册失败' }
& sc.exe qc MySQL84
if (Get-NetTCPConnection -State Listen -LocalPort 3308 -ErrorAction SilentlyContinue) {
    Write-Host '已修正服务。3308端口已有MySQL进程，暂不启动服务，避免重复打开数据目录。进程正常关闭后运行 Start-Service MySQL84。'
} else {
    Start-Service MySQL84
    Get-Service MySQL84
}
