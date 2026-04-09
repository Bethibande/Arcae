package com.bethibande.arcae.repository.backend;

import com.bethibande.arcae.repository.StreamHandle;

public interface RepositoryBackend {

    void put(final String path, final StreamHandle handle);

    boolean head(final String path);

    ObjectInfo headObject(final String path);

    StreamHandle get(final String path);

    StreamHandle get(final String path, final long offset, final long length);

    void delete(final String path);

}
