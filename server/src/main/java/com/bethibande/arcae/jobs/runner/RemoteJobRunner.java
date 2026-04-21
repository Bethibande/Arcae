package com.bethibande.arcae.jobs.runner;

import com.bethibande.arcae.k8s.KubernetesSupport;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;

import java.net.InetAddress;
import java.time.Instant;

public class RemoteJobRunner implements JobRunner {

    private final KubernetesSupport kubernetesSupport;

    private final String name;
    private final InetAddress remoteAddress;
    private final Instant startedAt;

    public RemoteJobRunner(final String name,
                           final InetAddress remoteAddress,
                           final Instant startedAt,
                           final KubernetesSupport kubernetesSupport) {
        this.name = name;
        this.remoteAddress = remoteAddress;
        this.startedAt = startedAt;
        this.kubernetesSupport = kubernetesSupport;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Instant getStartedAt() {
        return this.startedAt;
    }

    @Override
    public void run(final long jobId) throws RunnerQueueCapacityReached {
        final HttpResponse<Buffer> resp = this.kubernetesSupport.sendRequest(
                this.remoteAddress,
                (url, client) -> client.postAbs(url + "/api/v1/job/internal/run/%s".formatted(jobId)).send()
        ).toCompletionStage().toCompletableFuture().join(); // Yeah... Let's not talk about this :)
        // And this runs in a loop, synchronously, and not even on a virtual thread to run the pending jobs...

        if (resp.statusCode() == 503) {
            throw new RunnerQueueCapacityReached("Queue capacity exceeded");
        }

        if (resp.statusCode() != 204) {
            throw new RuntimeException("Failed to run job: %d - %s".formatted(resp.statusCode(), resp.body()));
        }
    }
}
