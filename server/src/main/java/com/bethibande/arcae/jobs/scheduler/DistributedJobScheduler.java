package com.bethibande.arcae.jobs.scheduler;

import com.bethibande.arcae.jobs.runner.JobRunner;
import com.bethibande.arcae.jobs.runner.RemoteJobRunner;
import com.bethibande.arcae.k8s.KubernetesLeaderService;
import com.bethibande.arcae.k8s.KubernetesServiceDiscovery;
import com.bethibande.arcae.k8s.KubernetesSupport;
import com.bethibande.arcae.k8s.ServiceDiscoveryPool;
import com.bethibande.arcae.web.api.SystemEndpoint;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    protected void onPodChange(final Watcher.Action action, final ServiceDiscoveryPool.PoolEntry pod) {
        final JobRunner existingRunner = this.workers.stream()
                .filter(worker -> Objects.equals(worker.getName(), pod.podName()))
                .findAny()
                .orElse(null);

        if (pod.ready() && existingRunner == null) {
            this.kubernetesSupport.sendRequest(
                    pod.address(),
                    (url, client) -> client.getAbs(url + "/api/v1/system/status").send()
            ).onSuccess(resp -> {
                final SystemEndpoint.AppStatus status = resp.bodyAsJson(SystemEndpoint.AppStatus.class);

                if (status == null) {
                    LOGGER.error("Failed to fetch status of runner {}: {}", pod.podName(), resp.statusCode());
                    return;
                }

                this.lock.lock();
                try {
                    this.workers.add(new RemoteJobRunner(
                            pod.podName(),
                            pod.address(),
                            status.startupTime(),
                            this.kubernetesSupport
                    ));
                } finally {
                    this.lock.unlock();
                }
            }).onFailure(ex -> LOGGER.error("Failed to fetch status of runner {}", pod.podName(), ex));
        } else if(!pod.ready() && existingRunner != null) {
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
