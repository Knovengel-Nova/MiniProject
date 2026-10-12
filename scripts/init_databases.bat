@echo off
REM ============================================================================
REM Script to initialize server and client PostgreSQL databases
REM ============================================================================
set PGPASSWORD=postgres
set PSQL="C:\Program Files\PostgreSQL\18\bin\psql.exe"

echo Creating server database: lan_chat_server...
%PSQL% -U postgres -h 127.0.0.1 -c "DROP DATABASE IF EXISTS lan_chat_server;"
%PSQL% -U postgres -h 127.0.0.1 -c "CREATE DATABASE lan_chat_server;"
%PSQL% -U postgres -h 127.0.0.1 -d lan_chat_server -f "%~dp0..\database\server_schema.sql"

echo Creating test client database: lan_chat_client_test...
%PSQL% -U postgres -h 127.0.0.1 -c "DROP DATABASE IF EXISTS lan_chat_client_test;"
%PSQL% -U postgres -h 127.0.0.1 -c "CREATE DATABASE lan_chat_client_test;"
%PSQL% -U postgres -h 127.0.0.1 -d lan_chat_client_test -f "%~dp0..\database\client_schema.sql"

echo Database initialization complete!
