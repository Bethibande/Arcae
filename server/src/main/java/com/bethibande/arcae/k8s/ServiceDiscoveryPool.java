package com.bethibande.arcae.k8s;

import com.bethibande.arcae.util.Registration;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
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
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class ServiceDiscoveryPool implements ResourceEventHandler<Pod> {

    private final String podSelector;

    private final KubernetesSupport kubernetesSupport;

    private final List<BiConsumer<Action, Pod>> listeners = new ArrayList<>();

    private volatile SharedIndexInformer<Pod> informer;

    public ServiceDiscoveryPool(final String podSelector,
                                final KubernetesSupport kubernetesSupport) {
        this.podSelector = podSelector;
        this.kubernetesSupport = kubernetesSupport;
    }

    public List<Pod> getAll() {
        return Collections.unmodifiableList(this.informer.getStore().list());
    }

    public Registration subscribe(final BiConsumer<Action, Pod> listener) {
        final Registration registration = Registration.addAndReturn(listener, this.listeners);

        getAll().forEach(pod -> listener.accept(Action.ADDED, pod));

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
                .filter(Readiness::isPodReady)
                .map(pod -> InetAddress.ofLiteral(pod.getStatus().getPodIP()))
                .toList();

        for (int i = 0; i < addresses.size(); i++) {
            final InetAddress address = addresses.get(i);
            futures.add(this.kubernetesSupport.sendRequest(address, fn));
        }

        return futures;
    }

    protected void sendEvent(final Action action, final Pod pod) {
        this.listeners.forEach(listener -> listener.accept(action, pod));
    }

    @Override
    public void onAdd(final Pod obj) {
        sendEvent(Action.ADDED, obj);
    }

    @Override
    public void onUpdate(final Pod oldObj, final Pod newObj) {
        sendEvent(Action.MODIFIED, newObj);
    }

    @Override
    public void onDelete(final Pod obj, final boolean deletedFinalStateUnknown) {
        sendEvent(Action.DELETED, obj);
    }

    public void start() {
        this.informer = this.kubernetesSupport.getClient()
                .pods()
                .inNamespace(this.kubernetesSupport.getNamespace())
                .withLabelSelector(this.podSelector)
                .inform(this, 0);
        this.informer.start();
    }

    public void close() {
        if (this.informer != null) this.informer.close();
    }
}
