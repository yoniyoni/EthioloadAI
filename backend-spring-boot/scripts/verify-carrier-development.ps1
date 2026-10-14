[CmdletBinding()]
param([string]$PsqlPath='C:\Program Files\PostgreSQL\18\bin\psql.exe',[int]$Port=18089)
# Read-only data audit and normal executable startup against the configured development DB.
$ErrorActionPreference='Stop'
$backend=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $backend
$settings=@{}
foreach($line in [IO.File]::ReadAllLines((Join-Path $backend '.env'))) {
 if($line.Trim() -match '^(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
  $settings[$Matches[1]]=$Matches[2].Trim().Trim('"').Trim("'")
 }
}
if($settings['SPRING_DATASOURCE_URL'] -notmatch '^jdbc:postgresql://([^/:]+):(\d+)/([^?]+)') {throw 'Expected PostgreSQL configuration'}
$pgHostName=$Matches[1];$pgPortNumber=$Matches[2];$database=$Matches[3]
$env:PGPASSWORD=$settings['SPRING_DATASOURCE_PASSWORD']
$checks=0;$server=$null
function Sql([string]$query) {
 $result=& $PsqlPath -X -w -h $pgHostName -p $pgPortNumber -U $settings['SPRING_DATASOURCE_USERNAME'] -d $database -v ON_ERROR_STOP=1 -At -c $query
 if($LASTEXITCODE -ne 0){throw 'Development SQL audit failed'}
 return $result
}
function Zero([string]$label,[string]$query) {
 if((Sql $query) -ne '0'){throw "Integrity failure: $label"}
 $script:checks++;Write-Output "PASS: $label"
}
try {
 Write-Output ('PostgreSQL '+(Sql 'SHOW server_version'))
 if((Sql "SELECT count(*) FROM flyway_schema_history WHERE version IN ('57','58','59','60') AND success") -ne '4') {
  throw 'Development DB must already have V57-V60 before this read-only verification.'
 }
 $checks++
 Zero 'No unsuccessful migrations' 'SELECT count(*) FROM flyway_schema_history WHERE NOT success'
 Zero 'No duplicate migration versions' 'SELECT count(*) FROM (SELECT version FROM flyway_schema_history WHERE version IS NOT NULL GROUP BY version HAVING count(*)>1) x'
 Zero 'Company owner references' 'SELECT count(*) FROM carrier_companies c LEFT JOIN users u ON u.id=c.owner_id WHERE u.id IS NULL'
 Zero 'Document company references' 'SELECT count(*) FROM carrier_company_documents d LEFT JOIN carrier_companies c ON c.id=d.carrier_company_id WHERE c.id IS NULL'
 Zero 'Membership references' 'SELECT count(*) FROM company_memberships m LEFT JOIN users u ON u.id=m.user_id LEFT JOIN carrier_companies c ON c.id=m.carrier_company_id WHERE u.id IS NULL OR c.id IS NULL'
 Zero 'Invitation references' 'SELECT count(*) FROM company_invitations i LEFT JOIN carrier_companies c ON c.id=i.carrier_company_id LEFT JOIN users u ON u.id=i.invited_by WHERE c.id IS NULL OR u.id IS NULL'
 Zero 'Unique company user memberships' 'SELECT count(*) FROM (SELECT carrier_company_id,user_id FROM company_memberships GROUP BY 1,2 HAVING count(*)>1) x'
 Zero 'Unique token hashes' 'SELECT count(*) FROM (SELECT token_hash FROM company_invitations GROUP BY 1 HAVING count(*)>1) x'
 Zero 'Invitation acceptance metadata' "SELECT count(*) FROM company_invitations WHERE (status='ACCEPTED')<>(accepted_at IS NOT NULL AND accepted_by IS NOT NULL)"
 Zero 'Invitation revocation metadata' "SELECT count(*) FROM company_invitations WHERE (status='REVOKED')<>(revoked_at IS NOT NULL AND revoked_by IS NOT NULL)"
 Zero 'Exactly one active owner membership per company' "SELECT count(*) FROM carrier_companies c WHERE (SELECT count(*) FROM company_memberships m WHERE m.carrier_company_id=c.id AND m.user_id=c.owner_id AND m.role='CARRIER_OWNER' AND m.status='ACTIVE')<>1"
 Zero 'Owner systems agree' "SELECT count(*) FROM company_memberships m JOIN carrier_companies c ON c.id=m.carrier_company_id WHERE m.role='CARRIER_OWNER' AND m.user_id<>c.owner_id"
 Zero 'No unvalidated phase constraints' "SELECT count(*) FROM pg_constraint WHERE conrelid IN ('carrier_companies'::regclass,'carrier_company_documents'::regclass,'company_memberships'::regclass,'company_invitations'::regclass) AND NOT convalidated"
 Sql "SELECT conrelid::regclass::text||':'||contype::text||':'||count(*) FROM pg_constraint WHERE conrelid IN ('carrier_companies'::regclass,'carrier_company_documents'::regclass,'company_memberships'::regclass,'company_invitations'::regclass) GROUP BY conrelid,contype ORDER BY conrelid,contype"
 Sql "SELECT tablename||':indexes='||count(*) FROM pg_indexes WHERE schemaname='public' AND tablename IN ('carrier_companies','carrier_company_documents','company_memberships','company_invitations') GROUP BY tablename ORDER BY tablename"
 foreach($key in $settings.Keys){[Environment]::SetEnvironmentVariable($key,$settings[$key],'Process')}
 $env:SERVER_PORT="$Port";$env:SPRING_PROFILES_ACTIVE='dev'
 $env:SPRING_JPA_HIBERNATE_DDL_AUTO='validate';$env:SPRING_JPA_SHOW_SQL='false'
 $env:LOGGING_LEVEL_ORG_HIBERNATE_SQL='WARN';$env:LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY='WARN'
 $jar=Join-Path $backend 'target/ethioloadai-backend-1.0.0.jar'
 $server=Start-Process (Get-Command java.exe).Source -ArgumentList @('-jar',('"'+$jar+'"')) -WorkingDirectory $backend -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $backend 'full-verification-development-startup.log') -RedirectStandardError (Join-Path $backend 'full-verification-development-startup-error.log')
 $healthy=$false
 for($attempt=0;$attempt -lt 90;$attempt++) {
  if($server.HasExited){throw 'Development startup exited'}
  try {if((Invoke-RestMethod "http://127.0.0.1:$Port/api/actuator/health").status -eq 'UP'){$healthy=$true;break}}catch{}
  Start-Sleep -Seconds 1
 }
 if(-not $healthy){throw 'Development health failed'}
 Stop-Process -Id $server.Id;$server.WaitForExit()
 $log=[IO.File]::ReadAllText((Join-Path $backend 'full-verification-development-startup.log'))
 if($log -notmatch 'Successfully validated' -or $log -notmatch 'No migration necessary'){throw 'Expected validation without development migration'}
 Write-Output "PASS development audit: $checks database checks; executable dev startup; Flyway validation; Hibernate schema validation; health UP. No test records created."
} finally {
 if($server -and -not $server.HasExited){Stop-Process -Id $server.Id;$server.WaitForExit()}
 Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}
