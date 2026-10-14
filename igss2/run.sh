#!/bin/sh
# Compile and start the system. Needs Java 17 or newer and an internet connection the first time (to fetch the SQLite driver).
M=https://repo1.maven.org/maven2
mkdir -p lib out
[ -f lib/sqlite-jdbc.jar ] || curl -L -o lib/sqlite-jdbc.jar $M/org/xerial/sqlite-jdbc/3.46.1.0/sqlite-jdbc-3.46.1.0.jar
[ -f lib/slf4j-api.jar ] || curl -L -o lib/slf4j-api.jar $M/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar
[ -f lib/slf4j-nop.jar ] || curl -L -o lib/slf4j-nop.jar $M/org/slf4j/slf4j-nop/1.7.36/slf4j-nop-1.7.36.jar
javac --release 17 -d out $(find src -name '*.java') && java -cp "out:lib/*" lesotho.igss.Main 8080
