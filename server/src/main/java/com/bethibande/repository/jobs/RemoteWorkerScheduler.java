package com.bethibande.repository.jobs;

import com.bethibande.repository.k8s.KubernetesSupport;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.readiness.Readiness;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class RemoteWorkerScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteWorkerScheduler.class);

    @ConfigProperty(name = "repository.scheduler.discovery-service")
    protected String discoveryService;

    @Inject
    protected KubernetesClient client;

    private final AtomicInteger counter = new AtomicInteger(0);

    @Inject
    protected KubernetesSupport kubernetesSupport;

    protected Map<String, String> labels;

    private final Cache<String, InetAddress> podIPCache = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    @PostConstruct
    protected void init() {
        if (!this.kubernetesSupport.isEnabled()
                || !this.kubernetesSupport.canListPods()
                || !this.kubernetesSupport.canInspectServices()) return;

        final Service service = this.client.services()
                .inNamespace(this.kubernetesSupport.getNamespace())
                .withName(this.discoveryService)
                .get();

        if (service == null) {
            LOGGER.error("Discovery service {} not found", discoveryService);
            LOGGER.warn("You can specify your own DNS-only service by setting the property \"repository.scheduler.discovery-service\"");
            return;
        }

        this.labels = service.getSpec().getSelector();
    }

    private InetAddress doResolvePodIP(final String name) {
        final Pod pod = this.client.pods()
                .inNamespace(this.kubernetesSupport.getNamespace())
                .withName(name)
                .get();

        if (pod == null) throw new IllegalStateException("Pod no longer available: " + name);
        if (!Readiness.isPodReady(pod)) throw new IllegalStateException("Pod not ready: " + name);

        final String ip = pod.getStatus().getPodIP();
        return InetAddress.ofLiteral(ip);
    }

    public String resolvePodHostname(final String name) {
        final InetAddress address = this.podIPCache.get(name, this::doResolvePodIP);
        if (address instanceof Inet6Address) {
            return "[" + address.getHostAddress() + "]";
        }
        return address.getHostAddress();
    }

    public List<String> getAllHostNames() {
        try {
            return this.client.pods()
                    .inNamespace(this.kubernetesSupport.getNamespace())
                    .withLabels(this.labels)
                    .list()
                    .getItems()
                    .stream()
                    .filter(Readiness::isPodReady)
                    .map(pod -> pod.getMetadata().getName())
                    .toList();
        } catch (final Exception ex) {
            throw new RuntimeException("Failed to resolve discovery service addresses", ex);
        }
    }

    public String getHostname() {
        try {
            final List<String> hostNames = this.getAllHostNames();
            return hostNames.get(Math.abs(this.counter.getAndIncrement()) % hostNames.size());
        } catch (final Exception ex) {
            throw new RuntimeException("Failed to resolve discovery service addresses", ex);
        }
    }

}
