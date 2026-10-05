$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
if ($env:JAVA_HOME) { $env:Path = "$env:JAVA_HOME\bin;$env:Path" }
$Build = Join-Path $Root '.build/core-checks'
New-Item -ItemType Directory -Force "$Build/classes", 'docs/qa' | Out-Null
# Paths are relative and slash-separated so javac argfile quoting works on Windows.
$Files = Get-ChildItem repository-core/src/main/java -Filter '*.java' -Recurse | ForEach-Object { $_.FullName.Substring($Root.Length + 1).Replace('\','/') }
$Files += 'repository-core/src/test/java/ir/graph/repo/core/SelfTest.java'
$Files += 'repository-core/src/test/java/ir/graph/repo/core/CoreContractTestMain.java'
$Files += 'repository-core/src/test/java/ir/graph/repo/core/LocalizationTestMain.java'
$Files += 'repository-core/src/test/java/ir/graph/repo/core/ReleaseContractTestMain.java'
[System.IO.File]::WriteAllLines("$Build/sources.txt", $Files, [System.Text.UTF8Encoding]::new($false))
& javac --release 21 -encoding UTF-8 -d "$Build/classes" "@$Build/sources.txt"
if ($LASTEXITCODE -ne 0) { throw 'Core compilation failed.' }
Copy-Item 'repository-core/src/test/resources/*' "$Build/classes" -Recurse -Force
& java -cp "$Build/classes" ir.graph.repo.core.SelfTest
if ($LASTEXITCODE -ne 0) { throw 'Core storage tests failed.' }
& java -cp "$Build/classes" ir.graph.repo.core.CoreContractTestMain
if ($LASTEXITCODE -ne 0) { throw 'Core protocol tests failed.' }
& java -cp "$Build/classes" ir.graph.repo.core.LocalizationTestMain
if ($LASTEXITCODE -ne 0) { throw 'Language checks failed.' }
& java -cp "$Build/classes" ir.graph.repo.core.ReleaseContractTestMain
if ($LASTEXITCODE -ne 0) { throw 'Release assurance tests failed.' }
Write-Host 'Core-only checks passed. Spring Boot was NOT compiled or started by this script.'
