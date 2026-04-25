package com.bethibande.arcae.repository.oci.client;

import com.bethibande.arcae.jpa.repository.Repository;
import com.bethibande.arcae.jpa.repository.RepositoryManager;
import com.bethibande.arcae.repository.StreamHandle;
import com.bethibande.arcae.repository.maven.MirrorConnectionSettings;
import com.bethibande.arcae.repository.mirror.StandardMirrorConfig;
import com.bethibande.arcae.repository.oci.OCIContentInfo;
import com.bethibande.arcae.repository.oci.OCIRepository;
import com.bethibande.arcae.repository.oci.OCIStreamHandle;
import com.bethibande.arcae.repository.oci.config.OCIRepositoryConfig;
import com.bethibande.arcae.repository.security.AuthContext;
import com.bethibande.arcae.util.CallableFunction;
import io.quarkus.narayana.jta.QuarkusTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiFunction;

public class OCIMirrorSupport implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(OCIMirrorSupport.class);

    private final RepositoryManager repositoryManager;

    private final OCIRepositoryConfig config;
    private final Repository repository;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public OCIMirrorSupport(final OCIRepositoryConfig config,
                            final Repository repository,
                            final RepositoryManager repositoryManager) {
        this.config = config;
        this.repository = repository;
        this.repositoryManager = repositoryManager;
    }

    private <T> T accessMirrorExternal(final MirrorConnectionSettings settings,
                                       final CallableFunction<OCIClient, T, IOException> func) {
        final OCIClient client = new OCIClient(this.httpClient, settings);

        try {
            return func.call(client);
        } catch (final IOException | NoSuchElementException ex) {
            LOGGER.warn("Failed to fetch object info from mirror: {}", ex.getMessage());
            return null;
        }
    }

    private <T> T accessMirrorInternal(final AuthContext userAuth,
                                       final MirrorConnectionSettings settings,
                                       final BiFunction<AuthContext, OCIRepository, T> funcInternal) {
        final OCIRepository repository = QuarkusTransaction.requiringNew().call(() -> {
            final Repository entity = this.repositoryManager.findRepositoryById(settings.repositoryId());
            if (entity == null) {
                LOGGER.warn("Mirrored repository not found, id {} on repository {}", settings.repositoryId(), this.repository.name);
                return null;
            }
            if (entity.packageManager != this.repository.packageManager) {
                LOGGER.warn("Invalid repository type for mirrored repository, is {} should be {}", entity.packageManager, this.repository.packageManager);
                return null;
            }
            if (Objects.equals(entity.id, this.repository.id)) {
                LOGGER.warn("Repository cannot mirror itself, {}", entity.name);
                return null;
            }

            return this.repositoryManager.manage(entity);
        });

        if (repository == null) return null;

        final AuthContext auth = switch (settings.authType()) {
            case APPLY_USER_AUTH -> userAuth;
            case APPLY_SYSTEM_AUTH -> AuthContext.ofSystem(userAuth);
            default -> throw new IllegalArgumentException("Unknown auth type: " + settings.authType());
        };

        return funcInternal.apply(auth, repository);
    }

    private <T> T accessMirror(final AuthContext userAuth,
                               final CallableFunction<OCIClient, T, IOException> func,
                               final BiFunction<AuthContext, OCIRepository, T> funcInternal) {
        final StandardMirrorConfig mirrorConfig = this.config.mirrorConfig();
        final List<MirrorConnectionSettings> connections = mirrorConfig.connections();

        for (int i = 0; i < connections.size(); i++) {
            final MirrorConnectionSettings connection = connections.get(i);

            final T result = connection.internal()
                    ? accessMirrorInternal(userAuth, connection, funcInternal)
                    : accessMirrorExternal(connection, func);

            if (result != null) return result;
        }
        return null;
    }

    public OCIContentInfo headBlobFromMirror(final AuthContext userAuth,
                                             final String namespace,
                                             final String digest) {
        return accessMirror(
                userAuth,
                client -> client.headBlob(namespace, digest),
                (auth, repo) -> repo.getBlobInfo(auth, namespace, digest)
        );
    }

    public StreamHandle getBlobFromMirror(final AuthContext userAuth,
                                          final String namespace,
                                          final String digest) {
        return accessMirror(
                userAuth,
                client -> {
                    final OCIStreamHandle result = client.getBlob(namespace, digest);
                    return result == null
                            ? null
                            : result.streamHandle();
                },
                (auth, repo) -> repo.getBlob(auth, namespace, digest)
        );
    }

    public StreamHandle getBlobRangeFromMirror(final AuthContext userAuth,
                                                  final String namespace,
                                                  final String digest,
                                                  final long offset,
                                                  final long end) {
        final long length = end - offset + 1;

        return accessMirror(
                userAuth,
                client -> {
                    final OCIStreamHandle result = client.getBlobRange(namespace, digest, offset, end);
                    return result == null
                            ? null
                            : result.streamHandle();
                },
                (auth, repo) -> repo.getBlob(auth, namespace, digest, offset, length)
        );
    }

    public OCIContentInfo headManifestFromMirror(final AuthContext userAuth,
                                                 final String namespace,
                                                 final String reference) {
        return accessMirror(
                userAuth,
                client -> client.headManifest(namespace, reference),
                (auth, repo) -> repo.getManifestInfo(auth, namespace, reference)
        );
    }

    public OCIStreamHandle getManifestFromMirror(final AuthContext userAuth,
                                                 final String namespace,
                                                 final String reference) {
        return accessMirror(
                userAuth,
                client -> client.getManifest(namespace, reference),
                (auth, repo) -> repo.getManifest(auth, namespace, reference)
        );
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
