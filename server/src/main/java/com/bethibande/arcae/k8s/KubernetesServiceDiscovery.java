package com.bethibande.arcae.k8s;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Startup
@ApplicationScoped
public class KubernetesServiceDiscovery {

    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesServiceDiscovery.class);

    public static final String API_POOL_NAME = "api";

    private final Map<String, ServiceDiscoveryPool> pools = new HashMap<>();

    @Inject
    protected KubernetesSupport kubernetesSupport;

    @ConfigProperty(name = "arcae.distributed")
    protected boolean distributedAllowed;

    @ConfigProperty(name = "arcae.discovery.selector.api")
    protected String apiSelectorLabels;

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    @PostConstruct
    protected void init() {
        if (isEnabled()) {
            createPool(API_POOL_NAME, apiSelectorLabels);
        }
    }

    public boolean isEnabled() {
        return this.kubernetesSupport.isEnabled()
                && this.kubernetesSupport.canListPods()
                && this.distributedAllowed;
    }

    void onStop(@Observes final ShutdownEvent ev) {
        this.shuttingDown.set(true);

        this.pools.values().forEach(ServiceDiscoveryPool::close);
    }

    public Optional<ServiceDiscoveryPool> getApiPool() {
        return getPool(API_POOL_NAME);
    }

    public Optional<ServiceDiscoveryPool> getPool(final String name) {
        return Optional.ofNullable(this.pools.get(name));
    }

    public ServiceDiscoveryPool createPool(final String name, final String podSelector) {
        if (!isEnabled()) throw new IllegalStateException("Distributed mode is not enabled");
        if (this.pools.containsKey(name)) throw new IllegalStateException("Pool already exists");

        final ServiceDiscoveryPool pool = new ServiceDiscoveryPool(
                podSelector,
                this.shuttingDown,
                this.kubernetesSupport
        );

        LOGGER.info("Starting service discovery pool {}", name);
        pool.start();

        this.pools.put(name, pool);

        return pool;
    }

}
