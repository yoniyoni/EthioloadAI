$ErrorActionPreference = 'Stop'
$mobileDirectory = $PSScriptRoot
$vitePath = Join-Path $mobileDirectory '../freight-link/node_modules/vite/bin/vite.js'
$previewConfig = Join-Path $mobileDirectory 'preview.config.mjs'
if (-not (Test-Path -LiteralPath (Join-Path $mobileDirectory 'build/web/main.dart.js'))) {
    throw 'The Flutter web build is missing. Build it with Flutter before starting this preview.'
}
if (-not (Test-Path -LiteralPath $vitePath)) {
    throw 'Frontend dependencies are missing. Run pnpm install in frontend first.'
}
Write-Host 'Serving the existing Flutter web build at http://127.0.0.1:8765.'
Write-Host 'Rebuild with Flutter to include future mobile source changes.'
& node $vitePath preview --config $previewConfig --configLoader native
exit $LASTEXITCODE
