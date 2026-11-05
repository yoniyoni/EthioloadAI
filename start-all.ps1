# Backwards-compatible entry point for the canonical normal-development launcher.
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'scripts\dev-up.ps1') -FlutterWeb
exit $LASTEXITCODE
