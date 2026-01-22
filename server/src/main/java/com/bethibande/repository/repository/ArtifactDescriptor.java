package com.bethibande.repository.repository;

import java.io.IOException;
import java.io.InputStream;

public record ArtifactDescriptor(
        String path,
        long contentLength,
        String contentType,
        InputStream stream
) implements AutoCloseable {

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
