# Backwards-compatible entry point for the canonical normal-development shutdown.
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'scripts\dev-down.ps1')
exit $LASTEXITCODE
