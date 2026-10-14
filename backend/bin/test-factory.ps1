$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path -Parent $PSScriptRoot
Push-Location $backendRoot
try {
    & mvn -o -s ../build-settings.xml -pl ruoyi-metal -am test '-Dmaven.test.skip=false' '-DskipTests=false' '-Dtest=Factory*Test' '-Dsurefire.failIfNoSpecifiedTests=false'
    if ($LASTEXITCODE -ne 0) { throw "Factory tests failed (exit code $LASTEXITCODE)." }
} finally {
    Pop-Location
}
