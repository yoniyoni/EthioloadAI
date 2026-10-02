[CmdletBinding()]
param(
 [string]$PsqlPath = 'C:\Program Files\PostgreSQL\18\bin\psql.exe',
 [string]$PythonPath = 'C:\Program Files\PostgreSQL\18\pgAdmin 4\python\python.exe',
 [int]$Port = 18088,
 [switch]$LiveEmail,
 [switch]$FullVerification
)
$ErrorActionPreference = 'Stop'
$backend = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $backend
$settings = @{}
foreach ($line in [IO.File]::ReadAllLines((Join-Path $backend '.env'))) {
 if ($line.Trim() -match '^(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
  $settings[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'")
 }
}
if ($settings['SPRING_DATASOURCE_URL'] -notmatch '^jdbc:postgresql://([^/:]+):(\d+)/([^?]+)') { throw 'Expected a PostgreSQL JDBC URL with host and port.' }
$pgHostName=$Matches[1]; $pgPortNumber=$Matches[2]; $sourceDatabase=$Matches[3]
$pgUserName=$settings['SPRING_DATASOURCE_USERNAME']
$env:PGPASSWORD=$settings['SPRING_DATASOURCE_PASSWORD']
$testDatabase='carrier_phase2_'+[Guid]::NewGuid().ToString('N').Substring(0,16)
$created=$false; $server=$null; $capture=$null
function Sql([string]$database,[string]$query) {
 $result=& $PsqlPath -X -w -h $pgHostName -p $pgPortNumber -U $pgUserName -d $database -v ON_ERROR_STOP=1 -At -c $query
 if($LASTEXITCODE -ne 0) { throw 'PostgreSQL verification command failed.' }
 return $result
}
function Api([string]$method,[string]$path,$payload,[string]$token) {
 $params=@{Method=$method;Uri="http://127.0.0.1:$Port/api$path"}
 if($token) { $params.Headers=@{Authorization="Bearer $token"} }
 if($null -ne $payload) {$params.ContentType='application/json';$params.Body=$payload|ConvertTo-Json -Depth 10 -Compress}
 return Invoke-RestMethod @params
}
function StartBackend {
 $jar=Join-Path $backend 'target/ethioloadai-backend-1.0.0.jar'
 $script:server=Start-Process (Get-Command java.exe).Source -ArgumentList @('-jar',('"' + $jar + '"')) -WorkingDirectory $backend -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $backend 'phase2-startup.log') -RedirectStandardError (Join-Path $backend 'phase2-startup-error.log')
 for($attempt=0;$attempt -lt 90;$attempt++) {
  if($script:server.HasExited) {throw 'Backend exited; inspect phase2-startup.log.'}
  try {if((Api GET '/actuator/health' $null $null).status -eq 'UP') {return}} catch {}
  Start-Sleep -Seconds 1
 }
 throw 'Backend startup timed out.'
}
try {
 [void](Sql $sourceDatabase "CREATE DATABASE $testDatabase");$created=$true
 [void](Sql $testDatabase 'CREATE EXTENSION IF NOT EXISTS postgis')
 $env:SPRING_DATASOURCE_URL="jdbc:postgresql://${pgHostName}:${pgPortNumber}/$testDatabase"
 $env:SPRING_DATASOURCE_USERNAME=$pgUserName
 $env:SPRING_DATASOURCE_PASSWORD=$env:PGPASSWORD
 $env:SPRING_DATASOURCE_DRIVER_CLASS_NAME='org.postgresql.Driver'
 $env:SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT='org.hibernate.dialect.PostgreSQLDialect'
 $env:SPRING_JPA_HIBERNATE_DDL_AUTO='validate'
 $env:SPRING_FLYWAY_ENABLED='true'
 $env:SPRING_FLYWAY_BASELINE_ON_MIGRATE='true'
 $env:SPRING_FLYWAY_BASELINE_VERSION='0'
 $env:SPRING_JPA_SHOW_SQL='false'
 $env:LOGGING_LEVEL_ORG_HIBERNATE_SQL='WARN'
 $env:LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB='WARN'
 $env:LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY='WARN'
 $env:SPRING_PROFILES_ACTIVE='test'
 $env:JWT_SECRET=[Guid]::NewGuid().ToString('N')+[Guid]::NewGuid().ToString('N')
 $oldError=$ErrorActionPreference;$ErrorActionPreference='Continue'
 & .\mvnw.cmd '-Dtest=CarrierCompanyIntegrationTest,CarrierCompanyTransactionTest,CompanyTeamIntegrationTest,CompanyTeamTransactionTest,Phase2PostgresVerification' test > phase2-postgres-tests.log 2>&1
 $testExit=$LASTEXITCODE;$ErrorActionPreference=$oldError
 if($testExit -ne 0) {throw 'PostgreSQL tests failed; see phase2-postgres-tests.log.'}
 Write-Output 'PostgreSQL Phase 1/2 integration, concurrency, and migration checks passed.'

 $env:TEST_MAIL_CAPTURE_SECRET=[Guid]::NewGuid().ToString('N')
 $capture=Start-Process $PythonPath -ArgumentList ('"'+(Join-Path $PSScriptRoot 'capture-invitation-smtp.py')+'"') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $backend 'phase2-smtp-capture.log') -RedirectStandardError (Join-Path $backend 'phase2-smtp-capture-error.log')
 $env:SMTP_HOST='127.0.0.1';$env:SMTP_PORT='12525';$env:SMTP_SECURE='false'
 $env:SMTP_USER='local-test';$env:SMTP_PASSWORD='local-test-only';$env:EMAIL_FROM='sender@example.test'
 $env:FRONTEND_BASE_URL='http://localhost:5173'
 $env:INVITATION_COOLDOWN_SECONDS='0'
 $env:SPRING_PROFILES_ACTIVE='dev';$env:SERVER_PORT="$Port"
 StartBackend
 Write-Output 'Executable JAR started with Flyway V60 and Hibernate schema validation.'
 if ($FullVerification) {
  $env:VERIFICATION_PORT="$Port"
  $env:VERIFICATION_PSQL=$PsqlPath
  $env:VERIFICATION_PGHOST=$pgHostName
  $env:VERIFICATION_PGPORT=$pgPortNumber
  & $PythonPath (Join-Path $PSScriptRoot 'verify-carrier-full-http.py')
  if ($LASTEXITCODE -ne 0) {throw 'Full HTTP verification failed.'}
 }
 $password=[Guid]::NewGuid().ToString('N')
 $owner=Api POST '/auth/register' @{fullName='Phase Two Owner';phone='+251922000001';email='phase2-owner@example.test';password=$password;role='shipper'} $null
 [void](Api POST '/auth/register' @{fullName='Phase Two Admin';phone='+251922000002';email='phase2-admin@example.test';password=$password;role='shipper'} $null)
 [void](Sql $testDatabase "UPDATE users SET role='admin' WHERE email='phase2-admin@example.test'")
 $admin=Api POST '/auth/login' @{identifier='phase2-admin@example.test';password=$password} $null
 $company=Api POST '/carrier-companies' @{companyName='Freight-Link Phase 2 Verification';tinNumber='TIN-VERIFY-2';businessRegistrationNumber='REG-VERIFY-2';phone='+251922000001';email='office@example.test';businessAddress='Bole Road';city='Addis Ababa'} $owner.token
 # Phase 1 real-file HTTP workflow is covered by verify-carrier-postgres.ps1.
 # Seed complete verification here to isolate the team HTTP checks.
 [void](Sql $testDatabase "UPDATE carrier_companies SET verification_status='APPROVED',submitted_at=CURRENT_TIMESTAMP,reviewed_at=CURRENT_TIMESTAMP,reviewed_by=(SELECT id FROM users WHERE email='phase2-admin@example.test') WHERE id=$($company.id)")
 $team="/carrier-companies/$($company.id)/team"
 $members=Api GET "$team/members" $null $owner.token
 if($members.content[0].role -ne 'CARRIER_OWNER') {throw 'Owner membership missing.'}
 $invited=Api POST "$team/invitations" @{email='phase2-employee@example.test';role='FLEET_MANAGER'} $owner.token
 $mail=Invoke-RestMethod -Uri 'http://127.0.0.1:12526/' -Headers @{'X-Test-Key'=$env:TEST_MAIL_CAPTURE_SECRET}
 if(((-not $FullVerification) -and $mail.count -ne 1) -or -not $mail.latest.token) {throw 'Local SMTP did not capture invitation.'}
 $raw=$mail.latest.token
 $preview=Api POST '/company-invitations/validate' @{token=$raw} $null
 if($preview.role -ne 'FLEET_MANAGER') {throw 'Invalid invitation preview.'}
 $employee=Api POST '/auth/register' @{fullName='Phase Two Employee';phone='+251922000003';email='phase2-employee@example.test';password=$password;role='shipper'} $null
 $joined=Api POST '/company-invitations/accept' @{token=$raw;role='CARRIER_OWNER';carrierCompanyId=999999} $employee.token
 $raw=$null;$mail=$null
 if($joined.role -ne 'FLEET_MANAGER' -or $joined.carrierCompanyId -ne $company.id) {throw 'Invitation scope was not enforced.'}
 [void](Api PATCH "$team/members/$($joined.id)/role" @{role='DISPATCHER'} $owner.token)
 [void](Api POST "$team/members/$($joined.id)/deactivate" $null $owner.token)
 [void](Api POST "$team/members/$($joined.id)/activate" $null $owner.token)
 $members=Api GET "$team/members" $null $owner.token
 if($members.totalElements -ne 2) {throw 'Team membership missing.'}
 $forbidden=$false
 try {[void](Api POST "$team/members/$($joined.id)/deactivate" $null $employee.token)}
 catch {if([int]$_.Exception.Response.StatusCode -eq 403) {$forbidden=$true} else {throw 'Unexpected unauthorized-operation response.'}}
 if(-not $forbidden) {throw 'Employee was able to manage company membership.'}
 [void](Api DELETE "$team/members/$($joined.id)" $null $owner.token)
 Write-Output 'HTTP/SMTP capture workflow passed: owner membership, invitation email, public validation, registration, acceptance, role change, deactivate/reactivate/remove, and unauthorized 403.'

 if($LiveEmail) {
  foreach($key in @('SMTP_HOST','SMTP_PORT','SMTP_SECURE','SMTP_USER','SMTP_PASSWORD','EMAIL_FROM','INVITATION_TEST_RECIPIENT')) {
   if([string]::IsNullOrWhiteSpace($settings[$key])) {throw "Live test requires $key in backend-spring-boot/.env."}
  }
  $attemptMarker=Join-Path $backend '../local-run-checks/phase2-live-email-attempted.txt'
  if(Test-Path -LiteralPath $attemptMarker) {throw 'A live invitation was already attempted; refusing another send.'}
  Stop-Process -Id $server.Id;$server.WaitForExit()
  foreach($key in @('SMTP_HOST','SMTP_PORT','SMTP_SECURE','SMTP_USER','SMTP_PASSWORD','EMAIL_FROM')) {
   [Environment]::SetEnvironmentVariable($key,$settings[$key],'Process')
  }
  StartBackend
  [IO.File]::WriteAllText($attemptMarker,'One live invitation attempt initiated; no credentials or recipient stored.')
  [void](Api POST "$team/invitations" @{email=$settings['INVITATION_TEST_RECIPIENT'];role='DISPATCHER'} $owner.token)
  Write-Output 'One live invitation accepted by the configured SMTP relay. Inbox delivery requires recipient confirmation. The temporary invitation expires on test database cleanup.'
 } else {Write-Output 'Real Brevo send not attempted; use -LiveEmail only after configuring INVITATION_TEST_RECIPIENT and SMTP variables.'}
} finally {
 if($server -and -not $server.HasExited) {Stop-Process -Id $server.Id;$server.WaitForExit()}
 if($capture -and -not $capture.HasExited) {Stop-Process -Id $capture.Id;$capture.WaitForExit()}
 if($created -and $testDatabase -match '^carrier_phase2_[a-f0-9]{16}$' -and $testDatabase -ne $sourceDatabase) {
  [void](Sql $sourceDatabase "DROP DATABASE $testDatabase WITH (FORCE)")
  Write-Output 'Removed the isolated Phase 2 verification database.'
 }
 Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}
