---
name: docker-hardening
description: Use when adding or changing Dockerfiles, container build settings, or release image pipelines.
license: MIT
---

Validate container hardening and runtime image risk:

1. Build image:
   - `docker build -t app:local .`
2. Lint Dockerfile:
   - `hadolint Dockerfile`
3. Scan image for HIGH and CRITICAL issues:
   - `trivy image --severity HIGH,CRITICAL --exit-code 1 app:local`
4. Prefer minimal, reproducible base images and non-root runtime users.
5. Document any accepted risk with rationale and expiry.

See `docker-checks.md` for detailed checks.

