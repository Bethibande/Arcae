package com.bethibande.arcae.k8s;

import com.bethibande.arcae.util.Registration;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.readiness.Readiness;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class ServiceDiscoveryPool implements Watcher<Pod> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceDiscoveryPool.class);

    public record PoolEntry(
            String podName,
            InetAddress address,
            boolean ready
    ) {
    }

    private final String podSelector;

    private final AtomicBoolean shutdownSignal;

    private final KubernetesSupport kubernetesSupport;

    private final List<PoolEntry> pods = new ArrayList<>();

    private final List<BiConsumer<Watcher.Action, PoolEntry>> listeners = new ArrayList<>();

    private volatile Watch watch;

    public ServiceDiscoveryPool(final String podSelector,
                                final AtomicBoolean shutdownSignal,
                                final KubernetesSupport kubernetesSupport) {
        this.podSelector = podSelector;
        this.shutdownSignal = shutdownSignal;
        this.kubernetesSupport = kubernetesSupport;
    }

    public List<PoolEntry> getAll() {
        return Collections.unmodifiableList(this.pods);
    }

    public Registration subscribe(final BiConsumer<Watcher.Action, PoolEntry> listener) {
        final Registration registration = Registration.addAndReturn(listener, this.listeners);

        new ArrayList<>(this.pods).forEach(pod -> listener.accept(Watcher.Action.ADDED, pod));

        return registration;
    }

    /**
     * Broadcasts an HTTP request to the management port of all replicas.
     * The function will supply the base URL for each endpoint and the webclient that should be used to send the request.
     */
    public List<Future<HttpResponse<Buffer>>> broadcastHttp(final BiFunction<String, WebClient, Future<HttpResponse<Buffer>>> fn) {
        final List<Future<HttpResponse<Buffer>>> futures = new ArrayList<>();
        final List<InetAddress> addresses = getAll()
                .stream()
                .filter(PoolEntry::ready)
                .map(PoolEntry::address)
                .toList();

        for (int i = 0; i < addresses.size(); i++) {
            final InetAddress address = addresses.get(i);
            futures.add(this.kubernetesSupport.sendRequest(address, fn));
        }

        return futures;
    }

    @Override
    public void eventReceived(final Action action, final Pod resource) {
        final String podIp = resource.getStatus().getPodIP();
        final PoolEntry entry = new PoolEntry(
                resource.getMetadata().getName(),
                podIp != null ? InetAddress.ofLiteral(podIp) : null,
                Readiness.isPodReady(resource)
        );

        pods.removeIf(e -> e.podName.equals(entry.podName));
        if (action == Action.ADDED || action == Action.MODIFIED) pods.add(entry);
    }

    public void start() {
        this.watch = this.kubernetesSupport.getClient()
                .pods()
                .inNamespace(this.kubernetesSupport.getNamespace())
                .withLabelSelector(this.podSelector)
                .watch(this);
    }

    @Override
    public void onClose(final WatcherException cause) {
        if (shutdownSignal.get()) return;

        LOGGER.error("Watcher closed unexpectedly, restarting...", cause);
        this.start();
    }

    public void close() {
        if (this.watch != null) this.watch.close();
    }
}
