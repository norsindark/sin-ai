# Deploy to a VPS

The `Deploy` workflow (`.github/workflows/deploy.yml`) deploys one image version of the backend to a single VPS over SSH.

- Server directory: `/opt/sin-ai` (compose project `sin-ai-prod`, separate from the local dev stack `sin-ai`).
- Files from this folder are uploaded on every deploy: `compose.yml`, `deploy.sh`, `.env.example`, plus `docker/minio/init-storage.sh` and `docker/postgres/init/*.sql` (see `bundle.sh`).
- Files that stay on the server and are never uploaded: `.env`, `version.env`, `deploy-history.log`, `backups/`.

## One-time server setup

1. Install Docker Engine with the compose plugin.
2. Create a deploy user that can run Docker and owns the deploy directory:
   ```sh
   sudo adduser --disabled-password deploy
   sudo usermod -aG docker deploy
   sudo install -d -o deploy -g deploy /opt/sin-ai
   ```
3. On your own machine, create a dedicated key pair (no passphrase): `ssh-keygen -t ed25519 -f sin-ai-deploy -N "" -C "github-actions-deploy"`. Then, on the server as an admin user, install the public key for `deploy` (it has no password, so `ssh-copy-id` cannot log in):
   ```sh
   sudo install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
   echo "<content of sin-ai-deploy.pub>" | sudo tee -a /home/deploy/.ssh/authorized_keys
   sudo chmod 600 /home/deploy/.ssh/authorized_keys
   sudo chown deploy:deploy /home/deploy/.ssh/authorized_keys
   ```
   Check it from your machine: `ssh -i sin-ai-deploy deploy@<vps-host> docker ps`.
4. Record the server host key: `ssh-keyscan -t ed25519 <vps-host>`. Use exactly the same host string as `VPS_HOST` (IP or DNS name). Compare the fingerprint (`ssh-keygen -lf -` on the scanned line) with `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub` run on the server.
5. Create `/opt/sin-ai/.env` from `.env.example` with real values, then `chmod 600 /opt/sin-ai/.env`.
6. In GitHub: Settings > Environments > New environment `production`. Add required reviewers (tick "Prevent self-review" only if someone else should approve). Add environment secrets:
   - `VPS_HOST`: host name or IP
   - `VPS_USER`: `deploy`
   - `VPS_SSH_KEY`: content of the private key file `sin-ai-deploy`
   - `VPS_KNOWN_HOSTS`: output of step 4

## Deploy and roll back

- Deploy: Actions > Deploy > Run workflow > version `0.1.0` (a release) or `main-<sha7>` (a main build). Approve the `production` environment when asked.
- Roll back: run the same workflow with the previous version. Nothing is rebuilt.
- A failed deploy is not rolled back automatically: the failing container stays and `version.env` keeps the last good version. Run Deploy again with that version.
- History: `/opt/sin-ai/deploy-history.log`; current version: `/opt/sin-ai/version.env`.
- Manual commands on the server: `cd /opt/sin-ai && docker compose --env-file .env --env-file version.env ps` (or `logs app`).

`deploy.sh` pulls the image, backs up the database (when Postgres is already running), starts the app and waits for its healthcheck. Flyway migrates on startup, so the backup is taken before the new version starts.

## Database backups

- `backups/sinai-<utc-time>-before-<version>.sql.gz`, the newest 14 are kept (`KEEP_BACKUPS`).
- A rollback does **not** restore the database. Migrations must follow expand/contract so an older app still runs on a newer schema.
- Restore a backup only when the data itself is broken:
  ```sh
  cd /opt/sin-ai
  docker compose --env-file .env --env-file version.env stop app
  docker compose --env-file .env --env-file version.env exec -T postgres dropdb -U sinai --if-exists sinai
  docker compose --env-file .env --env-file version.env exec -T postgres createdb -U sinai sinai
  gunzip -c backups/<file>.sql.gz | docker compose --env-file .env --env-file version.env exec -T postgres psql -U sinai -d sinai
  docker compose --env-file .env --env-file version.env start app
  ```

## Known gaps

- Backend only: the frontend image is not published yet.
- No domain, TLS or reverse proxy: the app (`:8080`) and MinIO (`:9000`) listen on `127.0.0.1` only. A reverse proxy must publish them; `STORAGE_PUBLIC_ENDPOINT` must be reachable from browsers for download links, and `SINAI_REFRESH_COOKIE_SECURE=true` needs HTTPS.
- Whisper and Ollama are not part of this stack, so subtitle jobs fail until they are added.
- The deploy files come from the workflow's branch (`main`), so a rollback runs today's `compose.yml` with the older image.
