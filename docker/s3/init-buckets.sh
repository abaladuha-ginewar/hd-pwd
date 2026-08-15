#!/usr/bin/env sh
set -eu

mc alias set local http://silo:9000 "$S3_ACCESS_KEY" "$S3_SECRET_KEY"

for bucket in hdpwd-s3-a hdpwd-s3-b hdpwd-s3-c; do
  mc mb --ignore-existing "local/$bucket"
done

mc ls local
