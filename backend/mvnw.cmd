@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM
@REM Optional ENV vars
@REM   MVNW_REPOURL - repo url base for downloading maven distribution
@REM   MVNW_USERNAME/MVNW_PASSWORD - user and password for downloading maven
@REM   MVNW_VERBOSE - true: enable verbose log; others: silence the output
@REM ----------------------------------------------------------------------------

@REM Begin all REM://'m'm'm

@echo off
@REM set title of command window
title %0
@REM enable echoing by setting MVNW_VERBOSE to anything
@setlocal

set WRAPPER_DIR=%~dp0.mvn\wrapper
set PROPERTIES_PATH=%WRAPPER_DIR%\maven-wrapper.properties
set MAVEN_PROJECTBASEDIR=%~dp0

@REM ==== START VALIDATION ====
if not "%JAVA_HOME%"=="" goto OkJHome
for %%i in (java.exe) do set "JAVACMD=%%~$PATH:i"
goto checkJCmd

:OkJHome
set "JAVACMD=%JAVA_HOME%\bin\java.exe"

:checkJCmd
if exist "%JAVACMD%" goto chkMHome

echo The JAVA_HOME environment variable is not defined correctly, >&2
echo this environment variable is needed to run this program. >&2
goto error

:chkMHome
@REM Determine Maven home from wrapper properties
set MAVEN_HOME=
for /f "usebackq tokens=1,2 delims==" %%a in ("%PROPERTIES_PATH%") do (
  if "%%a"=="distributionUrl" set WRAPPER_URL=%%b
)

if "%WRAPPER_URL%"=="" (
  echo Cannot read distributionUrl from %PROPERTIES_PATH% >&2
  goto error
)

@REM Extract distribution name for MAVEN_HOME
for %%i in ("%WRAPPER_URL%") do set WRAPPER_FILE=%%~nxi
set WRAPPER_FILE=%WRAPPER_FILE:-bin=%
for %%i in ("%WRAPPER_FILE%") do set WRAPPER_NAME=%%~ni
set MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\%WRAPPER_NAME%

if exist "%MAVEN_HOME%" goto runMaven

@REM Download Maven
echo Downloading from: %WRAPPER_URL%
set DOWNLOAD_DIR=%TEMP%\mvnw-%RANDOM%
mkdir "%DOWNLOAD_DIR%" 2>nul

powershell -Command "&{"^
  "$webclient = new-object System.Net.WebClient;"^
  "if (-not ([string]::IsNullOrEmpty('%MVNW_USERNAME%') -and [string]::IsNullOrEmpty('%MVNW_PASSWORD%'))) {"^
  "$webclient.Credentials = new-object System.Net.NetworkCredential('%MVNW_USERNAME%', '%MVNW_PASSWORD%');"^
  "}"^
  "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $webclient.DownloadFile('%WRAPPER_URL%', '%DOWNLOAD_DIR%\maven.zip')"^
  "}" || (
    echo Failed to download %WRAPPER_URL% >&2
    goto error
)

@REM Unzip
powershell -Command "Expand-Archive -Path '%DOWNLOAD_DIR%\maven.zip' -DestinationPath '%DOWNLOAD_DIR%'" || (
  echo Failed to unzip maven distribution >&2
  goto error
)

@REM Move to MAVEN_HOME
for /d %%G in ("%DOWNLOAD_DIR%\apache-maven-*") do (
  move "%%G" "%MAVEN_HOME%" >nul
)
rmdir /s /q "%DOWNLOAD_DIR%" 2>nul

:runMaven
set MAVEN_CMD=%MAVEN_HOME%\bin\mvn.cmd
if not exist "%MAVEN_CMD%" set MAVEN_CMD=%MAVEN_HOME%\bin\mvn

"%MAVEN_CMD%" %*
if ERRORLEVEL 1 goto error
goto end

:error
set ERROR_CODE=1

:end
@endlocal & set ERROR_CODE=%ERROR_CODE%

cmd /C exit /B %ERROR_CODE%
