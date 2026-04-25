package com.bethibande.arcae.repository.maven;

import com.bethibande.arcae.jpa.repository.PackageManager;
import com.bethibande.arcae.jpa.repository.Repository;
import com.bethibande.arcae.jpa.repository.RepositoryManager;
import com.bethibande.arcae.repository.StreamHandle;
import com.bethibande.arcae.repository.mirror.StandardMirrorConfig;
import com.bethibande.arcae.repository.security.AuthContext;
import io.quarkus.narayana.jta.QuarkusTransaction;
import org.apache.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * This class contains some utility methods for working with maven mirrors.
 * Mainly for pulling files from mirrors and retrieving some configuration values.
 */
public class MavenMirrorSupport implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenMirrorSupport.class);

    private final MavenRepository repository;
    private final StandardMirrorConfig config;

    private final RepositoryManager repositoryManager;

    private final HttpClient client = HttpClient.newHttpClient();

    public MavenMirrorSupport(final MavenRepository repository,
                              final StandardMirrorConfig config,
                              final RepositoryManager repositoryManager) {
        this.repository = repository;
        this.config = config;
        this.repositoryManager = repositoryManager;
    }

    public boolean enabled() {
        return this.config != null && this.config.enabled();
    }

    public boolean shouldStore(final AuthContext auth) {
        return this.config.canMirror(auth, this.repository.getInfo())
                && this.config.storeArtifacts();
    }

    protected StreamHandle getFromExternalMirrorConnection(final String path, final MirrorConnectionSettings mirror) {
        try {
            final String remoteUrl = "%s/%s".formatted(mirror.url().replaceAll("/+$", ""), path);

            final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(remoteUrl))
                    .GET();

            switch (mirror.authType()) {
                case BASIC -> {
                    final String value = "Basic " + Base64.getEncoder().encodeToString("%s:%s".formatted(mirror.username(), mirror.password()).getBytes());
                    builder.header(HttpHeaders.AUTHORIZATION, value);
                }
                case BEARER -> builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + mirror.password());
            }

            final HttpRequest request = builder.build();

            final HttpResponse<InputStream> response = this.client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 404) return null;

            final InputStream stream = response.body();
            final long contentLength = response.headers()
                    .firstValueAsLong(HttpHeaders.CONTENT_LENGTH)
                    .orElse(-1L);
            final String contentType = response.headers()
                    .firstValue(HttpHeaders.CONTENT_TYPE)
                    .orElse(null);

            return new StreamHandle(stream, contentType, contentLength);
        } catch (final Throwable th) {
            LOGGER.error("Failed to fetch remote artifact for path {} on repository {}", path, this.repository.getInfo().name, th);
            return null;
        }

    }

    protected StreamHandle getFromInternalMirrorConnection(final AuthContext userAuth,
                                                           final String path,
                                                           final MirrorConnectionSettings mirror) {
        final MavenRepository repository = QuarkusTransaction.requiringNew().call(() -> {
            final Repository entity = this.repositoryManager.findRepositoryById(mirror.repositoryId());
            if (entity == null) {
                LOGGER.warn("Repository not found for internal mirror connection: {} on repository {}", mirror.repositoryId(), this.repository.getInfo().name);
                return null;
            }
            if (entity.packageManager != PackageManager.MAVEN) {
                LOGGER.error("Invalid repository for internal mirror connection: {}, type {}", mirror.repositoryId(), entity.packageManager.name());
                return null;
            }
            if (Objects.equals(entity.id, this.repository.getInfo().id)) {
                LOGGER.warn("Repository cannot mirror itself, {}", entity.name);
                return null;
            }

            return this.repositoryManager.manage(entity);
        });

        if (repository == null) return null;

        final AuthContext auth = switch (mirror.authType()) {
            case APPLY_USER_AUTH -> userAuth;
            case APPLY_SYSTEM_AUTH -> AuthContext.ofSystem(userAuth);
            default -> throw new IllegalStateException("Unknown auth type for mirror connection: " + mirror.authType());
        };

        return repository.get(auth, path);
    }

    protected StreamHandle getFromMirrorConnection(final AuthContext auth,
                                                   final String path,
                                                   final MirrorConnectionSettings mirror) {
        if (mirror.internal()) {
            return this.getFromInternalMirrorConnection(auth, path, mirror);
        }
        return this.getFromExternalMirrorConnection(path, mirror);

    }

    public StreamHandle getFileFromMirror(final AuthContext auth, final String path) {
        final List<MirrorConnectionSettings> connections = this.config.connections();

        for (int i = 0; i < connections.size(); i++) {
            final MirrorConnectionSettings mirror = connections.get(i);
            final StreamHandle result = this.getFromMirrorConnection(auth, path, mirror);
            if (result != null) return result;
        }

        return null;
    }

    @Override
    public void close() {
        this.client.close();
    }
}
