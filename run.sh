#!/bin/sh
# Compile and run ThreadSeek (Mac / Linux)
cd "$(dirname "$0")" || exit 1

if ! command -v javac >/dev/null 2>&1; then
    echo "Java JDK not found. Install JDK 17 or newer from https://adoptium.net"
    exit 1
fi

mkdir -p out
find src -name "*.java" > /tmp/threadseek_sources.txt
javac -encoding UTF-8 -cp "lib/mysql-connector-j-26.10.0.jar" -d out @/tmp/threadseek_sources.txt || exit 1

if [ -z "$THREADSEEK_DB_PASSWORD" ]; then
    printf "Enter your MySQL root password (just press ENTER if it has no password): "
    stty -echo 2>/dev/null; read -r THREADSEEK_DB_PASSWORD; stty echo 2>/dev/null; echo
    export THREADSEEK_DB_PASSWORD
fi

java -cp "out:lib/mysql-connector-j-26.10.0.jar" Main
