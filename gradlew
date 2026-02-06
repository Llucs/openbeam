#!/usr/bin/env sh
# Minimal Gradle wrapper for demonstration purposes. If a local Gradle installation is
# available on the PATH, this script will delegate to it. Otherwise it will print an
# error. In a real project the Gradle wrapper JAR and properties file should be
# committed to version control.

if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
else
  echo "Gradle is not installed. Please install Gradle or replace this wrapper with a proper one." >&2
  exit 1
fi