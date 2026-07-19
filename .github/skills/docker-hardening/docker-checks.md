# 🤖 Docker Hardening Checks

## Dockerfile baseline
- [ ] Uses a pinned and maintained base image
- [ ] Uses non-root runtime user
- [ ] Includes only required runtime artifacts
- [ ] Avoids secrets in image layers and build args
- [ ] Defines explicit entrypoint or command

## Commands
```bash
docker build -t app:local .
hadolint Dockerfile
trivy image --severity HIGH,CRITICAL --exit-code 1 app:local
```

## Exception handling
If a HIGH or CRITICAL issue is temporarily accepted, include:
- vulnerability identifier
- compensating controls
- owner and target remediation date

