# PowerShell script to generate TLS Keystore and Truststore
$certDir = Join-Path $PSScriptRoot "..\certs"
if (-not (Test-Path $certDir)) { New-Item -ItemType Directory -Path $certDir | Out-Null }

$serverKs = Join-Path $certDir "server-keystore.p12"
$serverCert = Join-Path $certDir "server-cert.crt"
$clientTs = Join-Path $certDir "client-truststore.p12"

if (Test-Path $serverKs) { Remove-Item $serverKs }
if (Test-Path $serverCert) { Remove-Item $serverCert }
if (Test-Path $clientTs) { Remove-Item $clientTs }

Write-Host "Generating server keystore..."
keytool -genkeypair -alias lanchat -keyalg RSA -keysize 2048 -validity 365 `
  -keystore $serverKs -storetype PKCS12 `
  -storepass changeit -keypass changeit `
  -dname "CN=localhost, OU=MiniProject, O=SPIT, L=Mumbai, ST=Maharashtra, C=IN"

Write-Host "Exporting server certificate..."
keytool -exportcert -alias lanchat -keystore $serverKs -storepass changeit -file $serverCert

Write-Host "Importing into client truststore..."
keytool -importcert -noprompt -alias lanchat -file $serverCert -keystore $clientTs -storetype PKCS12 -storepass changeit

Write-Host "TLS certificates generated successfully at $certDir"
