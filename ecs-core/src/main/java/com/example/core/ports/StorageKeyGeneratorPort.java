package com.example.core.ports;

/**
 * <strong>Outbound port</strong> — abstraction for storage key generation strategy.
 *
 * <p>Supports multiple key generation strategies without modifying {@link FileServiceImpl}.
 * Implemented by adapters (e.g., {@code UserScopedKeyGenerator}).</p>
 *
 * <p><strong>Open/Closed Principle:</strong> New key generation strategies can be added
 * by implementing this port without modifying existing code.</p>
 *
 * <p>No Spring dependencies. Pure Java interface.</p>
 */
public interface StorageKeyGeneratorPort {

    /**
     * Generate a storage key for a user's file upload within a story context.
     *
     * <p><strong>Pre-conditions (validated by caller):</strong></p>
     * <ul>
     *   <li>userId must not be blank</li>
     *   <li>storyId must not be blank</li>
     *   <li>filename must not be blank</li>
     * </ul>
     *
     * @param userId   authenticated user ID (e.g., "alice")
     * @param storyId  story context ID (e.g., "story-123")
     * @param filename original filename (e.g., "report.pdf")
     * @return generated storage key (e.g., "users/alice/story-123/2024-01-15/abc123-report.pdf")
     * @throws IllegalArgumentException if any parameter is blank
     */
    String generateKey(String userId, String storyId, String filename);
}

