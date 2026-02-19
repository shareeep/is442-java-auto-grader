@echo off
REM Combined script to generate and fix realistic student submissions
REM This Windows batch file calls the bash script (requires Git Bash or WSL)

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

echo ================================================================
echo   IS442 Auto-Grader: Test Submissions Setup
echo ================================================================
echo.
echo This will generate realistic student submissions with common anomalies.
echo.
echo Note: This requires bash (Git Bash or WSL) to run the script.
echo Press Ctrl+C to cancel, or any key to continue...
pause >nul

REM Run the bash script
bash "%SCRIPT_DIR%\setup-test-submissions.sh"

if errorlevel 1 (
    echo.
    echo Error: Failed to complete setup
    pause
    exit /b 1
)

echo.
pause
