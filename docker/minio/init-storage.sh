#!/bin/sh
set -eu

until mc alias set local http://storage:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null 2>&1; do
  sleep 2
done

for bucket in "$STORAGE_RAW_BUCKET" "$STORAGE_PROCESSED_BUCKET"; do
  mc mb --ignore-existing "local/$bucket"
  mc anonymous set none "local/$bucket"
done

cat > /tmp/app-policy.json <<POLICY
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:ListBucket", "s3:ListBucketMultipartUploads", "s3:GetBucketLocation"],
      "Resource": ["arn:aws:s3:::$STORAGE_RAW_BUCKET", "arn:aws:s3:::$STORAGE_PROCESSED_BUCKET"]
    },
    {
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:ListMultipartUploadParts", "s3:AbortMultipartUpload"],
      "Resource": ["arn:aws:s3:::$STORAGE_RAW_BUCKET/*", "arn:aws:s3:::$STORAGE_PROCESSED_BUCKET/*"]
    }
  ]
}
POLICY

mc admin policy create local sinai-app /tmp/app-policy.json
mc admin user add local "$STORAGE_APP_ACCESS_KEY" "$STORAGE_APP_SECRET_KEY" 2>/dev/null || true
mc admin policy attach local sinai-app --user "$STORAGE_APP_ACCESS_KEY" 2>/dev/null || true
