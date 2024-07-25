#!/bin/bash

read -p "Please start fledge docker and hedera docker and press return" ignore_return_value

echo "Running all EmsInterfacingTests. The default ordering of the test framework (junit) will cause the test 'commandsAreForwardedJustOnTime' to fail."

./gradlew clean test --tests *EmsInterfacingTests*

