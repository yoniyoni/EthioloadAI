$script:DevWorkflowRoot = Split-Path -Parent $PSScriptRoot
$script:DevWorkflowStatePath = Join-Path $env:LOCALAPPDATA 'EthioloadAI\dev-workflow\owned-services.json'
$script:DevDatabaseHost = '127.0.0.1'
$script:DevDatabasePort = 5433
$script:DevDatabaseName = 'ethioloadai_spring'
$script:DevBackendPort = 8080
$script:DevWebPort = 5173
$script:DevFlutterPort = 8765
$script:DevHealthPath = '/api/actuator/health'
$script:DevDatabaseWaitSeconds = 45
. (Join-Path $PSScriptRoot 'dev-process-job.ps1')

function Get-DevWorkflowMutexName {
    param([string]$RepositoryRoot = $script:DevWorkflowRoot)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $digest = $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($RepositoryRoot.ToLowerInvariant()))
        $suffix = ([BitConverter]::ToString($digest, 0, 12)).Replace('-', '')
        return "Global\EthioloadAI-DevWorkflow-$suffix"
    }
    finally {
        $sha.Dispose()
    }
}

function Enter-DevWorkflowLock {
    param([string]$MutexName = (Get-DevWorkflowMutexName))
    $mutex = New-Object System.Threading.Mutex($false, $MutexName)
    try {
        try {
            $acquired = $mutex.WaitOne(0)
        }
        catch [System.Threading.AbandonedMutexException] {
            $acquired = $true
        }
        if (-not $acquired) {
            throw 'Another development launcher operation is already in progress.'
        }
        return $mutex
    }
    catch {
        $mutex.Dispose()
        throw
    }
}

function Exit-DevWorkflowLock {
    param([Parameter(Mandatory = $true)][System.Threading.Mutex]$Mutex)
    try {
        $Mutex.ReleaseMutex()
    }
    finally {
        $Mutex.Dispose()
    }
}

function Test-DevMigrationSafety {
    $frozenMigration = Join-Path $script:DevWorkflowRoot `
        'backend-spring-boot\src\main\resources\db\migration\V78__add_structured_marketplace_freight_details.sql'
    if (Test-Path -LiteralPath $frozenMigration -PathType Leaf) {
        throw 'V78 is frozen. Spring startup is blocked because Flyway could apply it; wait for separate migration authorization.'
    }
}

function Invoke-DevDockerReadOnly {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $result = & docker @Arguments 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Engine is unavailable or the read-only inspection failed.'
    }
    return $result
}

function Test-DevDockerEngine {
    if (-not (Get-Command docker.exe -CommandType Application -ErrorAction SilentlyContinue)) {
        throw 'Docker CLI is not installed or is not available on PATH.'
    }
    $version = Invoke-DevDockerReadOnly -Arguments @('info', '--format', '{{.ServerVersion}}')
    return -not [string]::IsNullOrWhiteSpace(($version -join ''))
}

function Get-DevConfiguration {
    $envPath = Join-Path $script:DevWorkflowRoot 'backend-spring-boot\.env'
    if (-not (Test-Path -LiteralPath $envPath -PathType Leaf)) {
        throw 'Create backend-spring-boot/.env using LOCAL_SETUP.md before starting normal development.'
    }

    $settings = @{}
    $lineNumber = 0
    foreach ($line in [System.IO.File]::ReadAllLines($envPath)) {
        $lineNumber++
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) {
            continue
        }
        if ($trimmed -notmatch '^(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
            throw "Invalid .env assignment at line $lineNumber. Use KEY=value."
        }
        $name = $Matches[1]
        $value = $Matches[2].Trim()
        if ($value.StartsWith('"') -or $value.StartsWith("'")) {
            $quote = $value.Substring(0, 1)
            if ($value.Length -lt 2 -or -not $value.EndsWith($quote)) {
                throw "Unclosed .env quote at line $lineNumber."
            }
            $value = $value.Substring(1, $value.Length - 2)
        }
        $settings[$name] = $value
    }

    $profile = if ($settings.ContainsKey('SPRING_PROFILES_ACTIVE')) {
        $settings['SPRING_PROFILES_ACTIVE']
    } elseif ($env:SPRING_PROFILES_ACTIVE) {
        $env:SPRING_PROFILES_ACTIVE
    } else {
        'dev'
    }
    if ($profile.Trim() -ne 'dev') {
        throw 'Normal development requires SPRING_PROFILES_ACTIVE=dev. E2E and production are separate modes.'
    }

    $url = if ($settings.ContainsKey('SPRING_DATASOURCE_URL')) {
        $settings['SPRING_DATASOURCE_URL']
    } elseif ($env:SPRING_DATASOURCE_URL) {
        $env:SPRING_DATASOURCE_URL
    } else {
        'jdbc:postgresql://127.0.0.1:5433/ethioloadai_spring'
    }
    if ($url -notmatch '^jdbc:postgresql://(?:127\.0\.0\.1|localhost):5433/ethioloadai_spring(?:\?.*)?$') {
        throw 'Normal development must use the local ethioloadai_spring database on port 5433.'
    }

    $user = if ($settings.ContainsKey('SPRING_DATASOURCE_USERNAME')) {
        $settings['SPRING_DATASOURCE_USERNAME']
    } elseif ($env:SPRING_DATASOURCE_USERNAME) {
        $env:SPRING_DATASOURCE_USERNAME
    } else {
        'postgres'
    }
    $password = if ($settings.ContainsKey('SPRING_DATASOURCE_PASSWORD')) {
        $settings['SPRING_DATASOURCE_PASSWORD']
    } else {
        $env:SPRING_DATASOURCE_PASSWORD
    }
    if ([string]::IsNullOrWhiteSpace($user) -or [string]::IsNullOrWhiteSpace($password)) {
        throw 'SPRING_DATASOURCE_USERNAME and SPRING_DATASOURCE_PASSWORD must be configured locally.'
    }

    $serverPort = if ($settings.ContainsKey('SERVER_PORT')) {
        $settings['SERVER_PORT']
    } elseif ($env:SERVER_PORT) {
        $env:SERVER_PORT
    } else {
        '8080'
    }
    if ($serverPort -ne '8080') {
        throw 'Normal development uses backend port 8080 because the web and Flutter proxies target it.'
    }

    return [pscustomobject]@{
        Profile = 'dev'
        DatabaseHost = $script:DevDatabaseHost
        DatabasePort = $script:DevDatabasePort
        DatabaseName = $script:DevDatabaseName
        DatabaseUser = $user
        DatabasePasswordConfigured = $true
        BackendPort = $script:DevBackendPort
        WebPort = $script:DevWebPort
        FlutterPort = $script:DevFlutterPort
    }
}

function Get-DevPostgresService {
    $services = @(Get-CimInstance Win32_Service | Where-Object {
        $_.Name -match '^postgresql-x64-\d+$'
    })
    $serviceMatches = @()
    foreach ($service in $services) {
        if ($service.PathName -notmatch '(?i)\s-D\s+"([^"]+)"') {
            continue
        }
        $dataDirectory = $Matches[1]
        $configuration = Join-Path $dataDirectory 'postgresql.conf'
        if (-not (Test-Path -LiteralPath $configuration -PathType Leaf)) {
            continue
        }
        $portLine = Select-String -LiteralPath $configuration -Pattern '^\s*port\s*=\s*(\d+)\s*(?:#.*)?$' |
            Select-Object -Last 1
        if (-not $portLine -or $portLine.Matches[0].Groups[1].Value -ne "$script:DevDatabasePort") {
            continue
        }
        $binaryDirectory = Split-Path -Parent ([regex]::Match($service.PathName, '^"?([^"]*?pg_ctl\.exe)').Groups[1].Value)
        $serviceMatches += [pscustomobject]@{
            Name = $service.Name
            State = $service.State
            ProcessId = [int]$service.ProcessId
            DataDirectory = $dataDirectory
            ServiceExecutable = Join-Path $binaryDirectory 'pg_ctl.exe'
            PostgresExecutable = Join-Path $binaryDirectory 'postgres.exe'
            ServiceCommandLine = $service.PathName
            ReadinessCommand = Join-Path $binaryDirectory 'pg_isready.exe'
        }
    }
    if ($serviceMatches.Count -ne 1) {
        throw 'Could not identify exactly one existing PostgreSQL Windows service configured for development port 5433.'
    }
    if (-not (Test-Path -LiteralPath $serviceMatches[0].ReadinessCommand -PathType Leaf)) {
        throw 'pg_isready.exe is missing beside the identified PostgreSQL service.'
    }
    return $serviceMatches[0]
}

function Get-DevPortListeners {
    param([Parameter(Mandatory = $true)][int]$Port)
    return @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
}

function Test-DevPostgresListenerOwned {
    param(
        [Parameter(Mandatory = $true)]$Service,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$Listeners
    )
    if ($Listeners.Count -eq 0 -or $Service.ProcessId -le 0) {
        return $false
    }
    $currentService = Get-CimInstance Win32_Service -Filter "Name='$($Service.Name)'" -ErrorAction SilentlyContinue
    if (-not $currentService -or $currentService.State -ne 'Running' -or
        [int]$currentService.ProcessId -ne [int]$Service.ProcessId) {
        return $false
    }
    $processes = @(Get-CimInstance Win32_Process)
    $byId = @{}
    foreach ($process in $processes) {
        $byId[[int]$process.ProcessId] = $process
    }
    if (-not $byId.ContainsKey([int]$Service.ProcessId)) {
        return $false
    }
    $serviceProcess = $byId[[int]$Service.ProcessId]
    if (-not $serviceProcess.ExecutablePath -or -not $serviceProcess.CommandLine -or
        -not [string]::Equals(
            [IO.Path]::GetFullPath([string]$serviceProcess.ExecutablePath),
            [IO.Path]::GetFullPath([string]$Service.ServiceExecutable),
            [StringComparison]::OrdinalIgnoreCase
        ) -or
        $serviceProcess.CommandLine -notmatch '(?i)pg_ctl\.exe"?\s+runservice\b' -or
        $serviceProcess.CommandLine.IndexOf([string]$Service.DataDirectory, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
        return $false
    }
    foreach ($listener in $Listeners) {
        $processId = [int]$listener.OwningProcess
        if (-not $byId.ContainsKey($processId)) {
            return $false
        }
        $listenerProcess = $byId[$processId]
        if ($listenerProcess.Name -ine 'postgres.exe' -or
            -not $listenerProcess.ExecutablePath -or
            -not [string]::Equals(
                [IO.Path]::GetFullPath([string]$listenerProcess.ExecutablePath),
                [IO.Path]::GetFullPath([string]$Service.PostgresExecutable),
                [StringComparison]::OrdinalIgnoreCase
            )) {
            return $false
        }
        $visited = @{}
        while ($processId -gt 0 -and -not $visited.ContainsKey($processId)) {
            if ($processId -eq $Service.ProcessId) {
                break
            }
            $visited[$processId] = $true
            if (-not $byId.ContainsKey($processId)) {
                break
            }
            $processId = [int]$byId[$processId].ParentProcessId
        }
        if ($processId -ne $Service.ProcessId) {
            return $false
        }
    }
    return $true
}

function Test-DevListenerBelongsToWorkspace {
    param(
        [Parameter(Mandatory = $true)][object[]]$Listeners,
        [Parameter(Mandatory = $true)][ValidateSet('backend', 'web', 'flutter')][string]$Kind
    )
    if ($Listeners.Count -eq 0) {
        return $false
    }
    $processes = @(Get-CimInstance Win32_Process)
    $byId = @{}
    foreach ($process in $processes) {
        $byId[[int]$process.ProcessId] = $process
    }
    $expectedPath = switch ($Kind) {
        'backend' { Join-Path $script:DevWorkflowRoot 'backend-spring-boot\start-local.ps1' }
        'web' { Join-Path $script:DevWorkflowRoot 'frontend\artifacts\freight-link' }
        'flutter' { Join-Path $script:DevWorkflowRoot 'frontend\artifacts\mobile-app' }
    }
    foreach ($listener in $Listeners) {
        $processId = [int]$listener.OwningProcess
        $visited = @{}
        $found = $false
        while ($processId -gt 0 -and -not $visited.ContainsKey($processId)) {
            $visited[$processId] = $true
            if (-not $byId.ContainsKey($processId)) {
                break
            }
            $process = $byId[$processId]
            $command = [string]$process.CommandLine
            if ($Kind -eq 'backend' -and
                $command.IndexOf($expectedPath, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
                $found = $true
                break
            }
            if ($Kind -ne 'backend') {
                $role = if ($Kind -eq 'web') { 'web' } else { 'flutter' }
                $matchingProject = $command.IndexOf($expectedPath, [StringComparison]::OrdinalIgnoreCase) -ge 0
                $matchingLauncher = $command.IndexOf(
                    (Join-Path $PSScriptRoot 'dev-vite-host.mjs'),
                    [StringComparison]::OrdinalIgnoreCase
                ) -ge 0 -and $command -match "(?:^|\s)$role(?:\s|$)"
                if ($matchingProject -or $matchingLauncher) {
                    $found = $true
                    break
                }
            }
            $processId = [int]$process.ParentProcessId
        }
        if (-not $found) {
            return $false
        }
    }
    return $true
}

function Test-DevDatabaseReady {
    param(
        [Parameter(Mandatory = $true)]$Service,
        [Parameter(Mandatory = $true)]$Configuration
    )
    & $Service.ReadinessCommand -h $Configuration.DatabaseHost -p $Configuration.DatabasePort `
        -d $Configuration.DatabaseName -U $Configuration.DatabaseUser *> $null
    return $LASTEXITCODE -eq 0
}

function Start-DevPostgresService {
    param([Parameter(Mandatory = $true)][string]$Name)
    Start-Service -Name $Name -ErrorAction Stop
}

function Get-DevDockerContainersOnPort {
    param([Parameter(Mandatory = $true)][int]$Port)
    $names = Invoke-DevDockerReadOnly -Arguments @(
        'ps', '--filter', "publish=$Port", '--format', '{{.Names}}'
    )
    return @($names | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Test-DevBackendHealth {
    try {
        $result = Invoke-RestMethod -Uri "http://127.0.0.1:$script:DevBackendPort$script:DevHealthPath" `
            -TimeoutSec 2 -ErrorAction Stop
        return $result.status -eq 'UP'
    }
    catch {
        return $false
    }
}

function Test-DevFrontendHealth {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('web', 'flutter')][string]$Kind,
        [Parameter(Mandatory = $true)][int]$Port
    )
    try {
        $response = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/" -TimeoutSec 2 -UseBasicParsing -ErrorAction Stop
        if ($response.StatusCode -ne 200) {
            return $false
        }
        if ($Kind -eq 'web') {
            return $response.Content.Contains('/@vite/client')
        }
        return $response.Content.Contains('main.dart.js')
    }
    catch {
        return $false
    }
}

function Wait-DevEndpoint {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('backend', 'web', 'flutter')][string]$Kind,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )
    $stopAt = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $healthy = switch ($Kind) {
            'backend' { Test-DevBackendHealth }
            'web' { Test-DevFrontendHealth -Kind web -Port $script:DevWebPort }
            'flutter' { Test-DevFrontendHealth -Kind flutter -Port $script:DevFlutterPort }
        }
        if ($healthy) {
            return $true
        }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $stopAt)
    return $false
}

function Start-DevManagedProcess {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('backend', 'web', 'flutter')][string]$Kind,
        [Parameter(Mandatory = $true)][string]$PipeName
    )
    $stateDirectory = Split-Path -Parent $script:DevWorkflowStatePath
    $logDirectory = Join-Path $stateDirectory 'logs'
    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    $stdout = Join-Path $logDirectory "$Kind.stdout.log"
    $stderr = Join-Path $logDirectory "$Kind.stderr.log"
    if ($Kind -eq 'backend') {
        $executable = (Get-Command powershell.exe -CommandType Application -ErrorAction Stop).Source
        $entry = Join-Path $script:DevWorkflowRoot 'backend-spring-boot\start-local.ps1'
        $arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$entry`" -ShutdownEventName `"$PipeName`""
        $workingDirectory = Join-Path $script:DevWorkflowRoot 'backend-spring-boot'
    }
    else {
        $executable = (Get-Command node.exe -CommandType Application -ErrorAction Stop).Source
        $hostScript = Join-Path $PSScriptRoot 'dev-vite-host.mjs'
        $role = if ($Kind -eq 'web') { 'web' } else { 'flutter' }
        $arguments = "`"$hostScript`" $role $PipeName"
        $workingDirectory = $script:DevWorkflowRoot
    }

    $process = Start-Process -FilePath $executable -ArgumentList $arguments `
        -WorkingDirectory $workingDirectory -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    if (-not $process) {
        throw "Could not start the $Kind process."
    }
    $startedAt = $process.StartTime.ToUniversalTime().ToString('o')
    $id = $process.Id
    $process.Dispose()
    return [pscustomobject]@{
        Kind = $Kind
        ProcessId = $id
        StartedAtUtc = $startedAt
        PipeName = if ($Kind -eq 'backend') { $null } else { $PipeName }
        ShutdownEventName = if ($Kind -eq 'backend') { $PipeName } else { $null }
        Owned = $true
    }
}

function Read-DevState {
    if (-not (Test-Path -LiteralPath $script:DevWorkflowStatePath -PathType Leaf)) {
        return $null
    }
    try {
        $state = Get-Content -LiteralPath $script:DevWorkflowStatePath -Raw | ConvertFrom-Json
    }
    catch {
        throw 'The development launcher state file is invalid. Inspect it before changing or removing it.'
    }
    if ($state.SchemaVersion -ne 2 -or $state.RepositoryRoot -ne $script:DevWorkflowRoot) {
        throw 'The development launcher state belongs to another or incompatible workspace.'
    }
    return $state
}

function Write-DevState {
    param([Parameter(Mandatory = $true)]$State)
    $directory = Split-Path -Parent $script:DevWorkflowStatePath
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $temporary = "$script:DevWorkflowStatePath.$([guid]::NewGuid().ToString('N')).tmp"
    $State | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $temporary -Encoding UTF8
    Move-Item -LiteralPath $temporary -Destination $script:DevWorkflowStatePath -Force
}

function Remove-DevState {
    if (Test-Path -LiteralPath $script:DevWorkflowStatePath -PathType Leaf) {
        Remove-Item -LiteralPath $script:DevWorkflowStatePath -Force
    }
}

function Get-DevProcessTree {
    param([Parameter(Mandatory = $true)]$Record)
    $root = Get-Process -Id ([int]$Record.ProcessId) -ErrorAction SilentlyContinue
    if (-not $root) {
        return @()
    }
    $rootStart = $root.StartTime.ToUniversalTime()
    if ([Math]::Abs(($rootStart - [DateTime]::Parse($Record.StartedAtUtc).ToUniversalTime()).TotalSeconds) -gt 1) {
        throw "Owned $($Record.Kind) process identity changed; refusing to stop a reused PID."
    }
    $rootInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $($Record.ProcessId)" -ErrorAction SilentlyContinue
    if (-not $rootInfo -or -not (Test-DevOwnedProcessCommand -Record $Record -Process $rootInfo)) {
        throw "Owned $($Record.Kind) command identity could not be verified; refusing to stop it."
    }
    $processes = @(Get-CimInstance Win32_Process)
    $parents = @{}
    foreach ($process in $processes) {
        $parentId = [int]$process.ParentProcessId
        if (-not $parents.ContainsKey($parentId)) {
            $parents[$parentId] = @()
        }
        $parents[$parentId] += $process
    }
    $result = @()
    $queue = New-Object 'System.Collections.Generic.Queue[int]'
    $queue.Enqueue([int]$Record.ProcessId)
    while ($queue.Count -gt 0) {
        $parentId = $queue.Dequeue()
        foreach ($child in @($parents[$parentId])) {
            if (-not $child.CreationDate) {
                continue
            }
            $childCreated = [Management.ManagementDateTimeConverter]::ToDateTime($child.CreationDate).ToUniversalTime()
            if ($childCreated -lt $rootStart.AddSeconds(-1)) {
                continue
            }
            $result += [pscustomobject]@{
                ProcessId = [int]$child.ProcessId
                Name = $child.Name
                ParentProcessId = [int]$child.ParentProcessId
                StartedAtUtc = $childCreated.ToString('o')
                ExecutablePath = [string]$child.ExecutablePath
                CommandLine = [string]$child.CommandLine
            }
            $queue.Enqueue([int]$child.ProcessId)
        }
    }
    [Array]::Reverse($result)
    return $result
}

function Test-DevOwnedProcessCommand {
    param($Record, $Process)
    if ($Record.Kind -eq 'backend') {
        return $Process.Name -ieq 'powershell.exe' -and
            $Process.CommandLine -and
            $Process.CommandLine.IndexOf(
                (Join-Path $script:DevWorkflowRoot 'backend-spring-boot\start-local.ps1'),
                [StringComparison]::OrdinalIgnoreCase
            ) -ge 0 -and
            $Process.CommandLine.Contains([string]$Record.ShutdownEventName)
    }
    return $Process.Name -ieq 'node.exe' -and
        $Process.CommandLine -and
        $Process.CommandLine.IndexOf(
            (Join-Path $PSScriptRoot 'dev-vite-host.mjs'),
            [StringComparison]::OrdinalIgnoreCase
        ) -ge 0 -and
        $Process.CommandLine.Contains([string]$Record.PipeName)
}

function Get-DevBackendJvmRecord {
    param([Parameter(Mandatory = $true)]$LauncherRecord)
    $tree = @(Get-DevProcessTree -Record $LauncherRecord)
    $backendDirectory = Join-Path $script:DevWorkflowRoot 'backend-spring-boot'
    $jvms = @($tree | Where-Object {
        $_.Name -ieq 'java.exe' -and
        $_.ExecutablePath -match '(?i)\\java\.exe$' -and
        $_.CommandLine -and
        $_.CommandLine -match 'org\.codehaus\.plexus\.classworlds\.launcher\.Launcher' -and
        $_.CommandLine.IndexOf($backendDirectory, [StringComparison]::OrdinalIgnoreCase) -ge 0
    })
    if ($jvms.Count -ne 1) {
        throw 'Could not identify exactly one Maven Java process for the normal-development backend.'
    }
    return [pscustomobject]@{
        JavaProcessId = [int]$jvms[0].ProcessId
        JavaStartedAtUtc = $jvms[0].StartedAtUtc
        JavaExecutablePath = $jvms[0].ExecutablePath
        JavaCommandLine = $jvms[0].CommandLine
        DescendantProcesses = $tree
    }
}

function Get-DevRecordedJvmProcess {
    param([Parameter(Mandatory = $true)]$Record)
    if (-not $Record.JavaProcessId -or -not $Record.JavaStartedAtUtc -or
        -not $Record.JavaExecutablePath -or -not $Record.JavaCommandLine) {
        return $null
    }
    $process = Get-Process -Id ([int]$Record.JavaProcessId) -ErrorAction SilentlyContinue
    if (-not $process) {
        return $null
    }
    $startedAt = $process.StartTime.ToUniversalTime()
    if ([Math]::Abs(($startedAt - [DateTime]::Parse($Record.JavaStartedAtUtc).ToUniversalTime()).TotalSeconds) -gt 1) {
        return $null
    }
    $details = Get-CimInstance Win32_Process -Filter "ProcessId = $($Record.JavaProcessId)" -ErrorAction SilentlyContinue
    if (-not $details -or $details.Name -ine 'java.exe' -or
        -not [string]::Equals(
            [IO.Path]::GetFullPath([string]$details.ExecutablePath),
            [IO.Path]::GetFullPath([string]$Record.JavaExecutablePath),
            [StringComparison]::OrdinalIgnoreCase
        ) -or $details.CommandLine -cne [string]$Record.JavaCommandLine) {
        return $null
    }
    return $process
}

function Test-DevOwnedRecordIdentity {
    param(
        [Parameter(Mandatory = $true)][AllowNull()]$Record,
        [Parameter(Mandatory = $true)][ValidateSet('backend', 'web', 'flutter')][string]$Kind
    )
    if (-not $Record -or -not $Record.Owned -or $Record.Kind -ne $Kind) {
        return $false
    }
    $process = Get-Process -Id ([int]$Record.ProcessId) -ErrorAction SilentlyContinue
    if (-not $process -or [Math]::Abs(
        ($process.StartTime.ToUniversalTime() - [DateTime]::Parse($Record.StartedAtUtc).ToUniversalTime()).TotalSeconds
    ) -gt 1) {
        return $false
    }
    $details = Get-CimInstance Win32_Process -Filter "ProcessId = $($Record.ProcessId)" -ErrorAction SilentlyContinue
    if (-not $details -or -not (Test-DevOwnedProcessCommand -Record $Record -Process $details)) {
        return $false
    }
    if ($Kind -eq 'backend') {
        $java = Get-DevBackendJvmRecord -LauncherRecord $Record
        return $java.JavaProcessId -eq $Record.JavaProcessId -and
            $java.JavaStartedAtUtc -eq $Record.JavaStartedAtUtc -and
            $java.JavaCommandLine -ceq $Record.JavaCommandLine
    }
    return $true
}

function Stop-DevBackendProcessJob {
    param([Parameter(Mandatory = $true)]$Record)
    $launcher = Get-Process -Id ([int]$Record.ProcessId) -ErrorAction SilentlyContinue
    if ($launcher) {
        if ([Math]::Abs(
            ($launcher.StartTime.ToUniversalTime() - [DateTime]::Parse($Record.StartedAtUtc).ToUniversalTime()).TotalSeconds
        ) -gt 1) {
            throw 'The backend launcher PID was reused; refusing to signal it.'
        }
        $details = Get-CimInstance Win32_Process -Filter "ProcessId = $($Record.ProcessId)" -ErrorAction SilentlyContinue
        if (-not $details -or -not (Test-DevOwnedProcessCommand -Record $Record -Process $details)) {
            throw 'The backend launcher command identity changed; refusing to signal it.'
        }
        Send-DevBackendStop -EventName ([string]$Record.ShutdownEventName)
    }

    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    do {
        Start-Sleep -Milliseconds 250
        $launcher = Get-Process -Id ([int]$Record.ProcessId) -ErrorAction SilentlyContinue
    } while ($launcher -and [DateTime]::UtcNow -lt $deadline)
    if ($launcher) {
        throw 'The backend launcher did not exit after its owned process job was terminated.'
    }
    $jvm = Get-DevRecordedJvmProcess -Record $Record
    if ($jvm) {
        $jvm.Kill()
        try {
            Wait-Process -Id ([int]$Record.JavaProcessId) -Timeout 10 -ErrorAction Stop
        }
        catch {
            if (Get-DevRecordedJvmProcess -Record $Record) {
                throw 'The recorded backend Java process did not exit after job shutdown.'
            }
        }
    }
    if (Get-DevRecordedJvmProcess -Record $Record) {
        throw 'The Spring Boot Java process is still running; PostgreSQL will be left running.'
    }
}

function Send-DevBackendStop {
    param([Parameter(Mandatory = $true)][string]$EventName)
    $deadline = [DateTime]::UtcNow.AddSeconds(10)
    do {
        try {
            $shutdownEvent = [System.Threading.EventWaitHandle]::OpenExisting($EventName)
            break
        }
        catch [System.Threading.WaitHandleCannotBeOpenedException] {
            Start-Sleep -Milliseconds 100
        }
    } while ([DateTime]::UtcNow -lt $deadline)
    if (-not $shutdownEvent) {
        throw 'The backend shutdown control is unavailable; refusing to stop or orphan its JVM.'
    }
    try {
        [void]$shutdownEvent.Set()
    }
    finally {
        $shutdownEvent.Dispose()
    }
}
function Stop-DevFrontendGracefully {
    param([Parameter(Mandatory = $true)]$Record)
    $root = Get-Process -Id ([int]$Record.ProcessId) -ErrorAction SilentlyContinue
    if (-not $root) {
        return
    }
    if ([Math]::Abs(($root.StartTime.ToUniversalTime() - [DateTime]::Parse($Record.StartedAtUtc).ToUniversalTime()).TotalSeconds) -gt 1) {
        throw "Owned $($Record.Kind) process identity changed; refusing to stop a reused PID."
    }
    $rootInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $($Record.ProcessId)" -ErrorAction SilentlyContinue
    if (-not $rootInfo -or -not (Test-DevOwnedProcessCommand -Record $Record -Process $rootInfo)) {
        throw "Owned $($Record.Kind) command identity could not be verified; refusing to stop it."
    }
    Send-DevFrontendStop -PipeName ([string]$Record.PipeName)
    try {
        Wait-Process -Id ([int]$Record.ProcessId) -Timeout 20 -ErrorAction Stop
    }
    catch {
        if (Get-Process -Id ([int]$Record.ProcessId) -ErrorAction SilentlyContinue) {
            throw "The $($Record.Kind) server did not stop gracefully; its process was left running."
        }
    }
}

function Send-DevFrontendStop {
    param([Parameter(Mandatory = $true)][string]$PipeName)
    $client = [System.IO.Pipes.NamedPipeClientStream]::new(
        '.', $PipeName, [System.IO.Pipes.PipeDirection]::InOut,
        [System.IO.Pipes.PipeOptions]::None
    )
    try {
        $client.Connect(3000)
        $writer = [System.IO.StreamWriter]::new(
            $client, [System.Text.Encoding]::UTF8, 1024, $true
        )
        $writer.AutoFlush = $true
        $writer.WriteLine('STOP')
        $writer.Dispose()
        $reader = [System.IO.StreamReader]::new(
            $client, [System.Text.Encoding]::UTF8, $true, 1024, $true
        )
        $response = $reader.ReadLine()
        $reader.Dispose()
        if ($response -ne 'STOPPING') {
            throw 'The owned Vite process did not acknowledge graceful shutdown.'
        }
    }
    finally {
        $client.Dispose()
    }
}

function Stop-DevRecord {
    param([Parameter(Mandatory = $true)][AllowNull()]$Record)
    if (-not $Record -or -not $Record.Owned) {
        return
    }
    if ($Record.Kind -eq 'backend') {
        Stop-DevBackendProcessJob -Record $Record
    }
    else {
        Stop-DevFrontendGracefully -Record $Record
    }
}

function Stop-DevWorkflowState {
    param([Parameter(Mandatory = $true)]$State)
    $failures = New-Object 'System.Collections.Generic.List[string]'
    foreach ($kind in @('flutter', 'web', 'backend')) {
        try {
            Stop-DevRecord -Record $State.$kind
        }
        catch {
            $failures.Add("$kind shutdown failed: $($_.Exception.Message)")
        }
    }
    if ($failures.Count -gt 0) {
        throw "Development shutdown was incomplete. Launcher state is preserved for retry. $($failures -join ' ')"
    }
    Remove-DevState
}

function Invoke-DevWorkflowStart {
    [CmdletBinding()]
    param([switch]$FlutterWeb)

    Test-DevMigrationSafety
    if (-not (Test-DevDockerEngine)) {
        throw 'Docker Engine is unavailable. Start Docker Desktop, then rerun dev-up.'
    }
    $configuration = Get-DevConfiguration
    $database = Get-DevPostgresService
    $state = Read-DevState
    if ($state -and $state.StartupInProgress) {
        throw 'A previous development startup did not complete. Run dev-down and inspect any reported cleanup failure.'
    }

    $databaseListeners = @(Get-DevPortListeners -Port $script:DevDatabasePort)
    if ($databaseListeners.Count -gt 0 -and
        -not (Test-DevPostgresListenerOwned -Service $database -Listeners $databaseListeners)) {
        throw 'Port 5433 is occupied by a PostgreSQL listener not owned by the configured development service.'
    }

    $dockerBackends = @(Get-DevDockerContainersOnPort -Port $script:DevBackendPort)
    if ($dockerBackends.Count -gt 0) {
        throw "Backend port 8080 is owned by Docker container(s): $($dockerBackends -join ', '). Normal development will not start beside an E2E/demo backend."
    }

    $backendListeners = @(Get-DevPortListeners -Port $script:DevBackendPort)
    $webListeners = @(Get-DevPortListeners -Port $script:DevWebPort)
    $flutterListeners = if ($FlutterWeb) { @(Get-DevPortListeners -Port $script:DevFlutterPort) } else { @() }
    if ($backendListeners.Count -gt 0) {
        if (-not (Test-DevBackendHealth)) {
            throw 'Port 8080 is occupied by a process that is not a healthy Spring Boot backend; no process was started or stopped.'
        }
        if (-not (Test-DevListenerBelongsToWorkspace -Listeners $backendListeners -Kind backend)) {
            throw 'Port 8080 serves a healthy but unverified backend process; normal development will not reuse or stop it.'
        }
    }
    if ($webListeners.Count -gt 0) {
        if (-not (Test-DevFrontendHealth -Kind web -Port $script:DevWebPort)) {
            throw 'Port 5173 is occupied by a process that is not the expected React/Vite app; no process was started or stopped.'
        }
        if (-not (Test-DevListenerBelongsToWorkspace -Listeners $webListeners -Kind web)) {
            throw 'Port 5173 serves a healthy but unverified frontend process; normal development will not reuse or stop it.'
        }
    }
    if ($flutterListeners.Count -gt 0) {
        if (-not (Test-DevFrontendHealth -Kind flutter -Port $script:DevFlutterPort)) {
            throw 'Port 8765 is occupied by a process that is not the expected Flutter Web preview; no process was started or stopped.'
        }
        if (-not (Test-DevListenerBelongsToWorkspace -Listeners $flutterListeners -Kind flutter)) {
            throw 'Port 8765 serves a healthy but unverified Flutter process; normal development will not reuse or stop it.'
        }
    }
    if ($FlutterWeb -and -not (Test-Path -LiteralPath (Join-Path $script:DevWorkflowRoot 'frontend\artifacts\mobile-app\build\web\main.dart.js') -PathType Leaf)) {
        throw 'Flutter Web build is missing. Build it separately before requesting the optional preview.'
    }

    if (-not $state) {
        $state = [pscustomobject]@{
            SchemaVersion = 2
            RepositoryRoot = $script:DevWorkflowRoot
            StartupInProgress = $true
            Database = [pscustomobject]@{ Name = $database.Name; StartedByLauncher = $false }
            backend = $null
            web = $null
            flutter = $null
        }
        Write-DevState -State $state
    }
    try {
        if ($database.State -ne 'Running') {
            Start-DevPostgresService -Name $database.Name
            $state.Database.StartedByLauncher = $true
            Write-DevState -State $state
            $database = Get-DevPostgresService
        }
        $ready = $false
        $databaseDeadline = [DateTime]::UtcNow.AddSeconds($script:DevDatabaseWaitSeconds)
        do {
            $database = Get-DevPostgresService
            if ($database.State -ne 'Running' -or $database.ProcessId -le 0) {
                Start-Sleep -Milliseconds 500
                continue
            }
            if (Test-DevDatabaseReady -Service $database -Configuration $configuration) {
                $databaseListeners = @(Get-DevPortListeners -Port $script:DevDatabasePort)
                if (-not (Test-DevPostgresListenerOwned -Service $database -Listeners $databaseListeners)) {
                    throw 'pg_isready responded, but port 5433 is not owned by the configured development PostgreSQL service.'
                }
                $ready = $true
                break
            }
            Start-Sleep -Milliseconds 500
        } while ([DateTime]::UtcNow -lt $databaseDeadline)
        if (-not $ready) {
            throw 'PostgreSQL did not become ready on 127.0.0.1:5433 for ethioloadai_spring.'
        }

        if ($backendListeners.Count -eq 0) {
            $eventName = 'Local\EthioloadAI-DevBackend-' + [guid]::NewGuid().ToString('N')
            $record = Start-DevManagedProcess -Kind backend -PipeName $eventName
            $state.backend = $record
            Write-DevState -State $state
            if (-not (Wait-DevEndpoint -Kind backend -TimeoutSeconds 180)) {
                throw 'Spring Boot did not become healthy. See the launcher log under %LOCALAPPDATA%\\EthioloadAI\\dev-workflow\\logs.'
            }
            $javaRecord = Get-DevBackendJvmRecord -LauncherRecord $record
            foreach ($property in $javaRecord.PSObject.Properties) {
                $record | Add-Member -NotePropertyName $property.Name -NotePropertyValue $property.Value -Force
            }
            Write-DevState -State $state
        }
        elseif (-not (Test-DevOwnedRecordIdentity -Record $state.backend -Kind backend)) {
            $state.backend = [pscustomobject]@{ Kind = 'backend'; Owned = $false }
        }

        if ($webListeners.Count -eq 0) {
            $pipeName = 'EthioloadAI-dev-' + [guid]::NewGuid().ToString('N')
            $record = Start-DevManagedProcess -Kind web -PipeName $pipeName
            $state.web = $record
            Write-DevState -State $state
            if (-not (Wait-DevEndpoint -Kind web -TimeoutSeconds 30)) {
                throw 'React/Vite did not become healthy at http://127.0.0.1:5173.'
            }
        }
        elseif (-not (Test-DevOwnedRecordIdentity -Record $state.web -Kind web)) {
            $state.web = [pscustomobject]@{ Kind = 'web'; Owned = $false }
        }

        if ($FlutterWeb) {
            if ($flutterListeners.Count -eq 0) {
                $pipeName = 'EthioloadAI-dev-' + [guid]::NewGuid().ToString('N')
                $record = Start-DevManagedProcess -Kind flutter -PipeName $pipeName
                $state.flutter = $record
                Write-DevState -State $state
                if (-not (Wait-DevEndpoint -Kind flutter -TimeoutSeconds 30)) {
                    throw 'Flutter Web preview did not become healthy at http://127.0.0.1:8765.'
                }
            }
            elseif (-not (Test-DevOwnedRecordIdentity -Record $state.flutter -Kind flutter)) {
                $state.flutter = [pscustomobject]@{ Kind = 'flutter'; Owned = $false }
            }
        }
        $state.StartupInProgress = $false
        Write-DevState -State $state

        $backendResponse = Invoke-RestMethod -Uri "http://127.0.0.1:$script:DevWebPort$script:DevHealthPath" `
            -TimeoutSec 5 -ErrorAction Stop
        if ($backendResponse.status -ne 'UP') {
            throw 'React/Vite did not successfully proxy the backend health endpoint.'
        }
        Write-Host "Database:        $($database.Name) on $($configuration.DatabaseHost):$($configuration.DatabasePort)/$($configuration.DatabaseName)"
        Write-Host "Backend health:  http://127.0.0.1:$script:DevBackendPort$script:DevHealthPath"
        Write-Host "Web app:         http://localhost:$script:DevWebPort/"
        if ($FlutterWeb) {
            Write-Host "Flutter Web:     http://127.0.0.1:$script:DevFlutterPort/"
        }
    }
    catch {
        $failure = $_
        try {
            Stop-DevWorkflowState -State $state
        }
        catch {
            Write-Warning "Startup cleanup was incomplete: $($_.Exception.Message)"
        }
        throw $failure
    }
}

function Start-DevWorkflow {
    [CmdletBinding()]
    param([switch]$FlutterWeb)
    $mutex = Enter-DevWorkflowLock
    try {
        Invoke-DevWorkflowStart -FlutterWeb:$FlutterWeb
    }
    finally {
        Exit-DevWorkflowLock -Mutex $mutex
    }
}

function Invoke-DevWorkflowStop {
    [CmdletBinding()]
    param()

    $state = Read-DevState
    if (-not $state) {
        Write-Host 'No launcher-owned development services are recorded; nothing was stopped.'
        return
    }
    Stop-DevWorkflowState -State $state
    Write-Host 'Stopped only development services started by scripts/dev-up.ps1.'
}

function Stop-DevWorkflow {
    [CmdletBinding()]
    param()
    $mutex = Enter-DevWorkflowLock
    try {
        Invoke-DevWorkflowStop
    }
    finally {
        Exit-DevWorkflowLock -Mutex $mutex
    }
}
