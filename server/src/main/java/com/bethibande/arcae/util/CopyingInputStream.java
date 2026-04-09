package com.bethibande.arcae.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class CopyingInputStream extends FilterInputStream {

    private final OutputStream sink;
    private volatile boolean sinkBroken;

    public CopyingInputStream(final InputStream in,
                              final OutputStream sink) {
        super(in);
        this.sink = sink;
    }

    protected boolean sinkValid() {
        return this.sink != null && !this.sinkBroken;
    }

    @Override
    public int read() throws IOException {
        final int data = super.read();
        if (data != -1 && sinkValid()) {
            try {
                this.sink.write(data);
            } catch (final IOException _) {
                this.sinkBroken = true;
            }
        }
        return data;
    }

    @Override
    public int read(final byte[] b) throws IOException {
        final int read = super.read(b);
        if (read != -1 && sinkValid()) {
            try {
                this.sink.write(b, 0, read);
            } catch (final IOException _) {
                this.sinkBroken = true;
            }
        }
        return read;
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        final int read = super.read(b, off, len);

        if (read != -1 && sinkValid()) {
            try {
                this.sink.write(b, off, read);
            } catch (final IOException _) {
                this.sinkBroken = true;
            }
        }

        return read;
    }

    @Override
    public void close() throws IOException {
        super.close();

        if (this.sink != null) {
            this.sink.close();
        }
    }
}
