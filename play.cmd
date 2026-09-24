@echo off
REM Launch Aside.
REM
REM -Dprism.order=sw is NOT optional on this machine. JavaFX's
REM hardware (Direct3D) pipeline renders the scene graph correctly but
REM never presents it to the screen, so the window shows pure white.
REM The scene is fine -- a canvas snapshot proves it -- the pixels just
REM never reach the display. Software rendering fixes it.
REM
REM If the window is ever blank again, this flag is the first thing to
REM check.

set JFX=C:\Users\chase\Downloads\javafx-sdk-27\lib
java ^
  --module-path "%JFX%" ^
  --add-modules javafx.base,javafx.graphics,javafx.controls,javafx.swing ^
  -Dprism.order=sw ^
  -Daside.root=. ^
  -cp classes aside.ui.Main

pause
