#!/usr/bin/env sh

# Gradle wrapper script (简化版，优先使用系统 gradle，否则提示)
if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
else
    echo "Gradle not found in PATH. Please install Gradle 8.7+ or use GitHub Actions."
    echo "Download: https://gradle.org/install/"
    exit 1
fi
