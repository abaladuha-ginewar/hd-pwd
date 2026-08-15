#!/usr/bin/env sh
set -eu

if [ "$#" -eq 0 ]; then
  set -- :shared:desktopTest
fi

exec gradle --no-daemon --stacktrace "$@"
