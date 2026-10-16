#!/bin/bash
# Local build helper. Set JAVA_HOME, ANDROID_HOME and GRADLE_USER_HOME in your environment to override the defaults.
export JAVA_HOME="${JAVA_HOME:-/opt/jdk17}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/opt/gradle-home}"
export PATH="$JAVA_HOME/bin:/opt/gradle-8.2/bin:$PATH"
cd "$(dirname "$0")" || exit 1
gradle "$@" --no-daemon
code=$?
echo "BUILD_EXIT=$code"
exit $code
