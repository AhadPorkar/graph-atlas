param([Parameter(Mandatory=$true)][string]$Evidence, [Parameter(Mandatory=$true)][string]$TrustedKeySha256)
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
if ($env:JAVA_HOME) { $env:Path = "$env:JAVA_HOME\bin;$env:Path" }
$Build = Join-Path ([System.IO.Path]::GetTempPath()) ('atlas-evidence-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $Build | Out-Null
try {
  $Src = Join-Path $Root 'repository-core/src/main/java/ir/graph/repo/core'
  & javac --release 21 -encoding UTF-8 -d $Build "$Src/util/Json.java" "$Src/release/EvidenceEnvelope.java" "$Src/release/EvidenceVerifierMain.java"
  if ($LASTEXITCODE -ne 0) { throw 'Verifier compilation failed.' }
  & java -cp $Build ir.graph.repo.core.release.EvidenceVerifierMain $Evidence $TrustedKeySha256
  if ($LASTEXITCODE -ne 0) { throw 'Evidence verification failed.' }
} finally { Remove-Item $Build -Recurse -Force }
