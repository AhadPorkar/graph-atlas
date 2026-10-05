param([Parameter(ValueFromRemainingArguments=$true)][string[]]$ApplicationArguments)
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
if ($env:JAVA_HOME) { $env:Path = "$env:JAVA_HOME\bin;$env:Path" }
$Jar = if ($env:GR_JAR) { $env:GR_JAR } else { Join-Path $Root 'dist/graph-repository.jar' }
if (-not (Test-Path $Jar)) { $Jar = Join-Path $Root 'repository-server/target/graph-repository.jar' }
if (-not (Test-Path $Jar)) { throw 'Build first with scripts/build.ps1. No prebuilt Spring Boot JAR is included.' }
& java -jar $Jar @ApplicationArguments
exit $LASTEXITCODE
