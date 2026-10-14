[CmdletBinding()]
param(
    [switch]$UseJar
)

$ErrorActionPreference = 'Stop'
$backendDirectory = $PSScriptRoot
$dotenvPath = Join-Path $backendDirectory '.env'
if (-not (Test-Path -LiteralPath $dotenvPath -PathType Leaf)) {
    throw 'Create backend-spring-boot/.env with your database connection settings first.'
}

# Parse values literally: never evaluate commands, expand variables, or print secrets.
$localSettings = @{}
$dotenvLineNumber = 0
foreach ($dotenvLine in [System.IO.File]::ReadAllLines($dotenvPath)) {
    $dotenvLineNumber++
    $trimmedLine = $dotenvLine.Trim()
    if ($trimmedLine.Length -eq 0 -or $trimmedLine.StartsWith('#')) {
        continue
    }
    if ($trimmedLine -notmatch '^(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
        throw "Invalid .env assignment at line $dotenvLineNumber. Use KEY=value."
    }
    $settingName = $Matches[1]
    $settingValue = $Matches[2].Trim()
    if ($settingValue.StartsWith('"') -or $settingValue.StartsWith("'")) {
        $quoteCharacter = $settingValue.Substring(0, 1)
        if ($settingValue.Length -lt 2 -or -not $settingValue.EndsWith($quoteCharacter)) {
            throw "Unclosed .env quote at line $dotenvLineNumber."
        }
        $settingValue = $settingValue.Substring(1, $settingValue.Length - 2)
    }
    $localSettings[$settingName] = $settingValue
}

# ProcessStartInfo inherits the current environment. Only the child gets .env values.
$backendStartInfo = New-Object System.Diagnostics.ProcessStartInfo
$backendStartInfo.UseShellExecute = $false
$backendStartInfo.CreateNoWindow = $true
$backendStartInfo.RedirectStandardOutput = $true
$backendStartInfo.RedirectStandardError = $true
$backendStartInfo.WorkingDirectory = $backendDirectory
foreach ($settingName in $localSettings.Keys) {
    if (($settingName -eq 'JWT_SECRET' -or $settingName -eq 'SPRING_PROFILES_ACTIVE') -and
        [string]::IsNullOrWhiteSpace($localSettings[$settingName])) {
        continue
    }
    $backendStartInfo.EnvironmentVariables[$settingName] = $localSettings[$settingName]
}

foreach ($requiredSetting in @('SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace($backendStartInfo.EnvironmentVariables[$requiredSetting])) {
        throw "Missing $requiredSetting. Save it in backend-spring-boot/.env."
    }
}

if ([string]::IsNullOrWhiteSpace($backendStartInfo.EnvironmentVariables['SPRING_PROFILES_ACTIVE'])) {
    $backendStartInfo.EnvironmentVariables['SPRING_PROFILES_ACTIVE'] = 'dev'
}

if ([string]::IsNullOrWhiteSpace($backendStartInfo.EnvironmentVariables['JWT_SECRET'])) {
    $activeProfiles = @(
        $backendStartInfo.EnvironmentVariables['SPRING_PROFILES_ACTIVE'] -split ',' |
            ForEach-Object { $_.Trim() } |
            Where-Object { $_.Length -gt 0 }
    )
    if ($activeProfiles.Count -ne 1 -or $activeProfiles[0] -ne 'dev') {
        throw 'JWT_SECRET is required unless running only the dev profile. Set it in backend-spring-boot/.env or the process environment.'
    }

    $jwtBytes = New-Object byte[] 64
    $jwtRandomGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $jwtRandomGenerator.GetBytes($jwtBytes)
        $backendStartInfo.EnvironmentVariables['JWT_SECRET'] = [Convert]::ToBase64String($jwtBytes)
    }
    finally {
        $jwtRandomGenerator.Dispose()
        [Array]::Clear($jwtBytes, 0, $jwtBytes.Length)
    }
    Write-Host 'Generated a temporary JWT secret; sign in again after restarting the backend.'
}

if ($UseJar) {
    $backendJar = Join-Path $backendDirectory 'target/ethioloadai-backend-1.0.0.jar'
    if (-not (Test-Path -LiteralPath $backendJar -PathType Leaf)) {
        throw 'The backend JAR is missing. Run without -UseJar to launch the current source.'
    }
    $backendStartInfo.FileName = (Get-Command java.exe -CommandType Application -ErrorAction Stop).Source
    $backendStartInfo.Arguments = '-jar "' + $backendJar + '"'
}
else {
    $mavenWrapperPath = Join-Path $backendDirectory 'mvnw.cmd'
    if (-not (Test-Path -LiteralPath $mavenWrapperPath -PathType Leaf)) {
        throw 'The Maven wrapper mvnw.cmd is missing.'
    }
    $backendStartInfo.FileName = [Environment]::GetEnvironmentVariable('ComSpec')
    $backendStartInfo.Arguments = '/d /s /c ""' + $mavenWrapperPath + '" spring-boot:run"'
}

Write-Host 'Starting the backend with the database settings saved in .env.'
$backendProcess = New-Object System.Diagnostics.Process
$backendProcess.StartInfo = $backendStartInfo
try {
    [void]$backendProcess.Start()
    # Keep both pipes draining concurrently, including when the caller redirects
    # this script's output to a log file. Merge stderr into the output stream so
    # ordinary Maven diagnostics do not become terminating PowerShell errors.
    $stdoutRead = $backendProcess.StandardOutput.ReadLineAsync()
    $stderrRead = $backendProcess.StandardError.ReadLineAsync()
    while ($null -ne $stdoutRead -or $null -ne $stderrRead) {
        if ($null -ne $stdoutRead -and $stdoutRead.IsCompleted) {
            $outputLine = $stdoutRead.GetAwaiter().GetResult()
            if ($null -eq $outputLine) {
                $stdoutRead = $null
            }
            else {
                Write-Output $outputLine
                $stdoutRead = $backendProcess.StandardOutput.ReadLineAsync()
            }
        }
        if ($null -ne $stderrRead -and $stderrRead.IsCompleted) {
            $errorLine = $stderrRead.GetAwaiter().GetResult()
            if ($null -eq $errorLine) {
                $stderrRead = $null
            }
            else {
                Write-Output $errorLine
                $stderrRead = $backendProcess.StandardError.ReadLineAsync()
            }
        }
        $pendingReads = [System.Threading.Tasks.Task[]]@(
            if ($null -ne $stdoutRead) { $stdoutRead }
            if ($null -ne $stderrRead) { $stderrRead }
        )
        if ($pendingReads.Length -gt 0) {
            [void][System.Threading.Tasks.Task]::WaitAny($pendingReads, 100)
        }
    }
    $backendProcess.WaitForExit()
    $backendExitCode = $backendProcess.ExitCode
}
finally {
    $backendProcess.Dispose()
}
exit $backendExitCode
