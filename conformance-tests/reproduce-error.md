# Error description

The fledge south plugin will stop publishing command messages (on topic "fledge/south-command"), the framework seems to work only partially as new schedules are published (on topic "fledge/south-schedule").

# How to reporoduce

- delete all existing FLEDGE docker images
- recreate FLEDGE docker image
- start FLEDGE docker image
- start hedera docker image
- run 01-reproduce-error.sh
--> 5 tests will complete, 1 will fail: EmsInterfacingTests > commandsAreForwardedJustOnTime()

# Additional scripts
The script 00-run-single-test.sh runs only the test "EmsInterfacingTests > commandsAreForwardedJustOnTime()", which will pass on fresh builds but fail if 01-reproduce-error.sh was run just before it.

Stragely, the FLEDGE docker seems to recover after a certain time. If t00-run-single-test.sh is run ~30 min after 01-reproduce-error.sh, it passes again.
