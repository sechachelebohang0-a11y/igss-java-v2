@echo off
rem Compile and start the system. Needs Java 17 or newer and internet the first time (to fetch the SQLite driver).
set M=https://repo1.maven.org/maven2
if not exist lib mkdir lib
if not exist out mkdir out
if not exist lib\sqlite-jdbc.jar powershell -Command "Invoke-WebRequest %M%/org/xerial/sqlite-jdbc/3.46.1.0/sqlite-jdbc-3.46.1.0.jar -OutFile lib\sqlite-jdbc.jar"
if not exist lib\slf4j-api.jar powershell -Command "Invoke-WebRequest %M%/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar -OutFile lib\slf4j-api.jar"
if not exist lib\slf4j-nop.jar powershell -Command "Invoke-WebRequest %M%/org/slf4j/slf4j-nop/1.7.36/slf4j-nop-1.7.36.jar -OutFile lib\slf4j-nop.jar"
dir /s /b src\*.java > sources.txt
javac --release 17 -d out @sources.txt
if errorlevel 1 exit /b 1
java -cp "out;lib\*" lesotho.igss.Main 8080
