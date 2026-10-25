package com.bethibande.arcae.security.oidc;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches the JSON Web Key Sets of OIDC providers, used to verify the signature of ID tokens.
 * Keys are re-fetched once they are older than {@link #MAX_AGE}, or when a token references an unknown key id (key rotation).
 */
@ApplicationScoped
public class JwksCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwksCache.class);

    public static final Duration MAX_AGE = Duration.ofHours(1);
    /**
     * Minimum time between two fetches of the same key set, prevents tokens with unknown key ids from hammering the provider.
     */
    public static final Duration MIN_REFRESH_INTERVAL = Duration.ofSeconds(30);

    private record CachedKeys(List<JsonWebKey> keys, Instant fetchedAt) {

        boolean containsKeyId(final String keyId) {
            return keyId == null || this.keys.stream().anyMatch(key -> Objects.equals(key.getKeyId(), keyId));
        }

    }

    private final WebClient client;
    private final Clock clock;
    private final Map<String, CachedKeys> cache = new ConcurrentHashMap<>();

    @Inject
    public JwksCache(final Vertx vertx) {
        this(vertx, Clock.systemUTC());
    }

    JwksCache(final Vertx vertx, final Clock clock) {
        this.client = WebClient.create(vertx);
        this.clock = clock;
    }

    public Future<List<JsonWebKey>> getKeys(final String jwksUri, final String keyId) {
        final CachedKeys cached = this.cache.get(jwksUri);
        final Instant now = this.clock.instant();

        if (cached != null) {
            final Duration age = Duration.between(cached.fetchedAt(), now);
            final boolean fresh = age.compareTo(MAX_AGE) < 0;

            if (fresh && cached.containsKeyId(keyId)) return Future.succeededFuture(cached.keys());
            if (age.compareTo(MIN_REFRESH_INTERVAL) < 0) return Future.succeededFuture(cached.keys());
        }

        return fetch(jwksUri);
    }

    private Future<List<JsonWebKey>> fetch(final String jwksUri) {
        return this.client.getAbs(jwksUri)
                .putHeader("Accept", "application/json")
                .send()
                .map(response -> {
                    if (response.statusCode() != 200) {
                        LOGGER.warn("Failed to fetch JWKS from OIDC provider {}, status: {}", jwksUri, response.statusCode());
                        throw new IllegalStateException("Failed to fetch JWKS from OIDC provider");
                    }

                    try {
                        final List<JsonWebKey> keys = new JsonWebKeySet(response.bodyAsString()).getJsonWebKeys();
                        this.cache.put(jwksUri, new CachedKeys(keys, this.clock.instant()));

                        return keys;
                    } catch (final Exception ex) {
                        throw new IllegalStateException("Failed to parse JWKS from OIDC provider", ex);
                    }
                });
    }

}
