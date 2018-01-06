#!/bin/bash

echo "Running $0..."

# find all Dockerfile files in repository
list=$(git ls-files | grep "/Dockerfile$")

exitcode=0
for file in $list; do
    # DL4000 is deprecated rule
    # DL4001 is very hard to satisfy and also just extremely inconvenient.
    out=$(linters/bin/hadolint --ignore DL4000 --ignore DL4001 "$file")
    if [ $? -ne 0 ]; then
        echo "hadolint failed for $file."
        echo "$out"
        exitcode=1
    fi
done

exit $exitcode
