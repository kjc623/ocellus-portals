@echo off
setlocal
rem Ocellus Portals: installs the plugin jar and starts RuneLite in developer mode.
rem Developer mode is the only way RuneLite loads a plugin that is not on the Plugin Hub.
rem It runs the normal RuneLite client files from %USERPROFILE%\.runelite\repository2,
rem so keep using the regular launcher now and then to receive client updates.

set "RL=%USERPROFILE%\.runelite"
set "REPO=%RL%\repository2"
set "JAR=%~dp0ocellus-portals-1.0.0.jar"

if not exist "%REPO%\" (
    echo RuneLite's client files were not found in "%REPO%".
    echo Start RuneLite normally once so it downloads them, then run this again.
    pause
    exit /b 1
)

rem Install (or refresh) the plugin jar when it sits next to this script.
if exist "%JAR%" (
    if not exist "%RL%\sideloaded-plugins\" mkdir "%RL%\sideloaded-plugins"
    copy /Y "%JAR%" "%RL%\sideloaded-plugins\" >nul
    echo Installed ocellus-portals-1.0.0.jar into "%RL%\sideloaded-plugins".
) else (
    if not exist "%RL%\sideloaded-plugins\ocellus-portals-1.0.0.jar" (
        echo Could not find ocellus-portals-1.0.0.jar next to this script or in sideloaded-plugins.
        pause
        exit /b 1
    )
)

rem The launcher keeps the previous client version too, so pick the newest client jar
rem (most recently downloaded) and only put that version's RuneLite jars on the classpath.
set "CLIENTJAR="
for /f "delims=" %%f in ('dir /b /o-d "%REPO%\client-*.jar"') do if not defined CLIENTJAR set "CLIENTJAR=%%f"
if not defined CLIENTJAR (
    echo No client-*.jar found in "%REPO%". Start RuneLite normally once, then run this again.
    pause
    exit /b 1
)
set "VER=%CLIENTJAR:client-=%"
set "VER=%VER:.jar=%"
echo Using RuneLite client %VER%.

set "CP="
for %%f in ("%REPO%\*.jar") do call :addjar "%%~nxf"
set "CP=%CP:~1%"

rem Prefer the Java runtime that ships with RuneLite; fall back to one on PATH.
set "JAVA=%LOCALAPPDATA%\RuneLite\jre\bin\java.exe"
if not exist "%JAVA%" set "JAVA=java"

echo Starting RuneLite in developer mode...
"%JAVA%" -ea -XX:+DisableAttachMechanism -Xmx768m -Xss2m -XX:CompileThreshold=1500 ^
  --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED ^
  -cp "%CP%" net.runelite.client.RuneLite --developer-mode %*
if errorlevel 1 (
    echo.
    echo RuneLite exited with an error. If Java was not found, install RuneLite's launcher
    echo or a Java 11+ runtime, or open the project in IntelliJ instead (see README.md).
    pause
)
endlocal
exit /b 0

:addjar
set "N=%~1"
rem Versioned RuneLite artifacts: keep only the newest version. Everything else: keep.
echo %N%| findstr /b /c:"client-" /c:"injected-client-" /c:"runelite-api-" >nul
if errorlevel 1 (
    set "CP=%CP%;%REPO%\%N%"
    goto :eof
)
echo %N%| findstr /c:"-%VER%" >nul
if not errorlevel 1 set "CP=%CP%;%REPO%\%N%"
goto :eof
