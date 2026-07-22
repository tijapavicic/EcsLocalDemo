package com.example.adapters.outbound.storage;

import com.example.core.ports.StorageKeyGeneratorPort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * <strong>Outbound adapter</strong> — generates user and story-scoped storage keys.
 *
 * <p><strong>Strategy:</strong> {@code users/{userId}/{storyId}/{yyyy-MM-dd}/{uuid}-{filename}}</p>
 *
 * <p><strong>Benefits of this separation:</strong></p>
 * <ul>
 *   <li>Easy to test key generation in isolation (no storage calls)</li>
 *   <li>Can swap key generation strategy by implementing {@link StorageKeyGeneratorPort}</li>
 *   <li>Keeps {@link com.example.core.usecases.FileServiceImpl} focused on orchestration</li>
 *   <li>Story-scoped organization enables per-story file isolation and management</li>
 * </ul>
 *
 * @see StorageKeyGeneratorPort
 */
@Component
public class UserScopedKeyGenerator implements StorageKeyGeneratorPort {

    @Override
    public String generateKey(String userId, String storyId, String filename) {
        Objects.requireNonNull(userId, "userId must not be null");
        if (userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        Objects.requireNonNull(storyId, "storyId must not be null");
        if (storyId.isBlank()) {
            throw new IllegalArgumentException("storyId must not be blank");
        }
        Objects.requireNonNull(filename, "filename must not be null");
        if (filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }

        // Generate date partition
        String date = LocalDate.now(ZoneId.of("UTC")).toString();

        // Generate unique prefix to avoid collisions
        String uuid = UUID.randomUUID().toString().substring(0, 8);

        // Sanitize filename to prevent path traversal and invalid characters
        String safeFilename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");

        // Return user and story-scoped key
        return String.format("users/%s/%s/%s/%s-%s", userId, storyId, date, uuid, safeFilename);
    }
}

