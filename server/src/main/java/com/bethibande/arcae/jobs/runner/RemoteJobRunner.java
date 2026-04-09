package com.bethibande.arcae.jobs.runner;

import com.bethibande.arcae.jobs.ScheduledJob;
import com.bethibande.arcae.web.api.JobEndpoint;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class RemoteJobRunner implements JobRunner {

    private final JobEndpoint jobEndpoint;
    private final String hostname;

    public RemoteJobRunner(final JobEndpoint jobEndpoint, final String hostname) {
        this.jobEndpoint = jobEndpoint;
        this.hostname = hostname;
    }

    @Override
    public void run(final ScheduledJob job) {
        final HttpRequest request = this.jobEndpoint.requestBuilder(hostname, "/api/v1/job/internal/run/%s".formatted(job.id))
                .method("POST", HttpRequest.BodyPublishers.noBody())
                .build();

        try {
            final HttpResponse<Void> response = this.jobEndpoint.getClient().send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 204) {
                throw new RuntimeException("Failed to run job: %d - %s".formatted(response.statusCode(), response.body()));
            }
        } catch (final IOException | InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }
}
