# GitHub Copilot Agents - EcsLocalDemo

This document recommends specialized agents for specific tasks within the EcsLocalDemo project.

## Project Context
**EcsLocalDemo** is a Java Spring Boot application for multi-cloud S3/object storage integration with ECS orchestration. The project implements three profiles (local-minio, amazon, azure) for flexibility across development, AWS, and Azure environments.

---

## Agent Recommendations by Task

### Infrastructure & Deployment

#### 1. **senior-java-spring-expert-engineer**
**When:** Implementing core Spring Boot features, security configurations, or API endpoints
- Production-grade Spring Boot development
- OWASP security alignment
- Exception handling and validation
- Testing and observability
- Docker hardening for containerization
**Example Tasks:**
- Implementing S3Service abstraction with Spring beans
- Creating REST controllers for file upload/download
- Configuring Spring Security for API access
- Adding observability (logging, metrics, tracing)

#### 2. **se-gitops-ci-specialist**
**When:** Setting up CI/CD pipelines, deployment workflows, GitHub Actions
- Docker image builds and pushes to ECR/ACR
- ECS task definition deployment automation
- Blue-green deployments for zero-downtime updates
- Infrastructure deployment validation
**Example Tasks:**
- Creating GitHub Actions workflows for Maven build + Docker build + ECR push
- Automating ECS task definition updates
- Setting up deployment approvals and safeguards

#### 3. **azure-iac-generator**
**When:** Creating infrastructure as code for Azure deployments
- Bicep template generation for Storage Accounts
- Managed Identity configurations
- ACI/AKS deployment definitions
- RBAC and network security rules
**Example Tasks:**
- Generating Bicep templates for Azure Blob Storage + Managed Identity
- Creating ACI container group definitions
- Setting up networking and private endpoints

#### 4. **swe-subagent**
**When:** General feature development, debugging, refactoring, testing
- Multi-file changes and dependency management
- Feature-driven implementation
- Bug fixes with comprehensive testing
- Code quality improvements
**Example Tasks:**
- Implementing MinIO integration layer
- Adding integration tests with TestContainers
- Refactoring storage service implementations

---

### Security & Compliance

#### 5. **se-security-reviewer**
**When:** Reviewing security configurations, IAM policies, OWASP compliance
- IAM policy validation (least privilege)
- Encryption at rest/in transit
- OWASP Top 10 alignment
- Secrets management best practices
**Example Tasks:**
- Validating IAM role policies for S3 access
- Reviewing Blob Storage RBAC configurations
- Ensuring credentials never exposed in code/logs

#### 6. **jfrog-sec** (if using JFrog for artifact management)
**When:** Managing dependencies and vulnerability scanning
- CVE detection and remediation
- Dependency updates
- Supply chain security
**Example Tasks:**
- Scanning dependencies for CVEs
- Updating AWS SDK v2 and Spring Boot versions

---

### Architecture & Design

#### 7. **se-system-architecture-reviewer**
**When:** Reviewing architectural decisions, scalability, design patterns
- Well-Architected Framework alignment (AWS, Azure)
- Multi-cloud abstraction layer validation
- Performance and scalability analysis
- Design pattern recommendations
**Example Tasks:**
- Reviewing cloud-agnostic storage layer design
- Validating profile-driven configuration strategy
- Assessing data consistency and durability patterns

#### 8. **software-architecture-expert**
**When:** Comprehensive architecture documentation and design patterns
- Architectural decision records (ADRs)
- System design documentation
- Design pattern implementations
- Scalability recommendations
**Example Tasks:**
- Creating ADR for multi-cloud S3 abstraction layer
- Documenting ECS + S3 integration patterns
- Architecture blueprint generation

---

### Documentation

#### 9. **se-technical-writer**
**When:** Creating user guides, API documentation, tutorials
- Developer documentation and tutorials
- API endpoint documentation
- Deployment guides for each profile
- Best practices guides
**Example Tasks:**
- Writing "How to Deploy to AWS ECS" guide
- Creating API documentation for file operations
- Documenting MinIO setup for local development

#### 10. **create-oo-component-documentation**
**When:** Documenting object-oriented components
- Service class documentation
- Interface specifications
- Integration point documentation
- Component interaction diagrams
**Example Tasks:**
- Documenting S3Service interface and implementations
- Creating component interaction diagrams for profile switching

---

### Testing & Quality

#### 11. **tdd-refactor**
**When:** Improving code quality while maintaining test coverage
- Refactoring with green tests
- Security best practices application
- Code smell elimination
- Design pattern improvements
**Example Tasks:**
- Refactoring storage implementations to reduce duplication
- Applying security patches while keeping tests green

#### 12. **tdd-red**
**When:** Starting test-first development from GitHub issues
- Writing failing tests that describe behavior
- Guiding implementation from test requirements
- Acceptance criteria validation
**Example Tasks:**
- Writing failing tests for MinIO integration
- Writing failing tests for IAM role assumption validation

---

### Planning & Research

#### 13. **task-planner**
**When:** Creating actionable implementation plans
- Breaking down large features into steps
- Dependency analysis
- Timeline estimation
- Risk identification
**Example Tasks:**
- Creating multi-step implementation plan for S3 integration
- Planning AWS deployment automation

#### 14. **task-researcher**
**When:** Research and analysis before implementation
- Codebase analysis
- Technology research
- Best practices investigation
- Compatibility validation
**Example Tasks:**
- Researching best practices for ECS + S3 IAM role attachment
- Analyzing MinIO API compatibility with AWS SDK

---

### Specialized Frameworks

#### 15. **github-actions-expert**
**When:** Advanced GitHub Actions workflows
- Secure CI/CD practices
- Secret management in workflows
- OIDC authentication for AWS/Azure
- Supply chain security
**Example Tasks:**
- Creating OIDC-based AWS credential provider for GitHub Actions
- Implementing secure ECR push workflow
- Setting up deployment approval gates

---

## Quick Reference by Workflow

### Local Development Setup
1. **senior-java-spring-expert-engineer** → Create Spring Boot project structure
2. **se-technical-writer** → Create local development guide
3. **swe-subagent** → Implement MinIO integration

### Feature Implementation
1. **task-planner** → Create implementation plan
2. **tdd-red** → Write failing tests
3. **senior-java-spring-expert-engineer** → Implement feature
4. **tdd-refactor** → Refactor and improve code quality
5. **se-security-reviewer** → Validate security

### AWS Deployment
1. **software-architecture-expert** → Design infrastructure
2. **azure-iac-generator** → Create IaC templates (for reference)
3. **se-gitops-ci-specialist** → Automate deployment
4. **se-security-reviewer** → Validate IAM and security

### Azure Deployment
1. **software-architecture-expert** → Design infrastructure
2. **azure-iac-generator** → Create Bicep templates
3. **se-gitops-ci-specialist** → Automate deployment
4. **se-security-reviewer** → Validate RBAC and security

### Documentation Updates
1. **se-technical-writer** → Create or update documentation
2. **create-oo-component-documentation** → Document new components
3. **adr-generator** → Create architectural decision records

### Security Review
1. **se-security-reviewer** → Review configurations
2. **se-responsible-ai-code** → Ensure inclusive and ethical design
3. **jfrog-sec** → Scan dependencies for vulnerabilities

---

## When to Use Multiple Agents

### Multi-Cloud Feature (e.g., Azure Blob Storage support)
1. Task Planner → decompose the work
2. Senior Java Spring Expert → implement core service
3. Azure IaC Generator → create infrastructure templates
4. SE Security Reviewer → validate RBAC/authentication
5. Technical Writer → document Azure setup

### Production Deployment
1. Task Planner → plan deployment strategy
2. Software Architecture Expert → review design
3. SE GitOps Specialist → automate CI/CD
4. SE Security Reviewer → final security audit
5. Technical Writer → create runbooks

---

## How to Request an Agent

When working with GitHub Copilot, you can request specific agents:

```
@copilot /use senior-java-spring-expert-engineer

Implement S3Service interface with Spring bean configuration for AWS SDK v2
with full exception handling and logging.
```

---

## Tips for Better Agent Collaboration

1. **Be Specific:** Provide context about the task (file names, requirements, constraints)
2. **Link to Issues:** Reference GitHub issues for requirements tracking
3. **Provide Examples:** Share similar code or patterns to follow
4. **Specify Constraints:** Mention budget limits, performance requirements, security concerns
5. **Update Documentation:** Always request tutorial or documentation updates after implementation

---

## Notes for Principal Engineer

- Ensure all agents follow the Copilot Instructions in `.github/copilot-instructions.md`
- Regularly review agent outputs for consistency with team standards
- Create ADRs when architectural decisions require long-term documentation
- Monitor dependency updates and security vulnerabilities
- Use agents to enforce code quality gates and best practices

---

*Last Updated: July 19, 2026*
*Project Lead: EcsLocalDemo Development Team*

