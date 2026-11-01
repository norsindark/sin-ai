#!/usr/bin/env bash
set -euo pipefail

output_dir="${1:?usage: bundle.sh <output-dir>}"
repo_root="$(cd "$(dirname "$0")/.." && pwd)"

mkdir -p "$output_dir/postgres-init"
cp "$repo_root/deploy/compose.yml" "$repo_root/deploy/deploy.sh" "$repo_root/deploy/.env.example" "$output_dir/"
cp "$repo_root/docker/minio/init-storage.sh" "$output_dir/"
cp "$repo_root/docker/postgres/init/"*.sql "$output_dir/postgres-init/"
