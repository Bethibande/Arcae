package com.bethibande.repository.repository.oci.client;

import com.bethibande.repository.repository.maven.MirrorConnectionSettings;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;

// Unremovable needed bacause we are lazy and injecting this bean through the arc container which the build process can't detect
@Unremovable
@ApplicationScoped
public class OCITokenCache {

    private final Cache<String, String> tokens = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    private String toKey(final MirrorConnectionSettings connection, final String namespace) {
        return connection.url() + ":" + namespace;
    }

    public void put(final MirrorConnectionSettings connection, final String namespace, final String token) {
        final String key = toKey(connection, namespace);
        this.tokens.put(key, token);
    }

    public String get(final MirrorConnectionSettings connection, final String namespace) {
        final String key = toKey(connection, namespace);
        return this.tokens.getIfPresent(key);
    }

}
