@echo off
setlocal enabledelayedexpansion
rem ---------------------------------------------------------------------
rem  Aside - DEVELOPMENT launcher.
rem
rem  This needs a JDK and the JavaFX SDK on THIS machine, and it runs the
rem  classes in .\classes, so it only works for working on the project.
rem
rem  If you just want to PLAY, do not use this file. Download the release
rem  zip instead - it bundles the runtime and needs nothing installed:
rem    https://github.com/ChasePlank/aside/releases
rem
rem  Three things here are load-bearing:
rem
rem  1. A JDK 21 or newer.  The JavaFX 27 jars are class version 69, which
rem     JDK 17 cannot read - it fails with:
rem         InvalidModuleDescriptorException: Unsupported major.minor 69.0
rem     So JAVA_HOME is honoured, then common install folders are searched,
rem     and only then PATH - because bare `java` is often an older JDK.
rem
rem  2. cd /d "%~dp0" - jump to this script's own folder, so `classes`,
rem     `stories`, `art` and `saves` resolve no matter where it is launched
rem     from (including double-clicking it).
rem
rem  3. -Dprism.order=sw - the hardware pipeline renders the scene graph but
rem     never presents it on some machines, so the window is pure white.
rem ---------------------------------------------------------------------

cd /d "%~dp0"

set "JAVA="

rem 0. A JDK shipped INSIDE this package. The full package carries one, so
rem    nobody has to install anything - which is the point of it.
for /f "delims=" %%D in ('dir /b /ad /o-n "%~dp0..\jdk-*" 2^>nul') do (
  if not defined JAVA if exist "%~dp0..\%%D\bin\java.exe" set "JAVA=%~dp0..\%%D\bin\java.exe"
)
if not defined JAVA if exist "%~dp0..\jdk\bin\java.exe" set "JAVA=%~dp0..\jdk\bin\java.exe"

rem 1. An installed JDK elsewhere on the machine.
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"

if not defined JAVA (
  for /f "delims=" %%D in ('dir /b /ad /o-n "C:\Program Files\Java\jdk-*" 2^>nul') do (
    if not defined JAVA if exist "C:\Program Files\Java\%%D\bin\java.exe" set "JAVA=C:\Program Files\Java\%%D\bin\java.exe"
  )
)

if not defined JAVA (
  for /f "delims=" %%J in ('where java 2^>nul') do (
    if not defined JAVA set "JAVA=%%J"
  )
)

if not defined JAVA (
  echo.
  echo   No Java found on this machine.
  echo.
  echo   This file is the DEVELOPMENT launcher and needs a JDK installed.
  echo   To just play, download the release - it bundles everything:
  echo     https://github.com/ChasePlank/aside/releases
  echo.
  pause
  exit /b 1
)

set "JFX="
for /f "delims=" %%D in ('dir /b /ad /o-n "%~dp0..\javafx-sdk-*" 2^>nul') do (
  if not defined JFX if exist "%~dp0..\%%D\lib" set "JFX=%~dp0..\%%D\lib"
)
if not defined JFX (
  for /f "delims=" %%D in ('dir /b /ad /o-n "%USERPROFILE%\Downloads\javafx-sdk-*" 2^>nul') do (
    if not defined JFX if exist "%USERPROFILE%\Downloads\%%D\lib" set "JFX=%USERPROFILE%\Downloads\%%D\lib"
  )
)
if not defined JFX (
  for /f "delims=" %%D in ('dir /b /ad /o-n "%~dp0javafx-sdk-*" 2^>nul') do (
    if not defined JFX if exist "%~dp0%%D\lib" set "JFX=%~dp0%%D\lib"
  )
)

if not defined JFX (
  echo.
  echo   Found Java at:
  echo     %JAVA%
  echo   but no JavaFX SDK next to this project, in Downloads, or in the
  echo   project folder.
  echo.
  echo   Download the JavaFX SDK and unzip it so that the folder
  echo   javafx-sdk-XX sits next to this play.cmd, or in %%USERPROFILE%%\Downloads.
  echo     https://gluonhq.com/products/javafx/
  echo.
  pause
  exit /b 1
)

if not exist "classes\aside\ui\Main.class" (
  echo.
  echo   No compiled classes in .\classes - this is a source checkout.
  echo   Build first, or download the release zip to just play:
  echo     https://github.com/ChasePlank/aside/releases
  echo.
  pause
  exit /b 1
)

echo   java: %JAVA%
echo   jfx : %JFX%
echo.

"%JAVA%" ^
  --module-path "%JFX%" ^
  --add-modules javafx.base,javafx.graphics,javafx.controls,javafx.swing,javafx.media ^
  -Dprism.order=sw ^
  -cp classes aside.ui.Main

if errorlevel 1 (
  echo.
  echo   Aside exited with an error.  Nothing above?  Then it was a crash.
  pause
)
