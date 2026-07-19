package com.example.core.domain;

/**
 * Domain exception thrown when a storage operation fails (network error, permission denied, etc.).
 * Checked exception — callers must explicitly handle or propagate it.
 *
 * <p>No Spring dependencies.</p>
 */
public class StorageException extends Exception {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

