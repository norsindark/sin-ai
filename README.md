# sin-ai

Backend that turns an uploaded `.mp4` into subtitles: it extracts the audio, transcribes it with Whisper, translates the subtitles with an LLM (Ollama) and merges every track back into the video. The web app lives in [`sin-ai-fe`](https://github.com/norsindark/sin-ai-fe).

```
upload -> job PENDING -> PROCESSING: FFmpeg audio -> Whisper STT -> source .srt -> Ollama translation per language -> merge into .mp4 -> COMPLETED | FAILED
```

## Stack

- Java 27, Spring Boot 4.1, Gradle 9.7 (Kotlin DSL, convention plugins in `build-logic/`)
- PostgreSQL 18 (Flyway migrations), MinIO AIStor (S3 storage, presigned download URLs)
- FFmpeg, Faster-Whisper (speaches) and Ollama for the AI pipeline
- Spring Security with self-issued JWTs (access token) and an HttpOnly refresh cookie
- OpenTelemetry export to Grafana (`grafana/otel-lgtm`), optional

## Modules

Gradle modular monolith. `app` depends on all modules; `subtitle-workflow` uses the other feature modules; the feature modules only depend on `common`.

| Module | Responsibility |
|---|---|
| `app` | Spring Boot application, configuration, Flyway migrations |
| `modules/common` | Shared constants, DTOs, exceptions, utilities |
| `modules/storage-service` | MinIO object storage |
| `modules/media-processor` | FFmpeg audio extraction and subtitle merging |
| `modules/ai-engine` | Whisper transcription and Ollama translation |
| `modules/identity` | Users, roles, JWT auth, refresh sessions, throttling |
| `modules/subtitle-workflow` | Jobs API, worker and pipeline, admin dashboard |

## Requirements

- JDK 27 (toolchain) and JDK 21 or newer to run Gradle
- Docker with the compose plugin
- FFmpeg on `PATH` for running the app or the media tests outside Docker

`gradle.properties` points the toolchain at a local JDK 27 (`org.gradle.java.installations.paths`). Change it to your JDK 27 path, or pass `-Dorg.gradle.java.installations.paths=<jdk-27-home>`.

## Quick start (everything in Docker)

```sh
cp docker/.env.example docker/.env
docker compose -f docker/docker-compose.yml up -d --build --wait
```

Fill in `docker/.env` first; `MINIO_LICENSE` needs a MinIO AIStor license. The API listens on http://localhost:8080 (Swagger UI: `/swagger-ui.html`, OpenAPI: `/v3/api-docs`).

Optional profiles:

- AI services: `docker compose -f docker/docker-compose.yml --profile ai up -d --build` (no `--wait`: the one-shot init containers make it fail spuriously; check that `app` is healthy with `ps`).
- NVIDIA GPU: add `-f docker/docker-compose.gpu.yml`.
- Observability: add `--profile observability`; Grafana on http://localhost:3000, dashboard "sin-ai pipeline".

## Local development

```sh
docker compose -f docker/docker-compose.yml up -d
docker compose -f docker/docker-compose.yml stop app
set -a; . docker/.env; set +a
./gradlew :app:bootRun
```

The `app` container uses port 8080 like `bootRun`, so stop it first. The app reads its settings from environment variables (see `app/src/main/resources/application.yml`); `SINAI_JWT_SECRET` must be at least 32 bytes.

| Command | Purpose |
|---|---|
| `./gradlew classes` | Compile everything |
| `./gradlew test` | All tests |
| `./gradlew :modules:<module>:test` | Tests of one module |
| `./gradlew :app:bootRun` | Run the app |

Integration tests (`JobFlowIntegrationTest`, `JobLifecycleIntegrationTest`, `MinioObjectStorageIntegrationTest`) need Postgres and MinIO running and `SIN_AI_DB_PASSWORD`, `STORAGE_APP_ACCESS_KEY`, `STORAGE_APP_SECRET_KEY` set; without them they skip themselves. The FFmpeg tests skip when `ffmpeg` is not installed.

## CI/CD

```
feature branch --PR (squash)--> dev --PR (merge commit)--> main --tag vX.Y.Z--> image on ghcr.io --Deploy--> VPS
```

| Workflow | File | Runs on | Job (check name) |
|---|---|---|---|
| CI | `.github/workflows/ci.yml` | push to `dev`/`main`, every PR | `test` |
| Release | `.github/workflows/release.yml` | push to `main`, tags `v*.*.*`, every PR (build only) | `image` |
| Deploy | `.github/workflows/deploy.yml` | manual run; PRs touching deploy files (rehearsal) | `precheck`, `deploy`, `rehearse` |

### Branches

- `main` is the default branch and the release branch; `dev` is the integration branch.
- New work: `git switch dev && git pull --ff-only && git switch -c <type>/<topic>`, then a PR into `dev`. Release: a PR from `dev` into `main`.
- Rulesets (Settings > Rules):

| Ruleset | Branch | Rules |
|---|---|---|
| `main-protection` | `main` | no deletion, no force push, PR required, merge commit only, required check `test` |
| `dev-protection` | `dev` | no deletion, no force push, PR required, squash only, required check `test` |

- Squash keeps one commit per feature on `dev`. A merge commit from `dev` into `main` keeps both branches on the same history; squashing into `main` creates commits that `dev` lacks and the next `dev` -> `main` PR conflicts. "Require linear history" must stay off for `main`.

### CI

1. Sets up JDK 27 and JDK 21. Gradle runs on 21 and compiles with the JDK 27 toolchain via `-Dorg.gradle.java.installations.paths=$JAVA_HOME_27_X64`, like `docker/app/Dockerfile`.
2. Installs `ffmpeg` so the media tests run.
3. Starts Postgres and MinIO from `docker/docker-compose.yml` (`up -d --wait postgres storage`, then `run --rm storage-init`).
4. Runs `./gradlew test`.
5. Fails if the integration test reports show skipped tests (they skip silently when env vars or ports are missing).
6. Uploads the HTML test reports as the `test-reports` artifact (7 days).

Throwaway credentials are written in the workflow `env:`; compose needs every `${X:?}` set, even for services that are not started. `MINIO_LICENSE` is the only repository secret. Pull requests from forks get no secrets, so they skip the infrastructure steps.

### Dependency updates

- Dependabot (`.github/dependabot.yml`) opens weekly version-update PRs into `dev` for GitHub Actions, Gradle (`gradle/libs.versions.toml`), the `docker/app/Dockerfile` base images and the images in `docker/docker-compose.yml` and `deploy/compose.yml`. Minor and patch updates are grouped into one PR per ecosystem. Dependabot reads the config from `main` only, so a change to it takes effect after the `dev` -> `main` merge.
- Ignored: JDK and Postgres majors (a Postgres major needs a data migration), the MinIO AIStor images (license-bound `RELEASE.<timestamp>` tags), MinIO client majors (API changes) and `opentelemetry-logback-appender-1.0` (its version must match the OpenTelemetry SDK that Spring Boot manages, so bump it together with Boot). Upgrade those by hand.
- Dependabot alerts are on; automatic security-update PRs are off, because they always target `main`. Fix an alert through a normal PR into `dev`.
- Dependabot runs get no Actions secrets, so `MINIO_LICENSE` is also stored as a Dependabot secret (see Secrets).
- Review a Dependabot PR like any other: wait for `test`, read the release notes for minor bumps, then squash into `dev`.

### Versioning and releases

Versions follow SemVer `vMAJOR.MINOR.PATCH` and come from git tags only.

| Change since the last release | Bump | Example |
|---|---|---|
| Bug fixes only | PATCH | `v0.3.0` -> `v0.3.1` |
| New features, still compatible | MINOR | `v0.3.1` -> `v0.4.0` |
| Breaking API or behaviour change | MAJOR (MINOR while on `0.x`) | `v1.4.2` -> `v2.0.0` |

| Event | Tags on `ghcr.io/norsindark/sin-ai` |
|---|---|
| Tag `v1.2.3` | `1.2.3`, `1.2`, `sha-<sha7>` |
| Push to `main` | `main-<sha7>`, `main`, `sha-<sha7>` |
| Pull request | built as `sha-<sha7>` (the version passed to the build), not pushed |

There is no `latest` tag. The version reaches Gradle as the `APP_VERSION` build arg (`-Pversion`), so `META-INF/build-info.properties` and `/actuator/info` report it; local compose builds get `0.0.0-local`.

Release steps:

1. Merge the `dev` -> `main` PR (merge commit) and wait for CI.
2. List the changes: `git fetch --tags && git log --oneline $(git describe --tags --abbrev=0 origin/main)..origin/main`.
3. Pick the next version from the table above.
4. Releases > Draft a new release > tag `vX.Y.Z` ("Create new tag on publish") > target `main` > Generate release notes > Publish. CLI: `git tag -a vX.Y.Z -m "vX.Y.Z" origin/main && git push origin vX.Y.Z`.
5. Check the Release run in Actions and the tags under Packages > `sin-ai`.

- Tag only after `dev` is merged into `main`: a workflow runs from the files in the tagged commit.
- Never move or reuse a published tag; release a new version instead. Rulesets do not protect tags.
- Pre-releases such as `v1.3.0-rc.1` are built too, without the short `1.3` tag.
- The image is public like the repository and contains no secrets; credentials come from the server `.env` at runtime.

### Image scan

The Release job builds the image locally, scans it with [Trivy](https://trivy.dev) and only then pushes it, so a vulnerable image never reaches ghcr.io.

- The job fails on CRITICAL vulnerabilities that have a fix (`ignore-unfixed`); unfixable ones would block every release without anything to do about them. Pull requests run the same gate.
- Pushes to `main` and tags also upload HIGH and CRITICAL findings to Security > Code scanning (category `trivy-image`).
- Fix a finding by bumping the dependency or the base image tag. Only when that is impossible, add the CVE to a `.trivyignore` file in the repository root with a reason and an expiry: `CVE-2026-12345 exp:2027-01-31` on its own line, the reason in a `#` comment above it.
- `aquasecurity/trivy-action` is pinned to a full commit SHA because its tags were hijacked in March 2026 ([GHSA-69fq-xp46-6x23](https://github.com/aquasecurity/trivy/security/advisories/GHSA-69fq-xp46-6x23)). Dependabot updates that SHA; the Trivy binary version (`TRIVY_VERSION` in `release.yml`) is bumped by hand.

### Deploy

- Actions > Deploy > Run workflow > version (`0.1.0` or `main-<sha7>`). The `precheck` job validates the version and checks that the image exists, so a typo fails before anyone is asked to approve. The `deploy` job then waits for approval of the `production` environment (an environment gate blocks the whole job before its first step), uploads the deploy bundle over SSH and runs `deploy/deploy.sh` (pull, database backup, start, healthcheck, history).
- Roll back by running Deploy again with an older version. Nothing is rebuilt and the database is not restored, so migrations must be expand/contract (add first, drop or rename in a later release).
- Pull requests touching `deploy/**` run the `rehearse` job on a runner: deploy, deploy another version, roll back, then check version, history, backups and health.
- Server setup, secrets, backups, restore and known gaps: [`deploy/README.md`](deploy/README.md).

### Secrets

| Name | Scope | Used by |
|---|---|---|
| `MINIO_LICENSE` | repository (Actions and Dependabot) | CI, deploy rehearsal |
| `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`, `VPS_KNOWN_HOSTS` | environment `production` | Deploy |
| `GITHUB_TOKEN` | automatic | Release (push to ghcr.io) |

Add one in Settings > Secrets and variables > Actions (or > Dependabot), or without printing the value: `grep '^NAME=' file | cut -d= -f2- | tr -d '\r' | gh secret set NAME --repo norsindark/sin-ai` (add `--app dependabot` for the Dependabot copy). Never commit a real `.env`; only `.env.example` files are tracked.

### Troubleshooting

- A run named after the file path instead of the workflow name, failing instantly: the YAML is invalid (indentation is syntax).
- A PR shows no checks: the workflow is invalid on the base branch or its triggers do not match.
- "Create a merge commit" missing on a PR into `main`: "Require linear history" is on.
- `dev` -> `main` conflicts after a squash into `main`: merge `main` back into `dev` through a branch and PR.
- The Deploy "Run workflow" button is missing: `deploy.yml` must exist on `main`.

## Project layout

```
app/                      Spring Boot app, application.yml, db/migration
modules/                  feature modules (see above)
build-logic/              Gradle convention plugins
gradle/libs.versions.toml dependency versions
docker/                   dev compose stack, app Dockerfile, init scripts, Grafana dashboards
deploy/                   production compose, deploy script and server guide
.github/workflows/        CI, Release, Deploy
```
