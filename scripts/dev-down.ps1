[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'dev-workflow.ps1')
Stop-DevWorkflow
