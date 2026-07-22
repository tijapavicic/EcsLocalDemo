package com.example.core.ports;

import java.util.Optional;

/**
 * Generic repository port for CRUD operations on any entity.
 *
 * <p><b>Generics Benefit:</b> Single interface provides type-safe CRUD for any entity
 * (UserContext, StorageObject, FileMetadata, etc.)
 *
 * <p><b>Type Parameters:</b>
 * <ul>
 *   <li>E = entity type (e.g., StorageObject, FileMetadata)</li>
 *   <li>ID = identifier type (e.g., String, Long, UUID)</li>
 * </ul>
 *
 * @param <E> the entity type
 * @param <ID> the entity identifier type
 * @since 3.0
 */
public interface GenericRepository<E, ID> {

    /**
     * Store an entity.
     *
     * @param entity the entity to store (not null)
     * @return the stored entity with ID populated
     */
    E save(E entity);

    /**
     * Retrieve an entity by ID.
     *
     * @param id the entity identifier
     * @return optional containing entity, empty if not found
     */
    Optional<E> findById(ID id);

    /**
     * Delete an entity by ID.
     *
     * @param id the entity identifier
     * @return true if deleted, false if not found
     */
    boolean deleteById(ID id);

    /**
     * Update an entity.
     *
     * @param entity the entity to update
     * @return the updated entity
     */
    E update(E entity);
}

