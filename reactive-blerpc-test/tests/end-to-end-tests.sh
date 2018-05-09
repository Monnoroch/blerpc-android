#!/usr/bin/env bash

set -e

echo "Running $0 $*..."

reactive-blerpc-test/tests/check_generated_service.sh
reactive-blerpc-test/tests/check_generated_service_deprecated.sh
reactive-blerpc-test/tests/check_generated_service_without_javadoc.sh
