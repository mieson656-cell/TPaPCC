#!/bin/sh
set -e
if [ -x "$HOME/.gradle/wrapper/dists/gradle-9.7.1-bin" ]; then :; fi
exec gradle "$@"
