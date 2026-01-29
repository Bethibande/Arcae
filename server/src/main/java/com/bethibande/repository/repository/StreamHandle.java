package com.bethibande.repository.repository;

import java.io.IOException;
import java.io.InputStream;

public record StreamHandle(
        InputStream stream,
        String contentType,
        long contentLength
) implements AutoCloseable {

    @Override
    public void close() throws IOException {
        this.stream.close();
    }
}
