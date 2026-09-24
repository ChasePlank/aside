@echo off
setlocal
rem ---------------------------------------------------------------------
rem  Aside launcher
rem
rem  Two things here are load-bearing and were both wrong the first time:
rem
rem  1. The FULL PATH to java.  Bare `java` on this machine resolves to
rem     JDK 17 (Eclipse Adoptium), but everything here is built with
rem     JDK 27 and the JavaFX 27 jars are class version 69.  JDK 17
rem     cannot read them, and fails with:
rem         InvalidModuleDescriptorException: Unsupported major.minor
rem         version 69.0
rem
rem  2. cd /d "%~dp0" -- jump to this script's own folder, so `classes`,
rem     `stories`, `art` and `saves` resolve no matter where the script
rem     is launched from (including double-clicking it).
rem
rem  -Dprism.order=sw is also required: the hardware pipeline renders the
rem  scene graph but never presents it, so the window is pure white.
rem ---------------------------------------------------------------------

set "JAVA=C:\Program Files\Java\jdk-27\bin\java.exe"
set "JFX=C:\Users\chase\Downloads\javafx-sdk-27\lib"

cd /d "%~dp0"

if not exist "%JAVA%" (
  echo.
  echo   Could not find JDK 27 at:
  echo     %JAVA%
  echo   Edit JAVA= near the top of this file to point at your JDK 27.
  echo.
  pause
  exit /b 1
)
if not exist "%JFX%" (
  echo.
  echo   Could not find the JavaFX 27 SDK at:
  echo     %JFX%
  echo   Edit JFX= near the top of this file.
  echo.
  pause
  exit /b 1
)

"%JAVA%" ^
  --module-path "%JFX%" ^
  --add-modules javafx.base,javafx.graphics,javafx.controls,javafx.swing ^
  -Dprism.order=sw ^
  -Daside.root=. ^
  -cp classes aside.ui.Main

if errorlevel 1 (
  echo.
  echo   Aside exited with an error.  Nothing above?  Then it was a crash.
  pause
)
