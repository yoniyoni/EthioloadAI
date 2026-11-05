$ErrorActionPreference = 'Stop'
. (Join-Path (Split-Path -Parent $PSScriptRoot) 'dev-workflow.ps1')

function Assert-DevThrows {
    param([scriptblock]$Action, [string]$MessagePattern)
    try {
        & $Action
    }
    catch {
        if ($_.Exception.Message -notlike $MessagePattern) {
            throw "Unexpected error message: $($_.Exception.Message)"
        }
        return
    }
    throw "Expected an error matching $MessagePattern."
}

function Start-DisposableJobTree {
    $suffix = [guid]::NewGuid().ToString('N')
    $scriptPath = Join-Path $env:TEMP "dev-job-owner-$suffix.ps1"
    $gatePath = Join-Path $env:TEMP "dev-job-owner-$suffix.start"
    $childPidPath = Join-Path $env:TEMP "dev-job-owner-$suffix.child"
    $powershellPath = (Get-Command powershell.exe -CommandType Application).Source
    $childScript = @"
while (-not [IO.File]::Exists('$gatePath')) { Start-Sleep -Milliseconds 50 }
`$child = Start-Process -FilePath '$powershellPath' -ArgumentList @('-NoProfile', '-Command', 'Start-Sleep -Seconds 60') -PassThru
[IO.File]::WriteAllText('$childPidPath', [string]`$child.Id)
Start-Sleep -Seconds 60
"@
    Set-Content -LiteralPath $scriptPath -Value $childScript -Encoding UTF8
    $process = Start-Process -FilePath $powershellPath -ArgumentList @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$scriptPath`""
    ) -PassThru
    return [pscustomobject]@{
        Process = $process
        ScriptPath = $scriptPath
        GatePath = $gatePath
        ChildPidPath = $childPidPath
    }
}

Describe 'PostgreSQL process identity' {
    It 'requires the expected service executable and listener process for port ownership' {
        $script:fakeProcesses = @(
            [pscustomobject]@{
                ProcessId = 101; ParentProcessId = 0; Name = 'pg_ctl.exe'
                ExecutablePath = 'C:\PostgreSQL\15\bin\pg_ctl.exe'
                CommandLine = 'pg_ctl.exe runservice -D C:\PostgreSQL\data'
            },
            [pscustomobject]@{
                ProcessId = 102; ParentProcessId = 101; Name = 'postgres.exe'
                ExecutablePath = 'C:\PostgreSQL\15\bin\postgres.exe'
                CommandLine = 'postgres.exe -D C:\PostgreSQL\data'
            }
        )
        Mock Get-CimInstance {
            if ($ClassName -eq 'Win32_Service') {
                return [pscustomobject]@{ State = 'Running'; ProcessId = 101 }
            }
            return $script:fakeProcesses
        }
        $service = [pscustomobject]@{
            Name = 'postgresql-x64-15'; ProcessId = 101
            ServiceExecutable = 'C:\PostgreSQL\15\bin\pg_ctl.exe'
            PostgresExecutable = 'C:\PostgreSQL\15\bin\postgres.exe'
            DataDirectory = 'C:\PostgreSQL\data'
        }

        (Test-DevPostgresListenerOwned -Service $service -Listeners @(
            [pscustomobject]@{ OwningProcess = 102 }
        )) | Should Be $true
        $script:fakeProcesses = @($script:fakeProcesses[0])
        (Test-DevPostgresListenerOwned -Service $service -Listeners @(
            [pscustomobject]@{ OwningProcess = 102 }
        )) | Should Be $false
    }
}

function Start-DisposableJobOwner {
    $suffix = [guid]::NewGuid().ToString('N')
    $scriptPath = Join-Path $env:TEMP "dev-job-launcher-$suffix.ps1"
    $childPidPath = Join-Path $env:TEMP "dev-job-launcher-$suffix.child"
    $powershellPath = (Get-Command powershell.exe -CommandType Application).Source
    $jobHelper = Join-Path $PSScriptRoot '..\dev-process-job.ps1'
    $ownerScript = @"
. '$jobHelper'
`$job = New-DevProcessJob
`$child = Start-Process -FilePath '$powershellPath' -ArgumentList @('-NoProfile', '-Command', 'Start-Sleep -Seconds 60') -PassThru
`$job.Assign(`$child.Id)
[IO.File]::WriteAllText('$childPidPath', [string]`$child.Id)
Start-Sleep -Seconds 60
"@
    Set-Content -LiteralPath $scriptPath -Value $ownerScript -Encoding UTF8
    $owner = Start-Process -FilePath $powershellPath -ArgumentList @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$scriptPath`""
    ) -PassThru
    return [pscustomobject]@{
        Process = $owner
        ScriptPath = $scriptPath
        ChildPidPath = $childPidPath
    }
}

Describe 'normal development workflow' {
    BeforeEach {
        $script:DevDatabaseWaitSeconds = 0.1
        $script:events = New-Object System.Collections.ArrayList
        $script:databaseState = 'Running'
        $script:databasePid = 51
        $script:databaseReady = $true
        $script:backendHealthy = $false
        $script:webHealthy = $false
        $script:flutterHealthy = $false
        $script:listeners = @{}
        $script:dockerContainers = @()
        $script:startedKinds = @()
        $script:state = $null
        Mock Test-DevMigrationSafety { }

        Mock Test-DevDockerEngine { return $true }
        Mock Get-DevConfiguration {
            return [pscustomobject]@{
                Profile = 'dev'; DatabaseHost = '127.0.0.1'; DatabasePort = 5433
                DatabaseName = 'ethioloadai_spring'; DatabaseUser = 'postgres'
                BackendPort = 8080; WebPort = 5173; FlutterPort = 8765
            }
        }
        Mock Get-DevPostgresService {
            return [pscustomobject]@{
                Name = 'postgresql-x64-15'; State = $script:databaseState
                ProcessId = $script:databasePid; ReadinessCommand = 'pg_isready.exe'
            }
        }
        Mock Get-DevDockerContainersOnPort { return $script:dockerContainers }
        Mock Get-DevPortListeners {
            if ($script:listeners.ContainsKey($Port)) { return @($script:listeners[$Port]) }
            return @()
        }
        Mock Test-DevPostgresListenerOwned { return $true }
        Mock Test-DevListenerBelongsToWorkspace { return $true }
        Mock Start-DevPostgresService {
            [void]$script:events.Add('start-db')
            $script:databaseState = 'Running'
            $script:databasePid = 601
        }
        Mock Test-DevDatabaseReady {
            [void]$script:events.Add('db-ready')
            return $script:databaseReady
        }
        Mock Start-Sleep { }
        Mock Test-DevBackendHealth { return $script:backendHealthy }
        Mock Test-DevFrontendHealth {
            if ($Kind -eq 'web') { return $script:webHealthy }
            return $script:flutterHealthy
        }
        Mock Wait-DevEndpoint {
            [void]$script:events.Add("wait-$Kind")
            switch ($Kind) {
                'backend' { return $script:backendHealthy }
                'web' { return $script:webHealthy }
                'flutter' { return $script:flutterHealthy }
            }
        }
        Mock Start-DevManagedProcess {
            [void]$script:events.Add("start-$Kind")
            $script:startedKinds += $Kind
            if ($Kind -eq 'backend') { $script:backendHealthy = $true }
            if ($Kind -eq 'web') { $script:webHealthy = $true }
            if ($Kind -eq 'flutter') { $script:flutterHealthy = $true }
            return [pscustomobject]@{
                Kind = $Kind; Owned = $true; ProcessId = 900
                StartedAtUtc = [DateTime]::UtcNow.ToString('o'); PipeName = $PipeName
                ShutdownEventName = if ($Kind -eq 'backend') { $PipeName } else { $null }
            }
        }
        Mock Get-DevBackendJvmRecord {
            return [pscustomobject]@{
                JavaProcessId = 901
                JavaStartedAtUtc = [DateTime]::UtcNow.ToString('o')
                JavaExecutablePath = 'C:\Java\bin\java.exe'
                JavaCommandLine = 'java org.codehaus.plexus.classworlds.launcher.Launcher C:\repo\backend-spring-boot'
                DescendantProcesses = @()
            }
        }
        Mock Test-DevOwnedRecordIdentity { return $false }
        Mock Read-DevState { return $script:state }
        Mock Write-DevState { $script:state = $State; [void]$script:events.Add('save-state') }
        Mock Remove-DevState { [void]$script:events.Add('remove-state') }
        Mock Stop-DevRecord {
            if ($Record -and $Record.Owned) { [void]$script:events.Add("stop-$($Record.Kind)") }
        }
        Mock Invoke-RestMethod {
            return [pscustomobject]@{ status = 'UP' }
        }
    }

    It 'starts an initially stopped database, backend, and web app in dependency order' {
        $script:databaseState = 'Stopped'
        $script:databasePid = 0
        Start-DevWorkflow

        $script:events.IndexOf('start-db') | Should BeLessThan $script:events.IndexOf('db-ready')
        $script:events.IndexOf('db-ready') | Should BeLessThan $script:events.IndexOf('start-backend')
        $script:events.IndexOf('start-backend') | Should BeLessThan $script:events.IndexOf('start-web')
        $script:startedKinds | Should Be @('backend', 'web')
        $script:state.Database.StartedByLauncher | Should Be $true
        Assert-MockCalled Test-DevPostgresListenerOwned -Times 1 -Scope It -ParameterFilter {
            $Service.ProcessId -eq 601
        }
        Assert-MockCalled Get-DevPostgresService -Times 3 -Scope It
        Assert-MockCalled Invoke-RestMethod -Times 1 -Scope It -ParameterFilter {
            $Uri -eq 'http://127.0.0.1:5173/api/actuator/health'
        }
    }

    It 'reuses healthy services without starting duplicate processes' {
        $script:listeners[8080] = @([pscustomobject]@{ OwningProcess = 500 })
        $script:listeners[5173] = @([pscustomobject]@{ OwningProcess = 501 })
        $script:backendHealthy = $true
        $script:webHealthy = $true
        Start-DevWorkflow

        $script:startedKinds.Count | Should Be 0
        Assert-MockCalled Start-DevPostgresService -Times 0 -Scope It
    }

    It 'fails before touching services when Docker Engine is unavailable' {
        Mock Test-DevDockerEngine { return $false }
        { Assert-DevThrows { Start-DevWorkflow } '*Docker Engine is unavailable*' } | Should Not Throw
        Assert-MockCalled Start-DevPostgresService -Times 0 -Scope It
        Assert-MockCalled Start-DevManagedProcess -Times 0 -Scope It
    }

    It 'leaves shared PostgreSQL running when readiness fails' {
        $script:databaseState = 'Stopped'
        $script:databaseReady = $false
        Mock Test-DevDatabaseReady { return $false }

        { Assert-DevThrows { Start-DevWorkflow } '*PostgreSQL did not become ready*' } | Should Not Throw
        $script:events.IndexOf('start-db') | Should BeGreaterThan -1
        $script:events.Contains('stop-db') | Should Be $false
        $script:startedKinds.Count | Should Be 0
    }

    It 'cleans up owned resources after backend startup failure before starting Vite' {
        $script:databaseState = 'Stopped'
        Mock Wait-DevEndpoint {
            [void]$script:events.Add("wait-$Kind")
            return $false
        }

        { Assert-DevThrows { Start-DevWorkflow } '*Spring Boot did not become healthy*' } | Should Not Throw
        $script:startedKinds | Should Be @('backend')
        $script:events.IndexOf('stop-backend') | Should BeGreaterThan $script:events.IndexOf('start-backend')
        $script:events.Contains('stop-db') | Should Be $false
    }

    It 'refuses a port owned by another process without stopping it or starting services' {
        $script:listeners[8080] = @([pscustomobject]@{ OwningProcess = 1234 })
        $script:backendHealthy = $false

        { Assert-DevThrows { Start-DevWorkflow } '*Port 8080 is occupied*' } | Should Not Throw
        Assert-MockCalled Start-DevPostgresService -Times 0 -Scope It
        Assert-MockCalled Start-DevManagedProcess -Times 0 -Scope It
        Assert-MockCalled Stop-DevRecord -Times 0 -Scope It
    }

    It 'does not reuse or stop a healthy but unverified backend process' {
        $script:listeners[8080] = @([pscustomobject]@{ OwningProcess = 1234 })
        $script:backendHealthy = $true
        Mock Test-DevListenerBelongsToWorkspace { return $false }

        { Assert-DevThrows { Start-DevWorkflow } '*healthy but unverified backend*' } | Should Not Throw
        Assert-MockCalled Start-DevManagedProcess -Times 0 -Scope It
        Assert-MockCalled Stop-DevRecord -Times 0 -Scope It
    }

    It 'refuses a PostgreSQL port owned by another service' {
        $script:listeners[5433] = @([pscustomobject]@{ OwningProcess = 1234 })
        Mock Test-DevPostgresListenerOwned { return $false }

        { Assert-DevThrows { Start-DevWorkflow } '*Port 5433 is occupied*' } | Should Not Throw
        Assert-MockCalled Start-DevPostgresService -Times 0 -Scope It
        Assert-MockCalled Start-DevManagedProcess -Times 0 -Scope It
    }

    It 'never issues a Docker container lifecycle command' {
        Start-DevWorkflow
        Assert-MockCalled Get-DevDockerContainersOnPort -Times 1 -Scope It -ParameterFilter { $Port -eq 8080 }
        Assert-MockCalled Start-DevManagedProcess -Times 2 -Scope It
    }

    It 'uses Docker only for read-only availability and ownership checks' {
        Mock Get-DevDockerContainersOnPort {
            return @(Invoke-DevDockerReadOnly -Arguments @(
                'ps', '--filter', "publish=$Port", '--format', '{{.Names}}'
            ))
        }
        Mock Invoke-DevDockerReadOnly {
            [void]$script:events.Add("docker-$($Arguments[0])")
            if ($Arguments[0] -eq 'info') { return '29.8.2' }
            return @()
        }
        Mock Test-DevDockerEngine {
            return (Invoke-DevDockerReadOnly -Arguments @('info', '--format', '{{.ServerVersion}}')) -eq '29.8.2'
        }
        Start-DevWorkflow

        $script:events.Contains('docker-info') | Should Be $true
        $script:events.Contains('docker-ps') | Should Be $true
        $script:events.Contains('docker-start') | Should Be $false
        $script:events.Contains('docker-stop') | Should Be $false
        $script:events.Contains('docker-rm') | Should Be $false
        Assert-MockCalled Invoke-DevDockerReadOnly -Times 2 -Scope It
    }

    It 'starts Flutter Web only when requested' {
        Start-DevWorkflow -FlutterWeb

        $script:startedKinds | Should Be @('backend', 'web', 'flutter')
        Assert-MockCalled Wait-DevEndpoint -Times 1 -Scope It -ParameterFilter { $Kind -eq 'flutter' }
    }

    It 'signals only the recorded backend owner and waits for it to exit' {
        $script:startedAt = [DateTime]::UtcNow.ToString('o')
        $script:stopSignaled = $false
        Mock Get-Process {
            if ($script:stopSignaled) { return $null }
            return [pscustomobject]@{ StartTime = [DateTime]::Parse($script:startedAt) }
        }
        Mock Get-CimInstance { return [pscustomobject]@{ Name = 'powershell.exe'; CommandLine = 'start-local.ps1' } }
        Mock Test-DevOwnedProcessCommand { return $true }
        Mock Send-DevBackendStop {
            $script:stopSignaled = $true
            [void]$script:events.Add("stop-event-$EventName")
        }

        Stop-DevBackendProcessJob -Record ([pscustomobject]@{
            Kind = 'backend'; ProcessId = 900; StartedAtUtc = $script:startedAt
            ShutdownEventName = 'Local\EthioloadAI-test-backend'
        })

        $script:events.Contains('stop-event-Local\EthioloadAI-test-backend') | Should Be $true
        Assert-MockCalled Send-DevBackendStop -Times 1 -Scope It -ParameterFilter {
            $EventName -eq 'Local\EthioloadAI-test-backend'
        }
    }

    It 'sends the named-pipe stop command only to the recorded Vite process' {
        $script:startedAt = [DateTime]::UtcNow.ToString('o')
        Mock Get-Process { return [pscustomobject]@{ StartTime = [DateTime]::Parse($script:startedAt) } }
        Mock Get-CimInstance { return [pscustomobject]@{ Name = 'node.exe'; CommandLine = 'dev-vite-host.mjs' } }
        Mock Test-DevOwnedProcessCommand { return $true }
        Mock Send-DevFrontendStop { [void]$script:events.Add("pipe-stop-$PipeName") }
        Mock Wait-Process { }

        Stop-DevFrontendGracefully -Record ([pscustomobject]@{
            Kind = 'web'; ProcessId = 900; StartedAtUtc = $script:startedAt; PipeName = 'owned-pipe'
        })

        $script:events.Contains('pipe-stop-owned-pipe') | Should Be $true
        Assert-MockCalled Send-DevFrontendStop -Times 1 -Scope It -ParameterFilter { $PipeName -eq 'owned-pipe' }
    }

    It 'stops Flutter, Vite, and backend while preserving shared PostgreSQL' {
        $script:state = [pscustomobject]@{
            Database = [pscustomobject]@{ Name = 'postgresql-x64-15'; StartedByLauncher = $true }
            flutter = [pscustomobject]@{ Kind = 'flutter'; Owned = $true }
            web = [pscustomobject]@{ Kind = 'web'; Owned = $true }
            backend = [pscustomobject]@{ Kind = 'backend'; Owned = $true }
        }
        Stop-DevWorkflow

        $script:events.IndexOf('stop-flutter') | Should BeLessThan $script:events.IndexOf('stop-web')
        $script:events.IndexOf('stop-web') | Should BeLessThan $script:events.IndexOf('stop-backend')
        $script:events.IndexOf('stop-backend') | Should BeLessThan $script:events.IndexOf('remove-state')
        $script:events.Contains('stop-db') | Should Be $false
    }

    It 'continues stopping independent services after a frontend shutdown failure' {
        $script:state = [pscustomobject]@{
            Database = [pscustomobject]@{ Name = 'postgresql-x64-15'; StartedByLauncher = $true }
            flutter = [pscustomobject]@{ Kind = 'flutter'; Owned = $true }
            web = [pscustomobject]@{ Kind = 'web'; Owned = $true }
            backend = [pscustomobject]@{ Kind = 'backend'; Owned = $true }
        }
        Mock Stop-DevRecord {
            if ($Record.Kind -eq 'flutter') { throw 'test shutdown failure' }
            [void]$script:events.Add("stop-$($Record.Kind)")
        }

        { Assert-DevThrows { Stop-DevWorkflow } '*Launcher state is preserved for retry*' } | Should Not Throw
        $script:events.IndexOf('stop-web') | Should BeGreaterThan -1
        $script:events.IndexOf('stop-backend') | Should BeGreaterThan $script:events.IndexOf('stop-web')
        $script:events.Contains('stop-db') | Should Be $false
        $script:events.Contains('remove-state') | Should Be $false
    }

    It 'leaves PostgreSQL running if the backend fails to stop' {
        $script:state = [pscustomobject]@{
            Database = [pscustomobject]@{ Name = 'postgresql-x64-15'; StartedByLauncher = $true }
            flutter = $null
            web = $null
            backend = [pscustomobject]@{ Kind = 'backend'; Owned = $true }
        }
        Mock Stop-DevRecord { throw 'test backend shutdown failure' }

        { Assert-DevThrows { Stop-DevWorkflow } '*backend shutdown failed*' } | Should Not Throw
        $script:events.Contains('remove-state') | Should Be $false
    }

    It 'refuses Spring startup while V78 is frozen' {
        Mock Test-DevMigrationSafety { throw 'V78 is frozen' }

        { Assert-DevThrows { Start-DevWorkflow } '*V78 is frozen*' } | Should Not Throw
        Assert-MockCalled Test-DevDockerEngine -Times 0 -Scope It
        Assert-MockCalled Start-DevPostgresService -Times 0 -Scope It
        Assert-MockCalled Start-DevManagedProcess -Times 0 -Scope It
    }

    It 'rejects a stale in-progress ownership record without touching services' {
        $script:state = [pscustomobject]@{
            SchemaVersion = 2
            RepositoryRoot = $script:DevWorkflowRoot
            StartupInProgress = $true
        }

        { Assert-DevThrows { Start-DevWorkflow } '*previous development startup did not complete*' } | Should Not Throw
        Assert-MockCalled Start-DevPostgresService -Times 0 -Scope It
        Assert-MockCalled Start-DevManagedProcess -Times 0 -Scope It
        Assert-MockCalled Stop-DevRecord -Times 0 -Scope It
    }

    It 'does not terminate a process that reused the recorded Java PID' {
        Mock Get-Process {
            return [pscustomobject]@{ StartTime = [DateTime]::UtcNow }
        }
        $record = [pscustomobject]@{
            JavaProcessId = 7001
            JavaStartedAtUtc = '2000-01-01T00:00:00Z'
            JavaExecutablePath = 'C:\Java\bin\java.exe'
            JavaCommandLine = 'java org.codehaus.plexus.classworlds.launcher.Launcher C:\repo\backend-spring-boot'
        }

        (Get-DevRecordedJvmProcess -Record $record) | Should Be $null
        Assert-MockCalled Get-CimInstance -Times 0 -Scope It
    }

    It 'does not stop stale recorded PIDs when reusing healthy workspace services' {
        $script:state = [pscustomobject]@{
            SchemaVersion = 2
            RepositoryRoot = $script:DevWorkflowRoot
            StartupInProgress = $false
            Database = [pscustomobject]@{ Name = 'postgresql-x64-15'; StartedByLauncher = $true }
            backend = [pscustomobject]@{
                Kind = 'backend'; Owned = $true; ProcessId = 12
                StartedAtUtc = '2000-01-01T00:00:00Z'; ShutdownEventName = 'stale'
            }
            web = [pscustomobject]@{
                Kind = 'web'; Owned = $true; ProcessId = 13
                StartedAtUtc = '2000-01-01T00:00:00Z'; PipeName = 'stale'
            }
            flutter = $null
        }
        $script:listeners[8080] = @([pscustomobject]@{ OwningProcess = 12 })
        $script:listeners[5173] = @([pscustomobject]@{ OwningProcess = 13 })
        $script:backendHealthy = $true
        $script:webHealthy = $true

        Start-DevWorkflow
        $script:state.backend.Owned | Should Be $false
        $script:state.web.Owned | Should Be $false
        Stop-DevWorkflow

        $script:events.Contains('stop-backend') | Should Be $false
        $script:events.Contains('stop-web') | Should Be $false
        $script:events.Contains('stop-db') | Should Be $false
    }

    It 'serializes concurrent startup attempts with the named Windows mutex' {
        $mutexName = Get-DevWorkflowMutexName
        $readyPath = Join-Path $env:TEMP ('dev-lock-ready-' + [guid]::NewGuid().ToString('N'))
        $releasePath = Join-Path $env:TEMP ('dev-lock-release-' + [guid]::NewGuid().ToString('N'))
        $lockJob = Start-Job -ScriptBlock {
            param($Name, $Ready, $Release)
            $mutex = New-Object System.Threading.Mutex($false, $Name)
            try {
                [void]$mutex.WaitOne()
                [IO.File]::WriteAllText($Ready, 'locked')
                while (-not [IO.File]::Exists($Release)) { Start-Sleep -Milliseconds 50 }
            }
            finally {
                try { $mutex.ReleaseMutex() } catch { }
                $mutex.Dispose()
            }
        } -ArgumentList $mutexName, $readyPath, $releasePath
        try {
            $deadline = [DateTime]::UtcNow.AddSeconds(10)
            while (-not (Test-Path -LiteralPath $readyPath) -and [DateTime]::UtcNow -lt $deadline) {
                Start-Sleep -Milliseconds 50
            }
            (Test-Path -LiteralPath $readyPath) | Should Be $true
            { Assert-DevThrows { Start-DevWorkflow } '*already in progress*' } | Should Not Throw
            Assert-MockCalled Start-DevPostgresService -Times 0 -Scope It
            Assert-MockCalled Start-DevManagedProcess -Times 0 -Scope It
        }
        finally {
            [IO.File]::WriteAllText($releasePath, 'release')
            Wait-Job -Job $lockJob -Timeout 10 | Out-Null
            Remove-Job -Job $lockJob -Force
            Remove-Item -LiteralPath $readyPath, $releasePath -Force -ErrorAction SilentlyContinue
        }
    }

    It 'terminates a real disposable Java child through the owned process job' {
        $javaDirectory = Join-Path $env:TEMP ('dev-workflow-java-' + [guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $javaDirectory | Out-Null
        $source = Join-Path $javaDirectory 'DevWorkflowDisposable.java'
        Set-Content -LiteralPath $source -Value @'
public final class DevWorkflowDisposable {
    public static void main(String[] args) throws Exception {
        Thread.sleep(60000);
    }
}
'@ -Encoding ASCII
        $javac = (Get-Command javac.exe -CommandType Application -ErrorAction Stop |
            Select-Object -First 1).Source
        & $javac -d $javaDirectory $source
        if ($LASTEXITCODE -ne 0) { throw 'Could not compile the disposable Java test process.' }
        $java = (Get-Command java.exe -CommandType Application -ErrorAction Stop |
            Select-Object -First 1).Source
        $process = Start-Process -FilePath $java -ArgumentList @(
            '-cp', "`"$javaDirectory`"", 'DevWorkflowDisposable'
        ) -PassThru
        $job = New-DevProcessJob
        try {
            $job.Assign($process.Id)
            $job.Terminate(1)
            $process.WaitForExit(10000) | Should Be $true
            $process.HasExited | Should Be $true
        }
        finally {
            $job.Dispose()
            $process.Refresh()
            if (-not $process.HasExited) { $process.Kill(); $process.WaitForExit() }
            $process.Dispose()
            Remove-Item -LiteralPath $javaDirectory -Recurse -Force -ErrorAction SilentlyContinue
        }
    }

    It 'kills owned descendants when the backend owner exits unexpectedly' {
        $owner = Start-DisposableJobOwner
        try {
            $deadline = [DateTime]::UtcNow.AddSeconds(10)
            while (-not (Test-Path -LiteralPath $owner.ChildPidPath) -and [DateTime]::UtcNow -lt $deadline) {
                Start-Sleep -Milliseconds 50
            }
            (Test-Path -LiteralPath $owner.ChildPidPath) | Should Be $true
            $childId = [int](Get-Content -LiteralPath $owner.ChildPidPath -Raw)
            $owner.Process.Kill()
            $owner.Process.WaitForExit(10000) | Should Be $true
            $childExited = $true
            try {
                $child = [Diagnostics.Process]::GetProcessById($childId)
                $childExited = $child.WaitForExit(10000)
                $child.Dispose()
            }
            catch [ArgumentException] { }
            $childExited | Should Be $true
        }
        finally {
            $owner.Process.Refresh()
            if (-not $owner.Process.HasExited) { $owner.Process.Kill(); $owner.Process.WaitForExit() }
            foreach ($pidToCheck in @($(if (Test-Path $owner.ChildPidPath) { Get-Content $owner.ChildPidPath }))) {
                try {
                    $process = [Diagnostics.Process]::GetProcessById([int]$pidToCheck)
                    if (-not $process.HasExited) { $process.Kill(); $process.WaitForExit() }
                    $process.Dispose()
                }
                catch [ArgumentException] { }
            }
            $owner.Process.Dispose()
            Remove-Item -LiteralPath $owner.ScriptPath, $owner.ChildPidPath `
                -Force -ErrorAction SilentlyContinue
        }
    }
}
