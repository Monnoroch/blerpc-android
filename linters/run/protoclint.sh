#!/bin/bash

echo "Running $0..."

protoc_imports=()
for path in $(echo "${PROTOC_INCLUDES}" | tr ":" "\n"); do
    protoc_imports+=(-I ${path})
done

# find all *.proto files in repository
list=$(git ls-files | grep "\.proto$")

exitcode=0
for file in $list; do
    out=$(protoc "${protoc_imports[@]}" --lint_out=. "$file")
    if [ $? -ne 0 ]; then
        echo "protoc failed for $file."
        echo "$out"
        exitcode=1
    fi
done

exit $exitcode
