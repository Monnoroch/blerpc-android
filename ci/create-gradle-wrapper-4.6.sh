#!/usr/bin/env bash

mkdir tmp-project
cd tmp-project
gradle init --type basic --dsl groovy --project-name temp
gradle wrapper --gradle-version 4.6 --distribution-type all
cd ..
mv tmp-project/gradlew ./
mv tmp-project/gradlew.bat ./
mv tmp-project/gradle ./
