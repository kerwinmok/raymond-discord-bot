@echo off
setlocal

set "MVN_VERSION=3.9.6"
set "BASE_DIR=%~dp0"
set "MVN_DIR=%BASE_DIR%.mvn\apache-maven-%MVN_VERSION%"
set "MVN_CMD=%MVN_DIR%\bin\mvn.cmd"
set "ZIP_PATH=%BASE_DIR%.mvn\apache-maven-%MVN_VERSION%-bin.zip"
set "DOWNLOAD_URL=https://archive.apache.org/dist/maven/maven-3/%MVN_VERSION%/binaries/apache-maven-%MVN_VERSION%-bin.zip"

if not exist "%MVN_CMD%" (
    if not exist "%BASE_DIR%.mvn" mkdir "%BASE_DIR%.mvn"
    echo Downloading Maven %MVN_VERSION%...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; Invoke-WebRequest '%DOWNLOAD_URL%' -OutFile '%ZIP_PATH%'; Expand-Archive '%ZIP_PATH%' -DestinationPath '%BASE_DIR%.mvn' -Force"
    if errorlevel 1 (
        echo Failed to download Maven.
        exit /b 1
    )
)

call "%MVN_CMD%" %*
