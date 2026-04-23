package com.bethibande.arcae.jobs.scheduler;

import com.bethibande.arcae.jobs.runner.LocalJobRunner;
import com.bethibande.arcae.k8s.KubernetesLeaderService;
import com.bethibande.arcae.k8s.KubernetesServiceDiscovery;
import com.bethibande.arcae.k8s.KubernetesSupport;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

public class SchedulerConfiguration {

    @Startup
    @ApplicationScoped
    public JobScheduler createScheduler(final KubernetesSupport kubernetesSupport,
                                        final KubernetesLeaderService kubernetesLeaderService,
                                        final KubernetesServiceDiscovery kubernetesServiceDiscovery,
                                        @ConfigProperty(name = "arcae.distributed") final boolean distributedAllowed,
                                        @ConfigProperty(name = "arcae.discovery.selector.jobs") final String workerPodSelector,
                                        final LocalJobRunner localJobRunner) {
        if (kubernetesSupport.isEnabled()
                && kubernetesSupport.hasLeaderElectionSupport()
                && distributedAllowed) {
            final DistributedJobScheduler scheduler = new DistributedJobScheduler(
                    kubernetesSupport,
                    kubernetesLeaderService,
                    kubernetesServiceDiscovery,
                    workerPodSelector
            );
            scheduler.start();

            return scheduler;
        } else {
            return new LocalJobScheduler(localJobRunner);
        }
    }

}
