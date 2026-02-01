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

    public byte[] readAllBytes() {
        try (final InputStream stream = stream()) {
            final byte[] data = new byte[(int) contentLength];
            int read = 0;
            while (read < data.length) {
                final int bytesRead = stream.read(data, read, data.length - read);
                if (bytesRead == -1) break;
                read += bytesRead;
            }
            return data;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read stream bytes", e);
        }
    }
}
