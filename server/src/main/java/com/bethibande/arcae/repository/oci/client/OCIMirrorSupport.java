package com.bethibande.arcae.repository.oci.client;

import com.bethibande.arcae.jpa.repository.Repository;
import com.bethibande.arcae.repository.maven.MirrorConnectionSettings;
import com.bethibande.arcae.repository.mirror.StandardMirrorConfig;
import com.bethibande.arcae.repository.oci.OCIContentInfo;
import com.bethibande.arcae.repository.oci.OCIStreamHandle;
import com.bethibande.arcae.repository.oci.config.OCIRepositoryConfig;
import com.bethibande.arcae.repository.security.AuthContext;
import com.bethibande.arcae.util.CallableFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;
import java.util.NoSuchElementException;

public class OCIMirrorSupport implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(OCIMirrorSupport.class);

    private final OCIRepositoryConfig config;
    private final Repository repository;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public OCIMirrorSupport(final OCIRepositoryConfig config, final Repository repository) {
        this.config = config;
        this.repository = repository;
    }

    private <T> T accessMirror(final CallableFunction<OCIClient, T, IOException> func) {
        final StandardMirrorConfig mirrorConfig = this.config.mirrorConfig();
        final List<MirrorConnectionSettings> connections = mirrorConfig.connections();

        for (int i = 0; i < connections.size(); i++) {
            final MirrorConnectionSettings connection = connections.get(i);
            final OCIClient client = new OCIClient(this.httpClient, connection);

            try {
                final T result = func.call(client);
                if (result != null) return result;
            } catch (final IOException | NoSuchElementException ex) {
                LOGGER.warn("Failed to fetch object info from mirror: {}", ex.getMessage());
            }
        }
        return null;
    }

    public OCIContentInfo headBlobFromMirror(final String namespace, final String digest) {
        return accessMirror(client -> client.headBlob(namespace, digest));
    }

    public OCIStreamHandle getBlobFromMirror(final String namespace, final String digest) {
        return accessMirror(client -> client.getBlob(namespace, digest));
    }

    public OCIStreamHandle getBlobRangeFromMirror(final String namespace, final String digest, final long offset, final long end) {
        return accessMirror(client -> client.getBlobRange(namespace, digest, offset, end));
    }

    public OCIContentInfo headManifestFromMirror(final String namespace, final String reference) {
        return accessMirror(client -> client.headManifest(namespace, reference));
    }

    public OCIStreamHandle getManifestFromMirror(final String namespace, final String reference) {
        return accessMirror(client -> client.getManifest(namespace, reference));
    }

    public boolean isMirroringEnabled() {
        return this.config.mirrorConfig() != null && this.config.mirrorConfig().enabled();
    }

    public boolean canMirror(final AuthContext auth) {
        return this.config.mirrorConfig().canMirror(auth, this.repository);
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
