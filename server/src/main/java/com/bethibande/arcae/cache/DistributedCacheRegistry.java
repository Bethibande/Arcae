package com.bethibande.arcae.cache;

import com.bethibande.arcae.k8s.KubernetesServiceDiscovery;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class DistributedCacheRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedCacheRegistry.class);

    private final Map<String, Cache<String, ?>> caches = new HashMap<>();

    @Inject
    protected KubernetesServiceDiscovery kubernetesServiceDiscovery;

    public void register(final String name, final Cache<String, ?> cache) {
        this.caches.put(name, cache);
    }

    /**
     * Will invalidate the given key in the given cache only locally. This request will not propagate to other instances.
     * It's used internally by the {@link #invalidateAll(String, String)} logic
     *
     * @param cache The registered name of the cache
     * @param key   The key to invalidate
     */
    public void invalidateLocal(final String cache, final String key) {
        final Cache<String, ?> localCache = this.caches.get(cache);
        if (localCache != null) localCache.invalidate(key);
    }

    /**
     * Will invalidate the given key in the given cache across all known instances.
     *
     * @param cache The registered name of the cache
     * @param key   The key you wish to invalidate
     */
    public void invalidateAll(final String cache, final String key) {
        if (!this.kubernetesServiceDiscovery.isEnabled()) {
            this.invalidateLocal(cache, key);
            return;
        }

        this.kubernetesServiceDiscovery.getApiPool()
                .map(pool -> pool.broadcastHttp(
                        (baseUrl, webClient) -> webClient.deleteAbs(baseUrl + "/api/v1/cache/" + cache + "/" + key).send()
                ))
                .ifPresent(futures -> {
                    for (int i = 0; i < futures.size(); i++) {
                        futures.get(i).onFailure(ex -> LOGGER.error("Failed to invalidate key {} for cache {}", key, cache, ex));
                    }
                });
    }

}
