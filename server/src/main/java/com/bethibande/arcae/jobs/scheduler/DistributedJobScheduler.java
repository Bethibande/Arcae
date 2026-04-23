package com.bethibande.arcae.jobs.scheduler;

import com.bethibande.arcae.jobs.runner.JobRunner;
import com.bethibande.arcae.jobs.runner.RemoteJobRunner;
import com.bethibande.arcae.k8s.KubernetesLeaderService;
import com.bethibande.arcae.k8s.KubernetesServiceDiscovery;
import com.bethibande.arcae.k8s.KubernetesSupport;
import com.bethibande.arcae.k8s.ServiceDiscoveryPool;
import com.bethibande.arcae.web.api.SystemEndpoint;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.readiness.Readiness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

public class DistributedJobScheduler extends AbstractJobScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedJobScheduler.class);

    private final ReentrantLock lock = new ReentrantLock();
    private final List<JobRunner> workers = new ArrayList<>();

    private final  KubernetesSupport kubernetesSupport;
    private final  KubernetesLeaderService kubernetesLeaderService;
    private final  KubernetesServiceDiscovery kubernetesServiceDiscovery;

    private final  String workerPodSelector;

    public DistributedJobScheduler(final KubernetesSupport kubernetesSupport,
                                   final KubernetesLeaderService kubernetesLeaderService,
                                   final KubernetesServiceDiscovery kubernetesServiceDiscovery,
                                   final String workerPodSelector) {
        this.kubernetesSupport = kubernetesSupport;
        this.kubernetesLeaderService = kubernetesLeaderService;
        this.kubernetesServiceDiscovery = kubernetesServiceDiscovery;
        this.workerPodSelector = workerPodSelector;
    }

    public void start() {
        if (!this.kubernetesServiceDiscovery.isEnabled()) {
            LOGGER.error("Failed to start distributed scheduler, service discovery is not available.");
            return;
        }

        this.kubernetesServiceDiscovery.createPool("jobs", workerPodSelector)
                .subscribe(this::onPodChange);
    }

    protected void onPodChange(final Watcher.Action action, final Pod pod) {
        final JobRunner existingRunner = this.workers.stream()
                .filter(worker -> Objects.equals(worker.getName(), pod.getMetadata().getName()))
                .findAny()
                .orElse(null);

        if (Readiness.isPodReady(pod) && existingRunner == null) {
            this.kubernetesSupport.sendRequest(
                    InetAddress.ofLiteral(pod.getStatus().getPodIP()),
                    (url, client) -> client.getAbs(url + "/api/v1/system/status").send()
            ).onSuccess(resp -> {
                final SystemEndpoint.AppStatus status = resp.bodyAsJson(SystemEndpoint.AppStatus.class);

                if (status == null) {
                    LOGGER.error("Failed to fetch status of runner {}: {}", pod.getMetadata().getName(), resp.statusCode());
                    return;
                }

                this.lock.lock();
                try {
                    this.workers.add(new RemoteJobRunner(
                            pod.getMetadata().getName(),
                            InetAddress.ofLiteral(pod.getStatus().getPodIP()),
                            status.startupTime(),
                            this.kubernetesSupport
                    ));
                } finally {
                    this.lock.unlock();
                }
            }).onFailure(ex -> LOGGER.error("Failed to fetch status of runner {}", pod.getMetadata().getName(), ex));
        } else if(!Readiness.isPodReady(pod) && existingRunner != null) {
            this.workers.remove(existingRunner);
        }
    }

    @Override
    public boolean isActive() {
        return this.kubernetesLeaderService.isLeader();
    }

    @Override
    protected JobRunner getWorkerByName(final String name) {
        return this.workers.stream()
                .filter(worker -> Objects.equals(worker.getName(), name))
                .findAny()
                .orElse(null);
    }

    @Override
    protected List<JobRunner> getWorkers() {
        return Collections.unmodifiableList(this.workers);
    }
}
