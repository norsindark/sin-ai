#!/usr/bin/env bash
set -euo pipefail
umask 077

version="${1:?usage: deploy.sh <version>}"
version_pattern='^([0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?|main-[0-9a-f]{7})$'
if [[ ! "$version" =~ $version_pattern ]]; then
  echo "Invalid version '$version' (expected 1.2.3, 1.2.3-rc.1 or main-<sha7>)" >&2
  exit 1
fi

cd "$(dirname "$0")"
if [[ ! -f .env ]]; then
  echo "Missing $(pwd)/.env (copy .env.example and fill it in)" >&2
  exit 1
fi

keep_backups="${KEEP_BACKUPS:-14}"
previous_version="$(sed -n 's/^APP_VERSION=//p' version.env 2>/dev/null || true)"

compose() {
  APP_VERSION="$version" docker compose --env-file .env "$@"
}

show_app_logs() {
  echo "Deploy of $version failed; last app logs:" >&2
  compose logs --tail 100 app >&2 || true
}
trap show_app_logs ERR

echo "Deploying $version (currently: ${previous_version:-none})"
compose pull app

mkdir -p backups
if [[ -n "$(compose ps -q --status running postgres)" ]]; then
  backup_file="backups/sinai-$(date -u +%Y%m%dT%H%M%SZ)-before-$version.sql.gz"
  if ! compose exec -T postgres pg_dump -U sinai -d sinai | gzip > "$backup_file"; then
    rm -f "$backup_file"
    echo "Database backup failed, deploy aborted" >&2
    exit 1
  fi
  echo "Database backup: $backup_file"
else
  echo "Postgres is not running yet, skipping the database backup"
fi
find backups -maxdepth 1 -name 'sinai-*.sql.gz' -type f | sort -r | tail -n +"$((keep_backups + 1))" | xargs -r rm --

compose up -d --wait --wait-timeout 300 app

printf 'APP_VERSION=%s\n' "$version" > version.env
printf '%s deployed %s (previous: %s)\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$version" "${previous_version:-none}" >> deploy-history.log
compose ps
echo "Deployed $version"
