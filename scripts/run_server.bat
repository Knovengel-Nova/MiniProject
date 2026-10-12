@echo off
REM ============================================================================
REM Script to start LAN Secure Chat Server
REM ============================================================================
cd /d "%~dp0.."
echo Building and running LAN Secure Chat Server...
call mvn -pl chat-server compile exec:java -Dexec.mainClass="com.spit.lanchat.server.ServerMain"
