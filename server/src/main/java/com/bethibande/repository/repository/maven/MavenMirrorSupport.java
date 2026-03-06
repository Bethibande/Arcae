package com.bethibande.repository.repository.maven;

import com.bethibande.repository.repository.StreamHandle;
import com.bethibande.repository.repository.mirror.StandardMirrorConfig;
import com.bethibande.repository.repository.security.AuthContext;
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

/**
 * This class contains some utility methods for working with maven mirrors.
 * Mainly for pulling files from mirrors and retrieving some configuration values.
 */
public class MavenMirrorSupport implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenMirrorSupport.class);

    private final MavenRepository repository;
    private final StandardMirrorConfig config;

    private final HttpClient client = HttpClient.newHttpClient();

    public MavenMirrorSupport(final MavenRepository repository, final StandardMirrorConfig config) {
        this.repository = repository;
        this.config = config;
    }

    public boolean enabled() {
        return this.config != null && this.config.enabled();
    }

    public boolean shouldStore(final AuthContext auth) {
        return this.config.canMirror(auth, this.repository.getInfo());
    }

    protected StreamHandle getFromMirrorConnection(final String path, final MirrorConnectionSettings mirror) {
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

    public StreamHandle getFileFromMirror(final AuthContext auth, final String path) {
        final List<MirrorConnectionSettings> connections = this.config.connections();

        for (int i = 0; i < connections.size(); i++) {
            final MirrorConnectionSettings mirror = connections.get(i);
            final StreamHandle result = this.getFromMirrorConnection(path, mirror);
            if (result != null) return result;
        }

        return null;
    }

    @Override
    public void close() {
        this.client.close();
    }
}
