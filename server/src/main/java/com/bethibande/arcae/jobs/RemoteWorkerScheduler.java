package com.bethibande.arcae.jobs;

import com.bethibande.arcae.k8s.KubernetesSupport;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class RemoteWorkerScheduler {

    @ConfigProperty(name = "arcae.scheduler.discovery-service")
    protected String discoveryService;

    @Inject
    protected KubernetesClient client;

    private final AtomicInteger counter = new AtomicInteger(0);

    @Inject
    protected KubernetesSupport kubernetesSupport;

    public String resolvePodHostname(final String name) {
        final InetAddress address = this.kubernetesSupport.podNameToClusterIP(name);
        if (address instanceof Inet6Address) {
            return "[" + address.getHostAddress() + "]";
        }
        return address.getHostAddress();
    }

    public String getHostname() {
        try {
            final List<String> hostNames = this.kubernetesSupport.getAllReplicaPodNames();
            return hostNames.get(Math.abs(this.counter.getAndIncrement()) % hostNames.size());
        } catch (final Exception ex) {
            throw new RuntimeException("Failed to resolve discovery service addresses", ex);
        }
    }

}
