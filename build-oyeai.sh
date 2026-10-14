#!/bin/bash
export JAVA_HOME=/opt/jdk17 ANDROID_HOME=/opt/android-sdk GRADLE_USER_HOME=/opt/gradle-home PATH=/opt/jdk17/bin:/opt/gradle-8.2/bin:$PATH
cd /home/user/oye-ai && gradle "$@" --no-daemon
echo "BUILD_EXIT=$?"
