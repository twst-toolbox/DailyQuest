@echo off
rem ?????? gradle????? gradle-wrapper.jar
where gradle >nul 2>nul
if %ERRORLEVEL% equ 0 (
    gradle %*
) else (
    echo Gradle not found in PATH. Please install Gradle 8.7+ or use GitHub Actions.
    echo Download: https://gradle.org/install/
    exit /b 1
)
