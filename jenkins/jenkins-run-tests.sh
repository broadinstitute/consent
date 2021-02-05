#!/bin/bash
set -e

# This script is specific to DSP's jenkins CI server.
# Although this script lives in a `jenkins` directory, it is intended to be run from `automation`.
# It is here because it is only intended to be run from a jenkins job and not by developers.

TEST_IMAGE=automation-consent

mkdir -p target

# Build Test Image
docker build -f Dockerfile -t ${TEST_IMAGE} .

# Run Tests
docker run -v "${PWD}/target":/app/target ${TEST_IMAGE}
TEST_EXIT_CODE=$?

# Parse Tests
docker run -v "${PWD}/scripts":/working -v "${PWD}/target":/working/target -w /working broadinstitute/dsp-toolbox python interpret_results.py

# exit with exit code of test script
exit $TEST_EXIT_CODE