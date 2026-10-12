@echo off
REM ============================================================================
REM Script to start LAN Secure Chat Swing Desktop Client
REM ============================================================================
cd /d "%~dp0.."
echo Building and running LAN Secure Chat Client...
call mvn -pl chat-client compile exec:java -Dexec.mainClass="com.spit.lanchat.client.ClientMain"
