#!/usr/bin/env bash

GRADLE_HOME=/home/travis/gradle
export PATH=${GRADLE_HOME}:$PATH

mkdir -p /tmp/work
tmp_gradle=/tmp/work/gradle.zip
wget --output-document=${tmp_gradle} --quiet https://services.gradle.org/distributions/gradle-4.6-all.zip
echo "a5c1815080f22e3b3437c32fd6c50758  ${tmp_gradle}" | md5sum -c
unzip -q ${tmp_gradle} -d /opt/
sudo ln -s /opt/gradle-4.6/ "${GRADLE_HOME}"
rm -rf /tmp/work