#!/bin/bash

echo "Running $0..."

# find all *.yml, *.yaml files in repository
list=$(git ls-files | grep "\.\(yaml\|yml\)$")

exitcode=0
for file in $list; do
    DIR=$(dirname "$0")
    DIR=$(dirname "$DIR")
    out=$(yamllint -c "$DIR"/configs/yamllint.yml "$file")
    if [ $? -ne 0 ]; then
        echo "yamllint failed for $file."
        echo "$out"
        exitcode=1
    fi
done

exit $exitcode
