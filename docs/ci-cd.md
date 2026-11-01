# CI/CD

How code moves from a branch to a running server.

```
feature branch --PR (squash)--> dev --PR (merge commit)--> main --tag vX.Y.Z--> image on ghcr.io --Deploy--> VPS
                 CI: test          CI: test                   Release: image       Release: image       manual, versioned
```

| Workflow | File | Runs on | Job (check name) |
|---|---|---|---|
| CI | `.github/workflows/ci.yml` | push to `dev`/`main`, every PR | `test` |
| Release | `.github/workflows/release.yml` | push to `main`, tags `v*.*.*`, every PR (build only) | `image` |
| Deploy | `.github/workflows/deploy.yml` | manual run; PRs touching deploy files (rehearsal) | `deploy`, `rehearse` |

## Branches and rules

- `main` is the default branch and the release branch. `dev` is the integration branch.
- New work: `git switch dev && git pull --ff-only && git switch -c <type>/<topic>`, then a PR into `dev`.
- Release: a PR from `dev` into `main`.
- Rulesets (Settings > Rules):

| Ruleset | Branch | Rules |
|---|---|---|
| `main-protection` | `main` | no deletion, no force push, PR required, merge commit only, required check `test` |
| `dev-protection` | `dev` | no deletion, no force push, PR required, squash only, required check `test` |

- Why two merge methods: squash keeps one commit per feature on `dev`. A merge commit from `dev` into `main` keeps both branches on the same history. Squashing `dev` into `main` creates commits that `dev` lacks, and the next `dev` -> `main` PR conflicts.
- "Require linear history" must stay off for `main`, otherwise merge commits are not allowed.

## CI (`ci.yml`)

1. Sets up JDK 27 and JDK 21. Gradle runs on 21 (last installed, so `JAVA_HOME`) and compiles with the JDK 27 toolchain via `-Dorg.gradle.java.installations.paths=$JAVA_HOME_27_X64`, like `docker/app/Dockerfile`. `gradle.properties` holds a Windows-only path, so the flag is required.
2. Installs `ffmpeg` so the media tests run instead of skipping.
3. Starts Postgres and MinIO from `docker/docker-compose.yml` (`up -d --wait postgres storage`, then `run --rm storage-init`).
4. Runs `./gradlew test`.
5. Fails if `JobFlowIntegrationTest`, `JobLifecycleIntegrationTest` or `MinioObjectStorageIntegrationTest` report skipped tests. Those tests skip themselves when env vars or ports are missing, which would otherwise look green.
6. Uploads the HTML test reports as the `test-reports` artifact (7 days).

Environment values:

- Throwaway credentials are written directly in the workflow `env:`; the containers only live for the run.
- Compose interpolates the whole file, so every `${X:?}` needs a value, even for services that are not started (`WHISPER_MODEL`, `OLLAMA_MODEL`, `SINAI_JWT_SECRET` get dummy values).
- `MINIO_LICENSE` is the only repository secret. Pull requests from forks get no secrets, so they skip the infrastructure steps and run unit tests only.

## Versioning and releases (`release.yml`)

Versions follow SemVer `vMAJOR.MINOR.PATCH` and are set by git tags only (no version in Gradle files).

| Change since the last release | Bump | Example |
|---|---|---|
| Bug fixes only | PATCH | `v0.3.0` -> `v0.3.1` |
| New features, still compatible | MINOR | `v0.3.1` -> `v0.4.0` |
| Breaking API or behaviour change | MAJOR (MINOR while on `0.x`) | `v1.4.2` -> `v2.0.0` |

Image tags on `ghcr.io/norsindark/sin-ai`:

| Event | Tags |
|---|---|
| Tag `v1.2.3` | `1.2.3`, `1.2`, `sha-<sha7>` |
| Push to `main` | `main-<sha7>`, `main` |
| Pull request | built, not pushed |

There is no `latest` tag. The version is passed to Gradle as the `APP_VERSION` build arg (`-Pversion`), so the jar's `META-INF/build-info.properties` and `/actuator/info` report it. Local compose builds get `0.0.0-local`.

Release steps:

1. Merge the `dev` -> `main` PR (merge commit) and wait for CI.
2. See what changed: `git fetch --tags && git log --oneline $(git describe --tags --abbrev=0 origin/main)..origin/main`.
3. Pick the next version from the table above.
4. Releases > Draft a new release > Choose a tag > type `vX.Y.Z` > "Create new tag on publish" > target `main` > Generate release notes > Publish. CLI alternative: `git tag -a vX.Y.Z -m "vX.Y.Z" origin/main && git push origin vX.Y.Z`.
5. Check the Release run in Actions and the tags under Packages > `sin-ai`.

Rules:

- Tag only after `dev` is merged into `main`. A workflow runs from the files in the tagged commit, so a tag on a commit without `release.yml` builds nothing.
- Never move or reuse a published tag; release a new version instead. Rulesets do not protect tags.
- Pre-releases such as `v1.3.0-rc.1` are built too, without the short `1.3` tag.
- The image package is public (the repository is public). It contains no secrets: `.dockerignore` excludes `.env` and `docker/`, and all credentials come from the server `.env` at runtime.

## Deploy (`deploy.yml`)

- Actions > Deploy > Run workflow > version (`0.1.0` or `main-<sha7>`). The job validates the version, checks the image exists, waits for approval of the `production` environment, uploads the deploy bundle over SSH and runs `deploy/deploy.sh` on the server.
- Roll back by running Deploy again with an older version. Nothing is rebuilt; the database is not restored.
- Pull requests that touch `deploy/**` run the `rehearse` job: deploy, deploy another version, roll back, then check version, history, backups and health on the runner.
- Server setup, secrets, backups, restore and known gaps: [`deploy/README.md`](../deploy/README.md).

## Secrets

| Name | Scope | Used by |
|---|---|---|
| `MINIO_LICENSE` | repository | CI, deploy rehearsal |
| `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`, `VPS_KNOWN_HOSTS` | environment `production` | Deploy |
| `GITHUB_TOKEN` | automatic | Release (push to ghcr.io) |

Add one in Settings > Secrets and variables > Actions, or without showing the value: `grep '^NAME=' file | cut -d= -f2- | tr -d '\r' | gh secret set NAME --repo norsindark/sin-ai`. Values cannot be read back, only replaced or deleted. Never commit a real `.env`; only `.env.example` files are tracked.

## Troubleshooting

- A run named after the file path (`.github/workflows/ci.yml`) instead of the workflow name, failing instantly: the YAML is invalid. Indentation is syntax; `branches:` must sit under `push:`.
- A PR shows no checks: the workflow file is invalid on the base branch, or its triggers do not match.
- "Create a merge commit" is missing on a PR into `main`: "Require linear history" is on in the ruleset.
- `dev` -> `main` conflicts after a squash into `main`: merge `main` back into `dev` through a branch and PR, keeping `dev`'s content.
- Integration tests "pass" too fast: check the skipped-tests step and the `test-reports` artifact.
- The Deploy "Run workflow" button is missing: `deploy.yml` must exist on `main`.
