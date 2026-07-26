#!/bin/bash
set -e

JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-24.jdk/Contents/Home
export JAVA_HOME

JD="${1:-jd.txt}"
OUTPUT="${2:-$HOME/tailored_resume.docx}"

echo "Building..."
JAVA_HOME=$JAVA_HOME mvn -q package -DskipTests

echo "Running agent: JD=$JD  OUTPUT=$OUTPUT"
JAVA_HOME=$JAVA_HOME "$JAVA_HOME/bin/java" \
  -cp "target/ResumeAgent-1.0-SNAPSHOT.jar:target/dependency/*" \
  org.example.Main "$JD" "$OUTPUT"
