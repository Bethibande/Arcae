package com.bethibande.arcae.repository.backend;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class NoCloseInputStream extends FilterInputStream {

    public NoCloseInputStream(final InputStream in) {
        super(in);
    }

    @Override
    public void close() throws IOException {
        // NOOP
    }

}
