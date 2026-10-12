@echo off
REM ============================================================================
REM Script to generate TLS Keystore and Truststore for LAN Secure Chat
REM ============================================================================

set CERT_DIR=%~dp0..\certs
if not exist "%CERT_DIR%" mkdir "%CERT_DIR%"

echo Generating Server Keystore...
keytool -genkeypair -alias lanchat -keyalg RSA -keysize 2048 -validity 365 ^
  -keystore "%CERT_DIR%\server-keystore.p12" -storetype PKCS12 ^
  -storepass changeit -keypass changeit ^
  -dname "CN=localhost, OU=MiniProject, O=SPIT, L=Mumbai, ST=Maharashtra, C=IN"

echo Exporting Server Public Certificate...
keytool -exportcert -alias lanchat ^
  -keystore "%CERT_DIR%\server-keystore.p12" -storepass changeit ^
  -file "%CERT_DIR%\server-cert.crt"

echo Importing into Client Truststore...
keytool -importcert -noprompt -alias lanchat ^
  -file "%CERT_DIR%\server-cert.crt" ^
  -keystore "%CERT_DIR%\client-truststore.p12" -storetype PKCS12 ^
  -storepass changeit

echo TLS Certificates Generated Successfully in %CERT_DIR%
