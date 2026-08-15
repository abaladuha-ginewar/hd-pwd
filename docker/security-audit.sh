#!/usr/bin/env sh
set -eu

if rg -n "println\\(|System\\.out|console\\.log|Log\\.d\\(" shared androidApp desktopApp webApp; then
  echo "敏感代码目录包含未封装的直接日志调用"
  exit 1
fi

if rg -n "secretAccessKey.*toString|recoveryPassword.*println|LocalEnvelopeKey.*println" shared androidApp desktopApp webApp; then
  echo "检测到可能的敏感信息输出"
  exit 1
fi

echo "Static sensitive-data logging audit passed"
