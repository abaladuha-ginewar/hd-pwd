#!/usr/bin/env sh
set -eu

mc alias set local http://silo:9000 "$S3_ACCESS_KEY" "$S3_SECRET_KEY"

for bucket in hdpwd-s3-a hdpwd-s3-b hdpwd-s3-c; do
  printf 'hd-pwd-silo-smoke:%s\n' "$bucket" | mc pipe "local/$bucket/.hd-pwd-smoke"
  mc stat "local/$bucket/.hd-pwd-smoke"
done

for bucket in hdpwd-s3-a hdpwd-s3-b hdpwd-s3-c; do
  mc rm "local/$bucket/.hd-pwd-smoke"
done

echo "Silo multi-bucket smoke test passed"
