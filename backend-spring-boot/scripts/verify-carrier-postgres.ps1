[CmdletBinding()]
param(
    [string]$PsqlPath = 'C:\Program Files\PostgreSQL\18\bin\psql.exe',
    [int]$Port = 18087
)
# Run in a separate PowerShell process. Requires local .env credentials with CREATEDB.
# Creates and removes ONLY a randomly named verification database, never the configured application database.
$ErrorActionPreference = 'Stop'
$backend = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $backend
$settings = @{}
foreach ($line in [IO.File]::ReadAllLines((Join-Path $backend '.env'))) {
    if ($line.Trim() -match '^(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
        $settings[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'")
    }
}
$jdbc = $settings['SPRING_DATASOURCE_URL']
if ($jdbc -notmatch '^jdbc:postgresql://([^/:]+):(\d+)/([^?]+)') {
    throw 'Expected a PostgreSQL JDBC URL with an explicit host and port in .env.'
}
$pgHostName = $Matches[1]
$pgPortNumber = $Matches[2]
$sourceDatabase = $Matches[3]
$env:PGPASSWORD = $settings['SPRING_DATASOURCE_PASSWORD']
$pgUserName = $settings['SPRING_DATASOURCE_USERNAME']
$verificationDatabase = 'carrier_phase1_' + [Guid]::NewGuid().ToString('N').Substring(0, 16)
$created = $false
$server = $null
$http = $null
function Sql([string]$database, [string]$query) {
    $result = & $PsqlPath -X -w -h $pgHostName -p $pgPortNumber -U $pgUserName -d $database -v ON_ERROR_STOP=1 -At -c $query
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL verification command failed.' }
    return $result
}
function Api([string]$method, [string]$path, $payload, [string]$token) {
    $parameters = @{ Method = $method; Uri = "http://127.0.0.1:$Port/api$path" }
    if ($token) { $parameters.Headers = @{ Authorization = "Bearer $token" } }
    if ($null -ne $payload) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $payload | ConvertTo-Json -Depth 10 -Compress
    }
    return Invoke-RestMethod @parameters
}
try {
    [void](Sql $sourceDatabase "CREATE DATABASE $verificationDatabase")
    $created = $true
    [void](Sql $verificationDatabase 'CREATE EXTENSION IF NOT EXISTS postgis')
    $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://${pgHostName}:${pgPortNumber}/$verificationDatabase"
    $env:SPRING_DATASOURCE_USERNAME = $pgUserName
    $env:SPRING_DATASOURCE_PASSWORD = $env:PGPASSWORD
    $env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'org.postgresql.Driver'
    $env:SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT = 'org.hibernate.dialect.PostgreSQLDialect'
    $env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
    $env:SPRING_FLYWAY_ENABLED = 'true'
    # PostGIS creates spatial_ref_sys before Flyway; baseline at 0 still executes V1 onward.
    $env:SPRING_FLYWAY_BASELINE_ON_MIGRATE = 'true'
    $env:SPRING_FLYWAY_BASELINE_VERSION = '0'
    $env:SPRING_JPA_SHOW_SQL = 'false'
    $env:LOGGING_LEVEL_ORG_HIBERNATE_SQL = 'WARN'
    $env:SPRING_PROFILES_ACTIVE = 'test'
    $env:JWT_SECRET = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue' # Native JVM warnings on stderr are not failures.
    & .\mvnw.cmd '-Dtest=CarrierCompanyIntegrationTest,CarrierCompanyTransactionTest' test > phase1-postgres-tests.log 2>&1
    $testExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorAction
    if ($testExitCode -ne 0) { throw 'PostgreSQL tests failed; see phase1-postgres-tests.log.' }
    Write-Output 'PostgreSQL carrier integration and transaction tests passed.'

    $jar = Join-Path $backend 'target/ethioloadai-backend-1.0.0.jar'
    if (-not (Test-Path -LiteralPath $jar)) { throw 'Build the current JAR with mvnw.cmd package first.' }
    $env:SPRING_PROFILES_ACTIVE = 'dev'
    $env:SERVER_PORT = "$Port"
    $server = Start-Process -FilePath (Get-Command java.exe).Source -ArgumentList @('-jar', ('"' + $jar + '"')) -WorkingDirectory $backend -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $backend 'phase1-startup.log') -RedirectStandardError (Join-Path $backend 'phase1-startup-error.log')
    $healthy = $false
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        if ($server.HasExited) { throw 'Backend exited; see phase1-startup.log.' }
        try {
            $health = Api GET '/actuator/health' $null $null
            if ($health.status -eq 'UP') { $healthy = $true; break }
        } catch { }
        Start-Sleep -Seconds 1
    }
    if (-not $healthy) { throw 'Backend did not become healthy.' }
    Write-Output 'Executable JAR started with PostgreSQL, Flyway V57, and Hibernate schema validation.'

    $password = [Guid]::NewGuid().ToString('N')
    $ownerAuth = Api POST '/auth/register' @{ fullName = 'Phase One Owner'; phone = '+251911000001'; email = 'phase1-owner@example.test'; password = $password; role = 'shipper' } $null
    [void](Api POST '/auth/register' @{ fullName = 'Phase One Admin'; phone = '+251911000002'; email = 'phase1-admin@example.test'; password = $password; role = 'shipper' } $null)
    [void](Sql $verificationDatabase "UPDATE users SET role = 'admin' WHERE email = 'phase1-admin@example.test'")
    $adminAuth = Api POST '/auth/login' @{ identifier = 'phase1-admin@example.test'; password = $password } $null
    $company = Api POST '/carrier-companies' @{ companyName = 'Phase One Transport'; tinNumber = 'TIN-SMOKE-001'; businessRegistrationNumber = 'REG-SMOKE-001'; phone = '+251911000001'; email = 'office@example.test'; businessAddress = 'Bole Road'; city = 'Addis Ababa' } $ownerAuth.token
    if ($company.verificationStatus -ne 'DRAFT') { throw 'Expected DRAFT.' }
    Add-Type -AssemblyName System.Net.Http
    $http = New-Object System.Net.Http.HttpClient
    $http.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $ownerAuth.token)
    $documentIds = @()
    foreach ($type in @('BUSINESS_LICENSE', 'BUSINESS_REGISTRATION', 'TIN_CERTIFICATE')) {
        $multipart = New-Object System.Net.Http.MultipartFormDataContent
        try {
            $bytes = [Text.Encoding]::UTF8.GetBytes('%PDF-1.4 Phase 1 verification document')
            $file = New-Object System.Net.Http.ByteArrayContent -ArgumentList (, $bytes)
            $file.Headers.ContentType = New-Object System.Net.Http.Headers.MediaTypeHeaderValue('application/pdf')
            $multipart.Add($file, 'file', 'verification.pdf')
            $upload = $http.PostAsync("http://127.0.0.1:$Port/api/carrier-companies/$($company.id)/documents?documentType=$type", $multipart).GetAwaiter().GetResult()
            try {
                [void]$upload.EnsureSuccessStatusCode()
                $document = $upload.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
                $documentIds += $document.id
            } finally { $upload.Dispose() }
        } finally { $multipart.Dispose() }
    }
    $pending = Api POST "/carrier-companies/$($company.id)/submit" $null $ownerAuth.token
    if ($pending.verificationStatus -ne 'PENDING') { throw 'Expected PENDING.' }
    [void](Api POST "/admin/carrier-companies/$($company.id)/request-information" @{ message = 'Confirm contact details' } $adminAuth.token)
    $read = Api GET "/carrier-companies/$($company.id)" $null $ownerAuth.token
    if ($read.reviewMessage -ne 'Confirm contact details') { throw 'Owner cannot see review message.' }
    [void](Api POST "/carrier-companies/$($company.id)/submit" $null $ownerAuth.token)
    $approved = Api POST "/admin/carrier-companies/$($company.id)/approve" $null $adminAuth.token
    if ($approved.verificationStatus -ne 'APPROVED') { throw 'Expected APPROVED.' }
    $download = $http.GetByteArrayAsync("http://127.0.0.1:$Port/api/carrier-companies/$($company.id)/documents/$($documentIds[0])/file").GetAwaiter().GetResult()
    if ([Text.Encoding]::UTF8.GetString($download) -ne '%PDF-1.4 Phase 1 verification document') { throw 'Document bytes changed.' }
    # Remove only objects created by this smoke test, before dropping its database.
    $keys = @(Sql $verificationDatabase 'SELECT storage_key FROM carrier_company_documents')
    $uploadRoot = [IO.Path]::GetFullPath((Join-Path $backend 'uploads'))
    foreach ($key in $keys) {
        if ($key -notmatch '^carrier-companies/[0-9]+/[a-f0-9-]+\.pdf$') { throw 'Unexpected verification storage key.' }
        $target = [IO.Path]::GetFullPath((Join-Path $uploadRoot $key))
        if (-not $target.StartsWith($uploadRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid cleanup path.' }
        Remove-Item -LiteralPath $target -ErrorAction SilentlyContinue
    }
    Write-Output 'HTTP smoke passed: register, create, upload 3 real files, submit, request information, resubmit, approve, owner read, private download.'
} finally {
    if ($http) { $http.Dispose() }
    if ($server -and -not $server.HasExited) { Stop-Process -Id $server.Id; $server.WaitForExit() }
    if ($created -and $verificationDatabase -match '^carrier_phase1_[a-f0-9]{16}$' -and $verificationDatabase -ne $sourceDatabase) {
        [void](Sql $sourceDatabase "DROP DATABASE $verificationDatabase WITH (FORCE)")
        Write-Output 'Removed the isolated verification database.'
    }
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}
