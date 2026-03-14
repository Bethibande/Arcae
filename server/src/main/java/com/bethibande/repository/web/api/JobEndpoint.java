package com.bethibande.repository.web.api;

import com.bethibande.repository.jobs.*;
import com.bethibande.repository.jobs.runner.LocalJobRunner;
import com.bethibande.repository.k8s.KubernetesLeaderService;
import com.bethibande.repository.util.HttpClientUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.*;
import org.apache.http.HttpHeaders;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

@ApplicationScoped
@Path("/api/v1/job")
public class JobEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobEndpoint.class);

    @Inject
    protected JobScheduler scheduler;

    @Inject
    protected KubernetesLeaderService kubernetesLeaderService;

    @Inject
    protected ObjectMapper objectMapper;

    @Inject
    protected LocalJobRunner localJobRunner;

    @Inject
    protected RemoteWorkerScheduler remoteWorkerScheduler;

    @ConfigProperty(name = "repository.management.port")
    protected int port;

    private final HttpClient client = HttpClient.newHttpClient();

    public HttpClient getClient() {
        return this.client;
    }

    public HttpRequest.Builder requestBuilder(final String hostname, final String path) {
        final String fqDomain = this.remoteWorkerScheduler.resolvePodHostname(hostname);
        final URI uri = URI.create("http://%s:%d%s".formatted(fqDomain, this.port, path));
        return HttpRequest.newBuilder()
                .uri(uri);
    }

    public HttpRequest.Builder requestBuilder(final String path) {
        return this.requestBuilder(this.kubernetesLeaderService.getLeader(), path);
    }

    public String toJson(final Object obj) {
        try {
            return this.objectMapper.writeValueAsString(obj);
        } catch (final JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }

    protected <T> T propagate(final String path,
                              final String method,
                              final Class<T> clazz,
                              final Object requestBody) throws IOException, InterruptedException {
        final HttpRequest request = requestBuilder(path)
                .method(method, requestBody == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(this.toJson(requestBody)))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();

        final HttpResponse<T> response = this.client.send(request, HttpClientUtil.jsonBodyHandler(clazz));
        if (response.statusCode() != 200) {
            LOGGER.warn("Failed to propagate request to remote worker: {} - {}", response.statusCode(), response.body());
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
    @Path("/schedule")
    @RolesAllowed("ADMIN")
    public ScheduledJobDTO scheduleJob(final ScheduledJobDTOWithoutId dto,
                                       final @QueryParam("now") @DefaultValue("false") boolean now) throws IOException, InterruptedException {
        if (scheduler.isDistributed() && !kubernetesLeaderService.isLeader()) {
            return propagate("/api/v1/job/schedule?now=" + now, "POST", ScheduledJobDTO.class, dto);
        }

        final ScheduledJob job = QuarkusTransaction.requiringNew().call(() -> {
            final ScheduledJob entity = new ScheduledJob();
            entity.type = dto.type();
            entity.settings = dto.settings();
            entity.deleteAfterRun = dto.deleteAfterRun();
            entity.cronSchedule = dto.cronSchedule();
            entity.nextRunAt = now ? Instant.now() : null;
            entity.persist();

            return entity;
        });

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
        job.persist();

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
        job.persist();

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
