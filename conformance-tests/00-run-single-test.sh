#!/bin/bash

echo "Test will only pass if it is run at first without as a single test."
./gradlew clean test --tests *EmsInterfacingTests.commandsAreForwardedJustOnTime
