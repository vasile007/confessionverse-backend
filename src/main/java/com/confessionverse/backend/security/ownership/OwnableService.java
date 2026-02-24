package com.confessionverse.backend.security.ownership;

import java.util.Optional;

/**
 * Generic service for entities that can be "owned".
 * The implementer must also return the entity class via getEntityClass()
 * to allow automatic registration in OwnershipUtil.
 */
public interface OwnableService<T extends Ownable> {
   Optional <T> getById(Long id);
    Class<T> getEntityClass();
}
