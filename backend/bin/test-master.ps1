$ErrorActionPreference = 'Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    & mvn -o -s ../build-settings.xml -pl ruoyi-metal -am test '-Dmaven.test.skip=false' '-DskipTests=false' '-Dsurefire.failIfNoSpecifiedTests=false'
    if ($LASTEXITCODE -ne 0) { throw "Master tests failed ($LASTEXITCODE)." }
} finally { Pop-Location }
