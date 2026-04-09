package com.bethibande.arcae.repository;

/**
 * An interface that extends {@link ManagedRepository} to provide additional functionality
 * for handling repository update notifications.
 */
public interface RepositoryUpdatedNotifier extends ManagedRepository {

    void processUpdate(final UpdateType type);

}
