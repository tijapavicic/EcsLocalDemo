# Spring Boot OWASP Checks

## Checklist
- [ ] `mvn -B clean verify` passed
- [ ] `mvn -B org.owasp:dependency-check-maven:check` completed
- [ ] No HIGH or CRITICAL vulnerabilities remain
- [ ] Upgrade proposal uses lowest-risk fixed version
- [ ] Security impact is documented

## Findings template
Use this in PR comments or review notes:

```
Dependency: <group:artifact or package>
Current version: <x.y.z>
CVE: <id>
Severity: <LOW|MEDIUM|HIGH|CRITICAL>
Fixed version: <x.y.z>
Planned upgrade: <x.y.z>
Risk notes: <compatibility/test impact>
```

