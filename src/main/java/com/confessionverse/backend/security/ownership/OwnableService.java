package com.confessionverse.backend.security.ownership;

import java.util.Optional;

/**
 * Service generic pentru entități care pot fi "owned".
 * Implementatorul trebuie să returneze și clasa entității prin getEntityClass()
 * pentru a permite înregistrarea automată în OwnershipUtil.
 */
public interface OwnableService<T extends Ownable> {
   Optional <T> getById(Long id);
    Class<T> getEntityClass();
}
