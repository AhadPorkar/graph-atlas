param([ValidateSet(21,25)][int]$JavaRelease = 25)
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
if ($env:JAVA_HOME) { $env:Path = "$env:JAVA_HOME\bin;$env:Path" }
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) { throw 'Install a complete JDK and configure JAVA_HOME.' }
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { throw 'Install Maven 3.6.3+ and add its bin directory to PATH.' }
& mvn -B -ntp "-Djava.version=$JavaRelease" clean verify
if ($LASTEXITCODE -ne 0) { throw "Maven verification failed: $LASTEXITCODE" }
$Jar = Join-Path $Root 'repository-server/target/graph-repository.jar'
if (-not (Test-Path $Jar)) { throw 'The Spring Boot JAR was not produced.' }
New-Item -ItemType Directory -Force (Join-Path $Root 'dist') | Out-Null
$Target = Join-Path $Root 'dist/graph-repository.jar'
Copy-Item $Jar $Target -Force
$Hash = (Get-FileHash $Target -Algorithm SHA256).Hash.ToLowerInvariant()
"$Hash  graph-repository.jar" | Set-Content -Encoding ascii "$Target.sha256"
Write-Host "Built: $Target"
