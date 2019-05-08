 #!/usr/bin/env bash

set -e

echo "Running $0 $*..."

swift-reactive-blerpc-test/tests/check_generated_service.sh
