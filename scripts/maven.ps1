param([Parameter(ValueFromRemainingArguments=$true)][string[]]$MavenArgs)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskConfig = Join-Path $taskRoot '.tools\toolchain.json'
if (!(Test-Path -LiteralPath $taskConfig)) { python (Join-Path $PSScriptRoot 'bootstrap.py'); if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } }
$taskTools = Get-Content -LiteralPath $taskConfig -Raw | ConvertFrom-Json
$env:JAVA_HOME = $taskTools.java
$env:JAVA_TOOL_OPTIONS = "$env:JAVA_TOOL_OPTIONS -Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE"
& (Join-Path $taskTools.maven 'bin\mvn.cmd') @MavenArgs
exit $LASTEXITCODE
