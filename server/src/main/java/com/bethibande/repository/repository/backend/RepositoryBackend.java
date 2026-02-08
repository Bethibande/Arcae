package com.bethibande.repository.repository.backend;

import com.bethibande.repository.repository.StreamHandle;

public interface RepositoryBackend {

    void put(final String path, final StreamHandle handle);

    boolean head(final String path);

    ObjectInfo headObject(final String path);

    StreamHandle get(final String path);

    void delete(final String path);

}
