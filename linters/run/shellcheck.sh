#!/bin/bash

echo "Running $0..."

# find all *.sh files in repository
list=$(git ls-files | grep "\.sh$")

exitcode=0
for file in $list; do
    out=$(shellcheck "$file")
    if [ $? -ne 0 ]; then
        echo "shellcheck failed for $file."
        echo "$out"
        exitcode=1
    fi
done

exit $exitcode
