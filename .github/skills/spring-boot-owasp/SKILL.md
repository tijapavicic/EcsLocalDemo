---
name: spring-boot-owasp
description: Use when updating dependencies, reviewing security posture, or preparing PRs that change auth/security/config.
license: MIT
---

Validate security for Spring Boot changes:

1. Run unit and integration tests:
   - `mvn -B clean verify`
2. Run dependency vulnerability scan:
   - `mvn -B org.owasp:dependency-check-maven:check`
3. Fail if HIGH or CRITICAL vulnerabilities are found.
4. Propose minimal safe upgrades (prefer patch/minor).
5. Summarize findings:
   - vulnerable dependency
   - CVE and severity
   - fixed version
   - upgrade risk notes

See `checks.md` for checklist and reporting template.

