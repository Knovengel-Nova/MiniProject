@echo off
REM ============================================================================
REM Script to start Python PDS Flask API on 127.0.0.1:5005
REM ============================================================================
cd /d "%~dp0..\pds-service"
echo Starting PDS Linear Regression Service on 127.0.0.1:5005...
python app.py
