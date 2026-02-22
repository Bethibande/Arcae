package com.bethibande.repository.web.api;

import com.bethibande.repository.jobs.*;
import com.bethibande.repository.jobs.runner.LocalJobRunner;
import com.bethibande.repository.k8s.KubernetesLeaderService;
import com.bethibande.repository.security.SystemAuthentication;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.security.UnauthorizedException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.*;
import org.apache.http.HttpHeaders;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@ApplicationScoped
@Path("/api/v1/job")
public class JobEndpoint {

    @Inject
    protected JobScheduler scheduler;

    @Inject
    protected KubernetesLeaderService kubernetesLeaderService;

    @Inject
    protected ObjectMapper objectMapper;

    @Inject
    protected SystemAuthentication systemAuthentication;

    @Inject
    protected LocalJobRunner localJobRunner;

    @Inject
    protected RemoteWorkerScheduler remoteWorkerScheduler;

    @ConfigProperty(name = "quarkus.http.port")
    protected int port;

    private final HttpClient client = HttpClient.newHttpClient();

    public HttpClient getClient() {
        return this.client;
    }

    public HttpRequest.Builder requestBuilder(final String hostname, final String path) {
        final URI uri = URI.create("http://%s:%d%s".formatted(hostname, this.port, path));
        return HttpRequest.newBuilder()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(this.systemAuthentication.getAccessToken().token));
    }

    public HttpRequest.Builder requestBuilder(final String path) {
        final String leaderHostname = this.remoteWorkerScheduler.leaderHostname(this.kubernetesLeaderService.getLeader());
        return this.requestBuilder(leaderHostname, path);
    }

    public String toJson(final Object obj) {
        try {
            return this.objectMapper.writeValueAsString(obj);
        } catch (final JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }

    protected <T> HttpResponse.BodyHandler<T> jsonBodyHandler(final Class<T> clazz) {
        return (_) -> HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8), (json) -> {
            try {
                return this.objectMapper.readValue(json, clazz);
            } catch (final JsonProcessingException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    protected <T> T propagate(final String path, final String method, final Class<T> clazz, final Object requestBody) throws IOException, InterruptedException {
        final HttpRequest request = requestBuilder(path)
                .method(method, requestBody == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(this.toJson(requestBody)))
                .build();

        final HttpResponse<T> response = this.client.send(request, jsonBodyHandler(clazz));
        if (response.statusCode() != 200) {
            throw new WebApplicationException(response.statusCode());
        }

        return response.body();
    }

    @POST
    @Transactional
    @RolesAllowed("SYSTEM")
    @Path("/internal/run/{jobId}")
    public void run(final @PathParam("jobId") long jobId) {
        final ScheduledJob job = ScheduledJob.findById(jobId);
        if (job == null) throw new NotFoundException("Job not found");

        this.localJobRunner.run(job);
    }

    @POST
    @Transactional
    @Path("/schedule")
    @RolesAllowed("ADMIN")
    public ScheduledJobDTO scheduleJob(final ScheduledJobDTOWithoutId dto) throws IOException, InterruptedException {
        if (scheduler.isDistributed() && !kubernetesLeaderService.isLeader()) {
            return propagate("/api/v1/job/schedule", "POST", ScheduledJobDTO.class, dto);
        }

        final ScheduledJob job = new ScheduledJob();
        job.type = dto.type();
        job.settings = dto.settings();
        job.deleteAfterRun = dto.deleteAfterRun();
        job.cronSchedule = dto.cronSchedule();

        scheduler.schedule(job, Instant.now());

        return ScheduledJobDTO.from(job);
    }

    @PUT
    @Transactional
    @RolesAllowed("ADMIN")
    public ScheduledJobDTO updateJob(final ScheduledJobDTO dto) throws IOException, InterruptedException {
        if (scheduler.isDistributed() && !kubernetesLeaderService.isLeader()) {
            return propagate("/api/v1/job", "PUT", ScheduledJobDTO.class, dto);
        }

        final ScheduledJob job = ScheduledJob.findById(dto.id());
        if (job == null) throw new NotFoundException("Job not found");

        job.type = dto.type();
        job.settings = dto.settings();
        job.deleteAfterRun = dto.deleteAfterRun();
        job.cronSchedule = dto.cronSchedule();

        scheduler.schedule(job, Instant.now());

        return ScheduledJobDTO.from(job);
    }

    @PUT
    @Transactional
    @RolesAllowed("ADMIN")
    @Path("/{id}/nextRunAt")
    public ScheduledJobDTO setNextExecution(final @PathParam("id") long id, final Instant nextRunAt) throws IOException, InterruptedException {
        if (scheduler.isDistributed() && !kubernetesLeaderService.isLeader()) {
            return propagate("/api/v1/job/%s/nextRunAt".formatted(id), "PUT", ScheduledJobDTO.class, nextRunAt);
        }

        final ScheduledJob job = ScheduledJob.findById(id);
        if (job == null) throw new NotFoundException("Job not found");

        job.nextRunAt = nextRunAt;
        scheduler.schedule(job, Instant.now());

        return ScheduledJobDTO.from(job);
    }

    @GET
    @RolesAllowed("ADMIN")
    public PagedResponse<ScheduledJobDTO> list(final @QueryParam("p") @Min(0) @DefaultValue("0") int page,
                                               final @QueryParam("s") @Min(1) @Max(100) @DefaultValue("20") int pageSize) {
        final PanacheQuery<ScheduledJob> query = ScheduledJob.findAll().page(page, pageSize);
        return PagedResponse.from(query, page, ScheduledJobDTO::from);
    }

}
