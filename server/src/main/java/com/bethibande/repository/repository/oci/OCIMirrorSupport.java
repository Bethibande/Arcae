package com.bethibande.repository.repository.oci;

import com.bethibande.repository.repository.maven.MirrorConnectionSettings;
import com.bethibande.repository.repository.mirror.StandardMirrorConfig;
import com.bethibande.repository.repository.oci.client.OCIClient;
import com.bethibande.repository.repository.oci.client.OCITokenCache;
import com.bethibande.repository.repository.oci.config.OCIRepositoryConfig;
import com.bethibande.repository.util.CallableFunction;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;
import java.util.NoSuchElementException;

public class OCIMirrorSupport implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(OCIMirrorSupport.class);

    private final OCIRepositoryConfig config;

    private final OCITokenCache tokenCache;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public OCIMirrorSupport(final OCIRepositoryConfig config) {
        this.config = config;

        try (final InstanceHandle<OCITokenCache> handle = Arc.container().instance(OCITokenCache.class)) {
            this.tokenCache = handle.get();
        }
    }

    private OCIContentInfo headFromMirror(final CallableFunction<OCIClient, OCIContentInfo, IOException> func) {
        final StandardMirrorConfig mirrorConfig = this.config.mirrorConfig();
        final List<MirrorConnectionSettings> connections = mirrorConfig.connections();

        for (int i = 0; i < connections.size(); i++) {
            final MirrorConnectionSettings connection = connections.get(i);
            final OCIClient client = new OCIClient(this.httpClient, connection, this.tokenCache);

            try {
                final OCIContentInfo info = func.call(client);
                if (info != null) return info;
            } catch (final IOException | NoSuchElementException ex) {
                LOGGER.warn("Failed to fetch object info from mirror: {}", ex.getMessage());
            }
        }
        return null;
    }

    private OCIStreamHandle getFromMirror(final CallableFunction<OCIClient, OCIStreamHandle, IOException> func) {
        final StandardMirrorConfig mirrorConfig = this.config.mirrorConfig();
        final List<MirrorConnectionSettings> connections = mirrorConfig.connections();

        for (int i = 0; i < connections.size(); i++) {
            final MirrorConnectionSettings connection = connections.get(i);
            final OCIClient client = new OCIClient(this.httpClient, connection, this.tokenCache);

            try {
                final OCIStreamHandle handle = func.call(client);
                if (handle != null) return handle;
            } catch (final IOException | NoSuchElementException ex) {
                LOGGER.warn("Failed to fetch object from mirror: {}", ex.getMessage());
            }
        }
        return null;
    }

    public OCIContentInfo headBlobFromMirror(final String namespace, final String digest) {
        return headFromMirror(client -> client.headBlob(namespace, digest));
    }

    public OCIStreamHandle getBlobFromMirror(final String namespace, final String digest) {
        return getFromMirror(client -> client.getBlob(namespace, digest));
    }

    public OCIStreamHandle getBlobRangeFromMirror(final String namespace,
                                                  final String digest,
                                                  final long start,
                                                  final long end) {
        return getFromMirror(client -> client.getBlobRange(namespace, digest, start, end));
    }

    public OCIContentInfo headManifestFromMirror(final String namespace, final String reference) {
        return headFromMirror(client -> client.headManifest(namespace, reference));
    }

    public OCIStreamHandle getManifestFromMirror(final String namespace, final String reference) {
        return getFromMirror(client -> client.getManifest(namespace, reference));
    }

    public boolean isMirroringEnabled() {
        return this.config.mirrorConfig() != null && this.config.mirrorConfig().enabled();
    }

    public boolean isStoreArtifacts() {
        return this.config.mirrorConfig() != null
                && this.config.mirrorConfig().storeArtifacts();
    }

    @Override
    public void close() {
        this.httpClient.close();
    }
}
