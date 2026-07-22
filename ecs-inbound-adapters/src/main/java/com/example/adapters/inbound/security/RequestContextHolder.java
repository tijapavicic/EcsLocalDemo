package com.example.adapters.inbound.security;

import com.example.core.domain.UserContext;

/**
 * Thread-safe utility for storing and retrieving UserContext in request scope.
 *
 * <p>Uses ThreadLocal to safely store request-scoped UserContext across the call stack.
 * Eliminates the need to pass UserContext through every method parameter.</p>
 *
 * <p><strong>Usage:</strong></p>
 * <pre>
 * // In filter/interceptor (after token extraction)
 * UserContext userContext = tokenExtractor.extractUserContext(token);
 * RequestContextHolder.set(userContext);
 *
 * // Later in controller/service
 * UserContext userContext = RequestContextHolder.get();
 *
 * // In filter's finally block (cleanup)
 * RequestContextHolder.clear();
 * </pre>
 *
 * <p><strong>Thread-Safety:</strong> Each thread maintains its own ThreadLocal context.
 * Suitable for servlet-based request handling where each request runs on a single thread.</p>
 *
 * <p><strong>Important:</strong> Always call {@code clear()} in a finally block to prevent
 * ThreadLocal leaks (especially in thread pools like Spring's DispatcherServlet).</p>
 */
public final class RequestContextHolder {

    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    /**
     * Store UserContext in ThreadLocal for the current thread.
     *
     * @param userContext the user context to store (must not be null)
     * @throws IllegalArgumentException if userContext is null
     */
    public static void set(UserContext userContext) {
        if (userContext == null) {
            throw new IllegalArgumentException("userContext must not be null");
        }
        CONTEXT.set(userContext);
    }

    /**
     * Retrieve UserContext from ThreadLocal for the current thread.
     *
     * @return the stored UserContext
     * @throws IllegalStateException if no context has been set for this thread
     */
    public static UserContext get() {
        UserContext ctx = CONTEXT.get();
        if (ctx == null) {
            throw new IllegalStateException("UserContext not set in RequestContextHolder. " +
                    "Ensure BearerTokenAuthenticationFilter has processed the request.");
        }
        return ctx;
    }

    /**
     * Retrieve UserContext if available, or null if not set.
     *
     * <p>Use this method when the context is optional (e.g., for unauthenticated endpoints).</p>
     *
     * @return the stored UserContext or null if not set
     */
    public static UserContext getIfPresent() {
        return CONTEXT.get();
    }

    /**
     * Clear UserContext from ThreadLocal.
     *
     * <p><strong>CRITICAL:</strong> Always call this in a finally block to prevent
     * ThreadLocal leaks in servlet containers.</p>
     */
    public static void clear() {
        CONTEXT.remove();
    }

    // Prevent instantiation
    private RequestContextHolder() {
        throw new AssertionError("Cannot instantiate " + RequestContextHolder.class.getSimpleName());
    }
}

