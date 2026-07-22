# Security in Hexagonal Architecture — Where & Why

**Date:** July 22, 2026  
**Purpose:** Presentation guide for security architecture in hexagonal systems  
**Audience:** Architects, developers, technical leads

---

## 📋 Table of Contents

1. [Overview](#overview)
2. [Architectural Layout](#architectural-layout)
3. [Component Placement](#component-placement)
4. [Complete Data Flow](#complete-data-flow)
5. [Anti-Patterns](#anti-patterns)
6. [Decision Matrix](#decision-matrix)
7. [Key Principles](#key-principles)
8. [Real-World Example](#real-world-example)
9. [Benefits](#benefits)
10. [Implementation Checklist](#implementation-checklist)

---

## Overview

In Hexagonal Architecture, **security is an inbound adapter** because:

- ✅ Authentication comes from external protocols (HTTP headers, tokens)
- ✅ User identity is a domain concern (UserContext)
- ✅ Token validation is implementation detail (could be Base64, JWT, OAuth2)
- ✅ Security configuration is Spring-specific (belongs in application module)

---

## Architectural Layout

### The Security Stack in Hexagonal

```
┌─────────────────────────────────────────────────────────────────┐
│                    SECURITY LAYER MAPPING                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  🧠 ecs-core/  (Pure Domain — ZERO Spring)                     │
│  ├── ports/TokenExtractorPort.java        ← Interface (contract)│
│  ├── domain/UserContext.java              ← User identity       │
│  └── domain/TokenValidationException.java ← Domain exception    │
│                                                                 │
│  🌐 ecs-inbound-adapters/  (HTTP/Request Protocol)            │
│  ├── security/BearerTokenExtractor.java   ← Implements port    │
│  ├── security/BearerTokenAuthenticationFilter.java ← Spring    │
│  ├── security/RequestContextHolder.java   ← Thread safety      │
│  └── rest/FileController.java             ← Uses UserContext   │
│                                                                 │
│  💾 ecs-outbound-adapters/  (Storage/External Services)       │
│  └── storage/S3StorageAdapter.java        ← Does NOT use auth  │
│                                                                 │
│  ⚙️  ecs-application/  (Spring Configuration ONLY)            │
│  └── config/SecurityConfiguration.java    ← Wires beans       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Component Placement

### 1️⃣ Ports/Interfaces → `ecs-core/ports/`

#### File: `TokenExtractorPort.java`

```java
// ✅ CORRECT: In ecs-core (pure domain)
public interface TokenExtractorPort {
    UserContext extractUserContext(String bearerToken) throws TokenValidationException;
    boolean isTokenValid(String bearerToken);
}
```

#### Why:
| Reason | Explanation |
|--------|-------------|
| **Domain needs it** | Core use cases call this port to get user info |
| **Zero Spring** | Interface is pure Java contract, no framework imports |
| **Swappable** | Other adapters can implement it (OAuth2, JWT, SAML) |
| **Hexagonal Rule 1** | Core has NO Spring dependencies |

#### Consequence:
- ✅ FileServiceImpl can reference UserContext without importing adapters
- ✅ Easy to test with mocked port
- ✅ Can swap implementations without touching core

---

### 2️⃣ Domain Objects → `ecs-core/domain/`

#### Files: `UserContext.java`, `TokenValidationException.java`

```java
// ✅ CORRECT: In ecs-core (domain value object)
@Value
public class UserContext {
    String userId;      // "alice"
    String email;       // "alice@example.com"
    String token;       // Bearer token from request
}

// ✅ CORRECT: In ecs-core (domain exception)
public class TokenValidationException extends Exception {
    public TokenValidationException(String message) { super(message); }
    public TokenValidationException(String message, Throwable cause) { 
        super(message, cause); 
    }
}
```

#### Why:
| Reason | Explanation |
|--------|-------------|
| **Used by domain** | FileServiceImpl generates user-scoped keys using userId |
| **Domain concept** | User identity is core, not framework-specific |
| **Exception contract** | Thrown by port, caught by domain logic |
| **Reusability** | Any use case can access UserContext |

#### Consequence:
- ✅ Core logic can work with user identity
- ✅ All layers can reference UserContext without circular deps
- ✅ Type-safe, compiler-checked user information

---

### 3️⃣ Token Extraction Implementation → `ecs-inbound-adapters/security/`

#### File: `BearerTokenExtractor.java`

```java
// ✅ CORRECT: In ecs-inbound-adapters
@Component
public class BearerTokenExtractor implements TokenExtractorPort {
    
    @Override
    public UserContext extractUserContext(String bearerToken) 
            throws TokenValidationException {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new TokenValidationException("Bearer token is empty");
        }
        
        try {
            // Decode Base64
            String decoded = new String(Base64.getDecoder().decode(bearerToken));
            
            // Parse userId:email format
            String[] parts = decoded.split(":");
            if (parts.length != 2) {
                throw new TokenValidationException("Invalid token format");
            }
            
            return new UserContext(parts[0].trim(), parts[1].trim(), bearerToken);
        } catch (IllegalArgumentException ex) {
            throw new TokenValidationException("Invalid token encoding", ex);
        }
    }
}
```

#### Why:
| Reason | Explanation |
|--------|-------------|
| **Inbound adapter** | Converts external format (Bearer token) to domain object |
| **Implements port** | Satisfies Hexagonal Rule 3 |
| **Can use Spring** | @Component is OK in adapters |
| **Testable** | Can mock without Spring context |
| **Separate concern** | Implementation details don't leak to core |

#### Consequence:
- ✅ Can unit test with Mockito (no Spring context)
- ✅ Easy to switch to JWT implementation
- ✅ Core doesn't know about Base64 encoding

---

### 4️⃣ Spring Security Filter → `ecs-inbound-adapters/security/`

#### File: `BearerTokenAuthenticationFilter.java`

```java
// ✅ CORRECT: In ecs-inbound-adapters
@Component
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {
    
    @Autowired
    private TokenExtractorPort tokenExtractor;  // Depends on port, not impl
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) 
            throws ServletException, IOException {
        try {
            // Extract Authorization header (HTTP protocol)
            String authHeader = request.getHeader("Authorization");
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                // Convert HTTP → domain
                String token = authHeader.substring(7);
                UserContext userContext = tokenExtractor.extractUserContext(token);
                
                // Store in request context
                RequestContextHolder.set(userContext);
                
                log.debug("Bearer token authenticated: userId={}", 
                         userContext.getUserId());
            }
        } finally {
            // Prevent ThreadLocal leaks
            filterChain.doFilter(request, response);
            RequestContextHolder.clear();
        }
    }
}
```

#### Why:
| Reason | Explanation |
|--------|-------------|
| **Inbound adapter** | Intercepts HTTP requests (external protocol) |
| **Hexagonal Rule 2** | Depends on port interface, not on outbound adapters |
| **Spring-specific** | OncePerRequestFilter is Spring — belongs in adapter |
| **Protocol handling** | HTTP headers are adapter concern, not domain |
| **Request lifecycle** | Manages servlet request context |

#### Consequence:
- ✅ Spring Security can be swapped/tested independently
- ✅ Core doesn't know about Spring Security
- ✅ Easy to disable filter for testing

---

### 5️⃣ ThreadLocal Context → `ecs-inbound-adapters/security/`

#### File: `RequestContextHolder.java`

```java
// ✅ CORRECT: In ecs-inbound-adapters
public class RequestContextHolder {
    
    private static final ThreadLocal<UserContext> contextHolder = new ThreadLocal<>();
    
    public static void set(UserContext context) {
        contextHolder.set(context);
    }
    
    public static UserContext get() {
        UserContext context = contextHolder.get();
        if (context == null) {
            throw new IllegalStateException("UserContext not set in ThreadLocal");
        }
        return context;
    }
    
    public static void clear() {
        contextHolder.remove();
    }
}
```

#### Why:
| Reason | Explanation |
|--------|-------------|
| **Request-scoped** | ThreadLocal is servlet container infrastructure |
| **Inbound concern** | Manages incoming request lifecycle |
| **Not domain** | Core shouldn't know about ThreadLocal mechanics |
| **Infrastructure** | Implementation detail of request handling |

#### Consequence:
- ✅ UserContext available throughout request without passing as parameter
- ✅ Proper cleanup prevents memory leaks
- ✅ Thread-safe for concurrent requests

---

### 6️⃣ Spring Security Configuration → `ecs-application/config/`

#### File: `SecurityConfiguration.java`

```java
// ✅ CORRECT: In ecs-application (ONLY place for @Configuration)
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {
    
    @Value("${app.security.bearer-token-auth-enabled:true}")
    private boolean bearerAuthEnabled;
    
    @Bean
    public TokenExtractorPort tokenExtractor() {
        return new BearerTokenExtractor();
    }
    
    @Bean
    public BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter(
            TokenExtractorPort tokenExtractor) {
        return new BearerTokenAuthenticationFilter(tokenExtractor);
    }
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                          BearerTokenAuthenticationFilter bearerFilter) 
            throws Exception {
        http
            .csrf().disable()
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/files/**").authenticated()
                .anyRequest().permitAll()
            )
            .addFilterBefore(bearerFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
```

#### Why:
| Reason | Explanation |
|--------|-------------|
| **Configuration only** | Hexagonal Rule 4: @Configuration forbidden in core & adapters |
| **Assembly point** | ONE place where everything is wired together |
| **Dependency injection** | Beans are created and managed here |
| **Spring-specific** | @Configuration is Spring concern |
| **Easy to understand** | All security wiring in one file |

#### Consequence:
- ✅ Single source of truth for security configuration
- ✅ Easy to swap implementations (change bean factory)
- ✅ ArchUnit validates no @Configuration elsewhere

---

## Complete Data Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                   SECURITY DATA FLOW                            │
└─────────────────────────────────────────────────────────────────┘

① HTTP Request arrives with Bearer token
   ↓
   Authorization: Bearer dXNlcjEyMzp1c2VyQGV4YW1wbGUuY29t
   
② Spring Security dispatches to BearerTokenAuthenticationFilter
   (inbound adapter)
   
③ Filter extracts Authorization header
   "Bearer dXNlcjEyMzp1c2VyQGV4YW1wbGUuY29t"
   
④ Filter calls TokenExtractorPort.extractUserContext()
   (port interface defined in core)
   
⑤ BearerTokenExtractor (inbound adapter) implements port
   - Removes "Bearer " prefix
   - Decodes Base64: "user123:user@example.com"
   - Validates format: has colon
   - Returns UserContext(userId="user123", email="user@example.com")
   
⑥ Filter stores UserContext in RequestContextHolder
   (Thread-local, request-scoped)
   
⑦ Request continues to FileController (inbound adapter)
   - Gets UserContext from RequestContextHolder
   - Calls FileServicePort.upload(userContext, filename, contentType, content)
   
⑧ FileServiceImpl (core domain, ZERO Spring knowledge)
   - Has access to userContext.getUserId()
   - Generates user-scoped key: "users/user123/2026-07-22/uuid-filename.pdf"
   - Calls StoragePort.store(bucket, key, contentType, content)
   
⑨ S3StorageAdapter (outbound adapter)
   - Doesn't know about authentication (right level)
   - Uploads to S3/MinIO
   - Returns StorageObject(key, userId)
   
⑩ Response: 201 Created
   {
     "key": "users/user123/2026-07-22/uuid-filename.pdf",
     "userId": "user123"
   }
   
⑪ RequestContextHolder.clear() removes ThreadLocal
   (prevents memory leaks)

```

---

## Anti-Patterns

### ❌ Anti-Pattern 1: Spring in Core

```java
// ❌ WRONG: This violates Hexagonal Rule 1
package com.example.core.security;

@Service
@Component
public class TokenExtractor implements TokenExtractorPort {
    // Spring annotations in core = VIOLATION
}
```

**Why it's wrong:**
- Core imports `org.springframework.stereotype.Service`
- ArchUnit test FAILS: "Core must not depend on Spring"
- Hard to test without full Spring context
- Violates clean architecture

**Fix:**
```java
// ✅ CORRECT: No Spring, just implementation
public class BearerTokenExtractor implements TokenExtractorPort {
    // Move to ecs-inbound-adapters/security/
}
```

---

### ❌ Anti-Pattern 2: Security in Outbound Adapters

```java
// ❌ WRONG: This violates purpose of outbound adapter
@Component
public class S3StorageAdapter implements StoragePort {
    
    @Autowired
    private TokenExtractorPort tokenExtractor;  // Wrong layer!
    
    @Override
    public StorageObject store(...) {
        // Outbound should NOT touch authentication
        UserContext user = tokenExtractor.extract(...);
        // ...
    }
}
```

**Why it's wrong:**
- Outbound adapters are for external services (S3, databases)
- Should NOT know about HTTP authentication
- Violates single responsibility
- Creates circular dependency with inbound logic

**Fix:**
```java
// ✅ CORRECT: Let inbound adapter provide userId
@Component
public class S3StorageAdapter implements StoragePort {
    
    @Override
    public StorageObject store(String bucket, String key, String contentType, byte[] content) {
        // Just upload — don't touch auth
        s3Client.putObject(bucket, key, content);
        return new StorageObject(key, bucket, contentType, content.length, Instant.now());
    }
}
```

---

### ❌ Anti-Pattern 3: Configuration in Adapters

```java
// ❌ WRONG: This violates Hexagonal Rule 4
package com.example.adapters.inbound.security;

@Configuration  // Forbidden here!
public class SecurityConfig {
    @Bean
    public TokenExtractorPort tokenExtractor() { ... }
}
```

**Why it's wrong:**
- ArchUnit test FAILS: "@Configuration allowed ONLY in application module"
- Makes wiring hard to understand (scattered across adapters)
- Breaks single assembly point principle
- Violates hexagonal boundaries

**Fix:**
```java
// ✅ CORRECT: All configuration in ecs-application
package com.example.application.config;

@Configuration
public class SecurityConfiguration {
    @Bean
    public TokenExtractorPort tokenExtractor() { ... }
}
```

---

### ❌ Anti-Pattern 4: Business Logic in Configuration

```java
// ❌ WRONG: Configuration should not have business logic
@Configuration
public class SecurityConfiguration {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/files/**").access((auth, context) -> {
                // ❌ WRONG: Business logic in configuration!
                User user = auth.getPrincipal();
                if (user.hasRole("ADMIN") || user.getId().equals("special-user")) {
                    return new AuthorizationDecision(true);
                }
                return new AuthorizationDecision(false);
            })
        );
        return http.build();
    }
}
```

**Why it's wrong:**
- Authorization rules are business logic, not configuration
- Hard to test
- Mixed concerns
- Violates SOLID principles

**Fix:**
```java
// ✅ CORRECT: Authorization in core/domain
public class FileServiceImpl implements FileServicePort {
    
    public StorageObject upload(UserContext user, String filename, 
                               String contentType, byte[] content) {
        // Authorization logic in core
        if (!isUserAuthorized(user)) {
            throw new UnauthorizedException("User not allowed");
        }
        // ... rest of business logic ...
    }
}

// ✅ CORRECT: Spring config just wires it
@Configuration
public class SecurityConfiguration {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/files/**").authenticated()  // Just require auth
            // Business authorization happens in domain layer
        );
        return http.build();
    }
}
```

---

## Decision Matrix

### Where Does Each Security Component Go?

| Component | Layer | Reason | Spring OK? |
|-----------|-------|--------|-----------|
| **Port Interface** | `ecs-core/ports/` | Domain decides what it needs | ❌ No |
| **UserContext** | `ecs-core/domain/` | Used throughout domain | ❌ No |
| **TokenValidationException** | `ecs-core/domain/` | Domain exception type | ❌ No |
| **Token Extractor Implementation** | `ecs-inbound-adapters/security/` | Converts HTTP to domain | ✅ Yes |
| **Spring Security Filter** | `ecs-inbound-adapters/security/` | HTTP interception | ✅ Yes |
| **ThreadLocal Context** | `ecs-inbound-adapters/security/` | Request-scoped infrastructure | ✅ Yes |
| **REST Controller** | `ecs-inbound-adapters/rest/` | HTTP endpoint | ✅ Yes |
| **Spring Security Config** | `ecs-application/config/` | Bean wiring ONLY | ✅ Yes |
| **OAuth2 Client** | `ecs-outbound-adapters/` | External auth provider | ✅ Yes |
| **JDBC/DB Security** | `ecs-outbound-adapters/` | External data access | ✅ Yes |

---

## Key Principles

### Principle 1: Ports in Core, Implementations in Adapters

```
┌──────────────────────┐
│  ecs-core/ports/     │  ← Where interface lives
│ FileServicePort      │     (core decides what it needs)
│ TokenExtractorPort   │
│ StoragePort          │
└──────────────────────┘
           ▲
           │ implements
           │
┌──────────────────────────────────────────┐
│  ecs-inbound-adapters/                   │  ← Where implementation lives
│  FileController                          │     (adapter handles protocol)
│  BearerTokenExtractor                    │
│  BearerTokenAuthenticationFilter         │
└──────────────────────────────────────────┘
```

**Benefit:** Core is protocol-agnostic. Can swap implementations without touching core.

---

### Principle 2: Spring Annotations Only in Adapters & Application

```
❌ ecs-core/
   ├── @Service (FORBIDDEN)
   ├── @Component (FORBIDDEN)
   ├── @Autowired (FORBIDDEN)
   └── import org.springframework.* (FORBIDDEN)

✅ ecs-inbound-adapters/
   ├── @Component
   ├── @Autowired
   └── import org.springframework.*

✅ ecs-application/config/
   ├── @Configuration
   ├── @Bean
   └── import org.springframework.*
```

**Benefit:** Core remains testable without Spring context.

---

### Principle 3: Inbound vs Outbound

```
Security = INBOUND ADAPTER
├── Comes from outside (HTTP headers)
├── Converts external format (Bearer token) to domain (UserContext)
├── Managed by servlet container (request lifecycle)
└── Examples: OAuth2, JWT validation, API keys

Outbound Security = Special Case
├── Only if calling external auth provider
├── Examples: OAuth2 token exchange, SAML validation
└── Still implements domain port interface
```

**Benefit:** Clear separation of concerns.

---

### Principle 4: One Configuration Place

```
SecurityConfiguration.java (ecs-application/config/)
│
├── Bean: TokenExtractorPort (which implementation?)
├── Bean: BearerTokenAuthenticationFilter (how configured?)
├── Bean: SecurityFilterChain (what rules?)
└── Bean: S3Client (already wired by StorageConfiguration)

SINGLE SOURCE OF TRUTH
for all security wiring
```

**Benefit:** Easy to understand, maintain, and test the entire security setup.

---

## Real-World Example

### Scenario: Switch from Base64 to JWT

**Before (Base64 tokens):**

```
Authorization: Bearer dXNlcjEyMzp1c2VyQGV4YW1wbGUuY29t
```

**Architecture:**
- BearerTokenExtractor (Base64 decoding)
- TokenExtractorPort (interface in core)
- SecurityConfiguration wires BearerTokenExtractor

**Requirements:**
- Switch to JWT tokens
- Add expiration validation
- Add signature verification

**What changes?**

✅ Just the implementation in inbound adapter:

```java
// ✅ NEW: JwtTokenExtractor
@Component
public class JwtTokenExtractor implements TokenExtractorPort {
    
    @Override
    public UserContext extractUserContext(String bearerToken) 
            throws TokenValidationException {
        try {
            // Parse JWT
            Jwt jwt = jwtDecoder.decode(bearerToken);
            
            String userId = jwt.getClaimAsString("sub");
            String email = jwt.getClaimAsString("email");
            
            // Check expiration
            if (jwt.getExpiresAt().isBefore(Instant.now())) {
                throw new TokenValidationException("Token expired");
            }
            
            return new UserContext(userId, email, bearerToken);
        } catch (JwtException ex) {
            throw new TokenValidationException("Invalid JWT", ex);
        }
    }
}

// ✅ UPDATED: SecurityConfiguration
@Configuration
public class SecurityConfiguration {
    
    @Bean
    public TokenExtractorPort tokenExtractor() {
        return new JwtTokenExtractor();  // Different implementation!
    }
    // Everything else stays the same
}
```

**What stays the same?**

- ❌ No changes to ecs-core (UserContext, TokenExtractorPort)
- ❌ No changes to FileServiceImpl
- ❌ No changes to BearerTokenAuthenticationFilter
- ❌ No changes to S3StorageAdapter
- ❌ No changes to FileController

**Why?**

Because security is properly isolated as an inbound adapter behind a port interface!

---

## Benefits

### 1. Testability

```java
// Unit test without Spring
@Test
void testFileUpload_withMockedAuth() {
    // Mock the port
    TokenExtractorPort tokenExtractor = mock(TokenExtractorPort.class);
    when(tokenExtractor.extractUserContext(any()))
        .thenReturn(new UserContext("alice", "alice@example.com", "token"));
    
    // Test domain logic directly
    FileServiceImpl fileService = new FileServiceImpl(storagePort, "bucket");
    fileService.upload("document.pdf", "application/pdf", new byte[100]);
    
    // No Spring context needed!
    assertThat(result.getUserId()).isEqualTo("alice");
}
```

**Benefit:** Fast, isolated tests. No TestContext, no Docker needed.

---

### 2. Swappability

```java
// Switch auth mechanisms by changing ONE bean
@Configuration
public class SecurityConfiguration {
    
    @Bean
    @ConditionalOnProperty(name = "auth.type", havingValue = "base64")
    public TokenExtractorPort base64Auth() {
        return new BearerTokenExtractor();
    }
    
    @Bean
    @ConditionalOnProperty(name = "auth.type", havingValue = "jwt")
    public TokenExtractorPort jwtAuth() {
        return new JwtTokenExtractor();
    }
    
    @Bean
    @ConditionalOnProperty(name = "auth.type", havingValue = "oauth2")
    public TokenExtractorPort oauth2Auth() {
        return new OAuth2TokenExtractor();
    }
}
```

**Benefit:** Entire auth system swappable without touching domain.

---

### 3. Compliance & Enforcement

```
ArchUnit Test: HexagonalArchitectureTest

Rule 1: ✅ Core has ZERO Spring imports
Rule 2: ✅ Inbound doesn't call outbound
Rule 3: ✅ Outbound implements ports
Rule 4: ✅ @Configuration ONLY in application

If you add Spring to core: BUILD FAILS
If you add @Configuration to adapter: BUILD FAILS
```

**Benefit:** Architecture is automatically enforced, not just guidelines.

---

### 4. Clarity

```
When debugging security issues, you know:

"Where do I look?"
→ Spring Security filter logic? → ecs-inbound-adapters/security/
→ Token validation? → ecs-inbound-adapters/security/
→ UserContext usage? → ecs-core/domain/
→ Beans wiring? → ecs-application/config/
→ Core domain logic? → ecs-core/usecases/
```

**Benefit:** Clear mental model, easy to navigate.

---

### 5. Independent Evolution

```
Frontend auth layer    → Evolve independently
(inbound adapter)         (change token format)

Core domain            → Evolve independently
(business logic)          (add user roles/permissions)

Backend auth service   → Evolve independently
(if outbound)             (call external provider)

All communicate through
stable port interfaces
```

**Benefit:** Teams can work independently without breaking contracts.

---

## Implementation Checklist

### ✅ Security in Hexagonal

- [ ] **Port interface** defined in `ecs-core/ports/`
- [ ] **UserContext** value object in `ecs-core/domain/`
- [ ] **Domain exceptions** in `ecs-core/domain/`
- [ ] **Token extractor** implements port in `ecs-inbound-adapters/security/`
- [ ] **Spring filter** in `ecs-inbound-adapters/security/`
- [ ] **ThreadLocal context** in `ecs-inbound-adapters/security/`
- [ ] **Spring configuration** in `ecs-application/config/`
- [ ] **No Spring in core** — verified by ArchUnit
- [ ] **No @Configuration in adapters** — verified by ArchUnit
- [ ] **Inbound doesn't call outbound** — verified by ArchUnit
- [ ] **Unit tests mock the port** — no Spring context
- [ ] **Integration tests** use real Spring context

---

## Summary

### The Golden Rule for Security in Hexagonal

```
┌─────────────────────────────────────────────────────────────┐
│  SECURITY ARCHITECTURE PRINCIPLE                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. Port Interface   → Core (contract)                      │
│  2. Implementation   → Inbound Adapter (protocol handling)  │
│  3. Configuration    → Application (Spring wiring)          │
│  4. Domain Objects   → Core (UserContext)                   │
│  5. Spring Beans     → Adapters & Application ONLY          │
│                                                             │
│  Result:                                                    │
│  ✅ Testable (mock the port)                               │
│  ✅ Swappable (change implementation)                       │
│  ✅ Clean (core has zero framework)                         │
│  ✅ Enforceable (ArchUnit validates)                        │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## References

- **Hexagonal Architecture:** https://alistair.cockburn.us/hexagonal-architecture/
- **Ports & Adapters:** https://www.dddcommunity.org/resources/ddd_resources/
- **Clean Architecture:** https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html
- **ArchUnit:** https://www.archunit.org/

---

**Last Updated:** July 22, 2026  
**Format:** Presentation-ready Markdown  
**Audience:** Technical leaders, architects, senior developers

