# Authorization Toggle Guide — Turn Bearer Token Auth On/Off

**Date:** July 22, 2026  
**Project:** EcsLocalDemo  
**Purpose:** Fast toggle of Bearer token authentication for development, testing, and troubleshooting

---

## 📋 Table of Contents

1. [Overview](#overview)
2. [Three Toggle Approaches](#three-toggle-approaches)
3. [Approach 1: Property-Based Toggle](#approach-1-property-based-toggle-fastest-restart)
4. [Approach 2: Comment Out Filter](#approach-2-comment-out-filter-simple)
5. [Approach 3: Runtime Toggle Endpoint](#approach-3-runtime-toggle-endpoint-instant--no-restart)
6. [Comparison Table](#comparison-table)
7. [Recommended: Hybrid Approach](#recommended-hybrid-approach)
8. [Use Cases](#use-cases)
9. [Implementation Guide](#implementation-guide)

---

## Overview

**Problem:** During development/testing, you may want to quickly disable Bearer token authentication without rebuilding or restarting the app.

**Solution:** Three approaches, each with different speed vs. complexity tradeoffs:

| Approach | Time | Restart? | Use Case |
|----------|------|----------|----------|
| Property | 10-30s | Yes | Production deployments |
| Comment Out | 15-20s | Yes | Quick development toggle |
| **Runtime Toggle** | **0 seconds** | **No** | **Development/testing** |

---

## Three Toggle Approaches

### Quick Comparison

```
┌─────────────────────────────────────────────────────────────┐
│                    TOGGLE SPEED RANKING                     │
├─────────────────────────────────────────────────────────────┤
│ 🏃 FASTEST:    Runtime Toggle (0 seconds) - Option 3        │
│ ⚡ FAST:       Restart with property (10-30s) - Option 1    │
│ 🐢 SLOWER:     Comment out & rebuild (15-20s) - Option 2    │
└─────────────────────────────────────────────────────────────┘
```

---

## Approach 1: Property-Based Toggle (Fastest Restart)

Toggle authorization by setting a configuration property. Clean, restartable.

### Setup

**Step 1: Add property to application.yml**

```yaml
# ecs-application/src/main/resources/application.yml
security:
  bearer-auth:
    enabled: true  # Set to false to disable Bearer token requirement
```

**Step 2: Update SecurityConfiguration.java**

```java
// ecs-application/config/SecurityConfiguration.java
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Value("${security.bearer-auth.enabled:true}")
    private boolean authEnabled;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                          BearerTokenAuthenticationFilter bearerFilter) 
            throws Exception {
        
        http.csrf().disable()
            .sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS);
        
        if (authEnabled) {
            // Auth ENABLED: require Bearer token
            http.authorizeRequests()
                    .antMatchers("/actuator/**").permitAll()
                    .antMatchers("/api/files/**").authenticated()
                    .anyRequest().permitAll()
                .and()
                .addFilterBefore(bearerFilter, UsernamePasswordAuthenticationFilter.class);
        } else {
            // Auth DISABLED: allow all requests
            http.authorizeRequests()
                    .anyRequest().permitAll();
        }
        
        return http.build();
    }
}
```

### Usage

**Disable auth (requires restart):**

```bash
# Restart with property override
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--security.bearer-auth.enabled=false"
```

**Or in Docker:**

```bash
docker run -p 8080:8080 \
  -e SECURITY_BEARER_AUTH_ENABLED=false \
  ecs-local-demo:latest
```

**Check if auth is enabled (requires endpoint):**

```bash
# If auth is enabled, requests require Bearer token
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@./test.txt"
# → 400 Bad Request (missing auth)

# If auth is disabled, no token needed
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@./test.txt"
# → 201 Created (auth skipped)
```

### Pros & Cons

✅ **Pros:**
- Clean, standard Spring approach
- Easy to understand
- Works for production deployments
- Environment-variable friendly
- No code changes needed

❌ **Cons:**
- Requires application restart (10-30 seconds)
- Not ideal for rapid testing iterations

---

## Approach 2: Comment Out Filter (Simple)

Temporarily disable auth by commenting out the filter registration. Quick and simple.

### Setup

**Step 1: Modify SecurityConfiguration.java**

```java
// ecs-application/config/SecurityConfiguration.java
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                          BearerTokenAuthenticationFilter bearerFilter) 
            throws Exception {
        
        http.csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
        
        // ╔════════════════════════════════════════════════════════════╗
        // ║ TOGGLE COMMENT: Uncomment to ENABLE Bearer token auth     ║
        // ╚════════════════════════════════════════════════════════════╝
        // http.addFilterBefore(bearerFilter, UsernamePasswordAuthenticationFilter.class)
        //     .authorizeRequests()
        //         .antMatchers("/actuator/**").permitAll()
        //         .antMatchers("/api/files/**").authenticated();
        
        // When filter is commented out, allow all requests
        http.authorizeRequests().anyRequest().permitAll();
        
        return http.build();
    }
}
```

### Usage

**To disable auth:**

1. Comment out the `.addFilterBefore(...)` block
2. Rebuild: `mvn clean package -DskipTests`
3. Restart app

```bash
mvn clean package -DskipTests  # ~5-10 seconds
mvn spring-boot:run -pl ecs-application \
  -Dspring-boot.run.arguments="--spring.profiles.active=local-minio"  # ~5 seconds
```

**To enable auth:**

1. Uncomment the block
2. Rebuild and restart (same process)

### Pros & Cons

✅ **Pros:**
- Very simple and explicit
- Clear comment shows toggle point
- Minimal code changes

❌ **Cons:**
- Requires rebuild (5-10 seconds)
- Requires restart (5 seconds)
- Total time: 15-20 seconds
- Not suitable for rapid iterations

---

## Approach 3: Runtime Toggle Endpoint (Instant — No Restart!) ⚡

**FASTEST APPROACH.** Toggle auth without restart using a REST endpoint.

### Setup

**Step 1: Create Feature Flag Component**

```java
// ecs-application/config/AuthFeatureFlag.java
@Component
public class AuthFeatureFlag {
    
    @Value("${security.bearer-auth.enabled:true}")
    private volatile boolean authEnabled;
    
    public boolean isAuthEnabled() {
        return authEnabled;
    }
    
    public void setAuthEnabled(boolean enabled) {
        this.authEnabled = enabled;
    }
}
```

**Step 2: Update BearerTokenAuthenticationFilter**

```java
// ecs-inbound-adapters/security/BearerTokenAuthenticationFilter.java
@Component
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private AuthFeatureFlag authFeatureFlag;
    
    @Autowired
    private TokenExtractorPort tokenExtractor;

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) 
            throws ServletException, IOException {
        
        try {
            // ✨ KEY: Check if auth is enabled
            if (!authFeatureFlag.isAuthEnabled()) {
                // Auth disabled → skip authentication, just pass through
                filterChain.doFilter(request, response);
                return;
            }
            
            // ─── Auth enabled: proceed with normal Bearer token validation ───
            String authHeader = request.getHeader("Authorization");
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                UserContext userContext = tokenExtractor.extractUserContext(token);
                RequestContextHolder.set(userContext);
                
                log.debug("Bearer token authenticated: userId={}", userContext.getUserId());
            }
        } finally {
            filterChain.doFilter(request, response);
            RequestContextHolder.clear();
        }
    }
}
```

**Step 3: Create Admin Toggle Controller**

```java
// ecs-inbound-adapters/rest/SecurityToggleController.java
@RestController
@RequestMapping("/admin/security")
@DisplayName("Security toggle endpoint — development/testing only")
public class SecurityToggleController {
    
    @Autowired
    private AuthFeatureFlag authFeatureFlag;
    
    /**
     * Get current auth status.
     */
    @GetMapping("/auth-status")
    public Map<String, Object> getAuthStatus() {
        return Map.of(
            "bearerAuthEnabled", authFeatureFlag.isAuthEnabled(),
            "timestamp", Instant.now()
        );
    }
    
    /**
     * Toggle Bearer token authentication on/off.
     * ⚠️ WARNING: Development/testing only!
     */
    @PostMapping("/toggle-auth")
    public Map<String, Object> toggleAuth() {
        boolean newState = !authFeatureFlag.isAuthEnabled();
        authFeatureFlag.setAuthEnabled(newState);
        
        return Map.of(
            "bearerAuthEnabled", newState,
            "action", newState ? "ENABLED" : "DISABLED",
            "timestamp", Instant.now()
        );
    }
    
    /**
     * Explicitly enable Bearer token auth.
     */
    @PostMapping("/enable-auth")
    public Map<String, Object> enableAuth() {
        authFeatureFlag.setAuthEnabled(true);
        
        return Map.of(
            "bearerAuthEnabled", true,
            "action", "ENABLED",
            "timestamp", Instant.now()
        );
    }
    
    /**
     * Explicitly disable Bearer token auth.
     */
    @PostMapping("/disable-auth")
    public Map<String, Object> disableAuth() {
        authFeatureFlag.setAuthEnabled(false);
        
        return Map.of(
            "bearerAuthEnabled", false,
            "action", "DISABLED",
            "timestamp", Instant.now()
        );
    }
}
```

### Usage

**Check auth status:**

```bash
curl http://localhost:8080/admin/security/auth-status

# Response:
# {
#   "bearerAuthEnabled": true,
#   "timestamp": "2026-07-22T10:30:45.123Z"
# }
```

**Disable auth (instantly, no restart!):**

```bash
curl -X POST http://localhost:8080/admin/security/toggle-auth

# Response:
# {
#   "bearerAuthEnabled": false,
#   "action": "DISABLED",
#   "timestamp": "2026-07-22T10:30:46.456Z"
# }
```

**Verify auth is disabled — no Bearer token needed:**

```bash
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@./test.txt"

# ✅ Response: 201 Created (no auth required!)
```

**Re-enable auth:**

```bash
curl -X POST http://localhost:8080/admin/security/toggle-auth

# Response:
# {
#   "bearerAuthEnabled": true,
#   "action": "ENABLED",
#   "timestamp": "2026-07-22T10:30:47.789Z"
# }
```

**Verify auth is enabled — now Bearer token required:**

```bash
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@./test.txt"

# ❌ Response: 400 Bad Request (missing auth)

# With Bearer token:
TOKEN=$(echo -n "alice:alice@example.com" | base64)
curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./test.txt"

# ✅ Response: 201 Created (auth verified)
```

### Pros & Cons

✅ **Pros:**
- **INSTANT** — 0 seconds (no restart, no rebuild)
- Perfect for development/testing iterations
- HTTP endpoints are simple to use
- Can toggle multiple times in seconds
- Useful for debugging auth issues

❌ **Cons:**
- Adds development endpoint (security concern)
- More complex implementation
- Should NOT be exposed in production
- Requires Spring context running

---

## Comparison Table

### Speed Comparison

| Metric | Approach 1 (Property) | Approach 2 (Comment) | Approach 3 (Runtime) |
|--------|----------------------|---------------------|----------------------|
| **Toggle time** | 10-30s | 15-20s | **0 seconds** |
| **Requires restart** | Yes | Yes | **No** |
| **Requires rebuild** | No | Yes | **No** |
| **Code changes** | 1 file | 1 file | 3 files |
| **Complexity** | Low | Very Low | Medium |
| **Dev/Test friendly** | ⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Production safe** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐ (security risk) |

### When to Use Each

| Scenario | Best Approach |
|----------|---------------|
| Development/testing cycle | **Approach 3 (Runtime)** |
| Quick sanity check | **Approach 3 (Runtime)** |
| Production deployment | **Approach 1 (Property)** |
| Emergency troubleshooting | **Approach 1 (Property)** |
| Simple ad-hoc testing | **Approach 2 (Comment)** |
| Rapid iteration (10+ toggles) | **Approach 3 (Runtime)** |

---

## Recommended: Hybrid Approach

Use **Approach 3 (Runtime Toggle)** for development, **Approach 1 (Property)** for production.

### Development Setup

1. Implement Approach 3 (runtime toggle endpoint)
2. Run locally with MinIO
3. Toggle auth instantly as needed:

```bash
# Start app
mvn spring-boot:run -pl ecs-application \
  -Dspring-boot.run.arguments="--spring.profiles.active=local-minio"

# During testing, toggle as needed (0 seconds each)
curl -X POST http://localhost:8080/admin/security/toggle-auth  # Disable
curl -X POST http://localhost:8080/api/files/upload -F "file=@./test.txt"  # Test without auth
curl -X POST http://localhost:8080/admin/security/toggle-auth  # Enable
curl -X POST http://localhost:8080/api/files/upload -H "Authorization: Bearer $TOKEN" -F "file=@./test.txt"  # Test with auth
```

### Production Deployment

Use environment variables (Approach 1):

```bash
# Enable auth in production
docker run -e SECURITY_BEARER_AUTH_ENABLED=true app

# Disable auth in emergency (restart required)
docker run -e SECURITY_BEARER_AUTH_ENABLED=false app
```

---

## Use Cases

### Use Case 1: Testing Without Auth

**Scenario:** You want to test file upload without dealing with Bearer tokens.

```bash
# Disable auth instantly
curl -X POST http://localhost:8080/admin/security/toggle-auth

# Upload without Bearer token
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@./document.pdf"

# Re-enable when done
curl -X POST http://localhost:8080/admin/security/toggle-auth
```

**Time:** ⚡ 2 seconds total

---

### Use Case 2: Debugging Auth Issues

**Scenario:** Authorization is failing, and you want to isolate whether the problem is in auth or elsewhere.

```bash
# Disable auth temporarily
curl -X POST http://localhost:8080/admin/security/disable-auth

# Test if the upload works without auth
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@./test.txt"
# → If this works, problem is in auth layer
# → If this fails, problem is elsewhere

# Re-enable auth and check logs
curl -X POST http://localhost:8080/admin/security/enable-auth
```

**Time:** ⚡ Instant switching

---

### Use Case 3: Comparing With vs Without Auth

**Scenario:** You want to compare response times or behavior with and without auth.

```bash
# Measure with auth disabled
curl -X POST http://localhost:8080/admin/security/disable-auth
time curl -X POST http://localhost:8080/api/files/upload -F "file=@./test.txt"

# Measure with auth enabled
curl -X POST http://localhost:8080/admin/security/enable-auth
time curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" -F "file=@./test.txt"
```

---

### Use Case 4: Integration Tests

**Scenario:** Run integration tests with and without auth.

```bash
# Run tests with auth disabled
curl -X POST http://localhost:8080/admin/security/disable-auth
mvn test -Dtest=FileUploadIT

# Run tests with auth enabled
curl -X POST http://localhost:8080/admin/security/enable-auth
mvn test -Dtest=FileUploadWithAuthIT
```

---

## Implementation Guide

### Step-by-Step: Implement Runtime Toggle

#### Step 1: Create Feature Flag (1 minute)

Create `ecs-application/config/AuthFeatureFlag.java`:

```java
@Component
public class AuthFeatureFlag {
    @Value("${security.bearer-auth.enabled:true}")
    private volatile boolean authEnabled;
    
    public boolean isAuthEnabled() { return authEnabled; }
    public void setAuthEnabled(boolean enabled) { this.authEnabled = enabled; }
}
```

#### Step 2: Update Filter (2 minutes)

Modify `BearerTokenAuthenticationFilter.java` to check flag:

```java
@Override
protected void doFilterInternal(HttpServletRequest request, 
                               HttpServletResponse response, 
                               FilterChain filterChain) 
        throws ServletException, IOException {
    
    if (!authFeatureFlag.isAuthEnabled()) {
        filterChain.doFilter(request, response);
        return;
    }
    
    // ... rest of auth logic ...
}
```

#### Step 3: Create Controller (2 minutes)

Create `ecs-inbound-adapters/rest/SecurityToggleController.java` (see code above)

#### Step 4: Test (1 minute)

```bash
# Check status
curl http://localhost:8080/admin/security/auth-status

# Toggle
curl -X POST http://localhost:8080/admin/security/toggle-auth

# Verify toggle worked
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@./test.txt"
```

**Total time to implement:** ~5-6 minutes

---

## ⚠️ Security Considerations

### Do NOT Expose in Production

The toggle endpoint should ONLY be available in development:

```java
@PostMapping("/toggle-auth")
@ConditionalOnProperty(name = "security.toggle-endpoint-enabled", havingValue = "true")
public Map<String, Object> toggleAuth() {
    // Only enabled if property is explicitly set
    // ...
}
```

**In application.yml:**

```yaml
# Development
security:
  toggle-endpoint-enabled: true

# Production (default)
# security:
#   toggle-endpoint-enabled: false
```

### Or: Only Allow Localhost

```java
@PostMapping("/toggle-auth")
public Map<String, Object> toggleAuth(HttpServletRequest request) {
    if (!"127.0.0.1".equals(request.getRemoteAddr())) {
        throw new AccessDeniedException("Toggle endpoint only available on localhost");
    }
    // ...
}
```

---

## Summary

| Need | Solution | Time |
|------|----------|------|
| **Instant toggle (dev)** | Runtime endpoint (Approach 3) | ⚡ 0 seconds |
| **Quick restart (dev)** | Property toggle (Approach 1) | 🏃 10-30 seconds |
| **Production** | Environment variable (Approach 1) | ⏱️ Restart time |

**Recommendation for development:** Implement **Approach 3** → toggle auth instantly without restart!

---

**Last Updated:** July 22, 2026  
**Status:** Complete & Ready to Implement

