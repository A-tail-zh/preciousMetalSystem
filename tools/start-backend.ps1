$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ($line -match '^([A-Z_]+)=(.*)$') {
            $variableName = $Matches[1]
            $variableValue = $Matches[2]
            if ($variableValue.Length -ge 2 -and $variableValue.StartsWith("'") -and $variableValue.EndsWith("'")) {
                $variableValue = $variableValue.Substring(1, $variableValue.Length - 2)
            }
            [Environment]::SetEnvironmentVariable($variableName, $variableValue, 'Process')
        }
    }
}
foreach ($name in @('MYSQL_PASSWORD','TOKEN_SECRET','ADMIN_PASSWORD_HASH')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) { throw "缺少环境变量 $name，请配置项目 .env 文件" }
}
if (-not $env:MYSQL_URL) {
    $databasePort = if ($env:MYSQL_PORT) { $env:MYSQL_PORT } else { '3308' }
    $env:MYSQL_URL = "jdbc:mysql://localhost:$databasePort/heavy_metal?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
}
Set-Location $projectRoot
$runtimeDirectory = Join-Path $projectRoot '.runtime'
New-Item -ItemType Directory -Path $runtimeDirectory -Force | Out-Null
$runtimeJar = Join-Path $runtimeDirectory 'ruoyi-admin.jar'
Copy-Item -LiteralPath (Join-Path $projectRoot 'backend\ruoyi-admin\target\ruoyi-admin.jar') -Destination $runtimeJar -Force
& java -jar $runtimeJar --spring.profiles.active=druid,metal
exit $LASTEXITCODE
