package com.bethibande.repository.jobs;

import com.bethibande.repository.k8s.KubernetesSupport;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@ApplicationScoped
public class RemoteWorkerScheduler {

    @ConfigProperty(name = "repository.scheduler.discovery-service")
    protected String discoveryService;

    private final AtomicInteger counter = new AtomicInteger(0);

    @Inject
    protected KubernetesSupport kubernetesSupport;

    public String leaderHostname(final String leader) {
        return "%s.%s.%s.svc.%s".formatted(
                leader,
                this.discoveryService,
                kubernetesSupport.getNamespace(),
                kubernetesSupport.getClusterDomain()
        );
    }

    public Set<String> getAllHostNames() {
        try {
            return Arrays.stream(InetAddress.getAllByName(this.discoveryService))
                    .map(InetAddress::getHostAddress)
                    .collect(Collectors.toSet());
        } catch (final Exception ex) {
            throw new RuntimeException("Failed to resolve discovery service addresses", ex);
        }
    }

    public String getHostname() {
        try {
            final InetAddress[] addresses = InetAddress.getAllByName(this.discoveryService);
            return addresses[Math.abs(this.counter.getAndIncrement()) % addresses.length].getHostAddress();
        } catch (final Exception ex) {
            throw new RuntimeException("Failed to resolve discovery service addresses", ex);
        }
    }

}
