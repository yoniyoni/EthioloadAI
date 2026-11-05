[CmdletBinding()]
param(
    [switch]$FlutterWeb
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'dev-workflow.ps1')
Start-DevWorkflow -FlutterWeb:$FlutterWeb
