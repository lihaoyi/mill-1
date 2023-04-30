#!/usr/bin/env sh

set -eux

# Starting from scratch...
git stash -u
git stash -a

# Build Mill
./mill -i dev.assembly

# Clean up
git stash -a -m "preserve mill-release" -- target/mill-release
git stash -u
git stash -a
git stash pop "$(git stash list | grep "preserve mill-release" | head -n1 | sed -E 's/([^:]+):.*/\1/')"


MILL_RUNNER=out/dev/assembly.dest/mill ci/test-example.sh
