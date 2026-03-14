package com.bethibande.repository.k8s;

import com.bethibande.repository.util.Registration;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderCallbacks;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfigBuilder;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.virtual.threads.VirtualThreads;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

@ApplicationScoped
public class KubernetesLeaderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesLeaderService.class);

    private static final String LOCK_NAME = "repository-leader";

    @Inject
    protected KubernetesClient client;

    @ConfigProperty(name = "HOSTNAME", defaultValue = "unknown-host")
    protected String hostname;

    @ConfigProperty(name = "repository.management.port")
    protected int managementPort;

    @Inject
    @VirtualThreads
    protected Executor executor;

    @Inject
    protected KubernetesSupport kubernetesSupport;

    protected volatile String leader;

    protected long lastFailure = 0;
    protected int electionTimeoutSeconds = 5;

    private final List<LeaderCallbacks> subscriptions = new CopyOnWriteArrayList<>();

    @ConfigProperty(name = "repository.distributed")
    protected boolean distributedAllowed;

    void onStart(final @Observes StartupEvent startupEvent) {
        if (!kubernetesSupport.isEnabled()) return;
        if (!kubernetesSupport.hasLeaderElectionSupport()) {
            LOGGER.warn("Leader election not supported (possibly missing permissions), replication not supported.");
            LOGGER.warn("Set the option \"repository.scheduler.distributed\" to \"false\" to disable leader election.\"");
            return;
        }
        if (!distributedAllowed) {
            LOGGER.warn("Leader election disabled by configuration, replication not supported.");
            return;
        }

        this.executor.execute(this::startLeaderElection);
    }

    private void startLeaderElection() {
        LOGGER.info("Starting leader election for hostname: {}", hostname);

        final String namespace = client.getNamespace();

        while (true) {
            try {
                client.leaderElector()
                        .withConfig(new LeaderElectionConfigBuilder()
                                .withReleaseOnCancel()
                                .withName(LOCK_NAME)
                                .withLock(new LeaseLock(namespace, LOCK_NAME, hostname))
                                .withRenewDeadline(Duration.ofSeconds(30))
                                .withLeaseDuration(Duration.ofMinutes(1))
                                .withRetryPeriod(Duration.ofSeconds(5))
                                .withLeaderCallbacks(new LeaderCallbacks(
                                        this::onAcquireLock,
                                        this::onLostLock,
                                        this::onNewLeader
                                ))
                                .build())
                        .build()
                        .run();
            } catch (final Throwable th) {
                updateBackoffTimer();

                LOGGER.error("Leader election failed, waiting {} seconds before re-entering leader election", this.electionTimeoutSeconds, th);
                try {
                    LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(this.electionTimeoutSeconds));
                } catch (final Throwable _) {
                    break;
                }
                this.electionTimeoutSeconds = this.electionTimeoutSeconds * 2;
            }
        }
    }

    protected void updateBackoffTimer() {
        final long now = System.currentTimeMillis();
        if (this.lastFailure + TimeUnit.MINUTES.toMillis(30) < now) this.electionTimeoutSeconds = 5;
        this.lastFailure = now;
    }

    public String getLeader() {
        return this.leader;
    }

    public boolean isLeader() {
        return this.leader != null && this.leader.equals(hostname);
    }

    public Registration subscribe(final LeaderCallbacks callbacks) {
        return Registration.addAndReturn(callbacks, this.subscriptions);
    }

    private void post(final Consumer<LeaderCallbacks> consumer) {
        final List<LeaderCallbacks> subscriptions = new ArrayList<>(this.subscriptions);
        for (int i = 0; i < subscriptions.size(); i++) {
            final LeaderCallbacks callbacks = subscriptions.get(i);
            this.executor.execute(() -> consumer.accept(callbacks));
        }
    }

    private void onAcquireLock() {
        post(LeaderCallbacks::onStartLeading);
    }

    private void onLostLock() {
        post(LeaderCallbacks::onStopLeading);
    }

    private void onNewLeader(final String leader) {
        this.leader = leader;
        post(callback -> callback.onNewLeader(leader));
    }

    public <T> CompletableFuture<HttpResponse<T>> sendHTTPRequestToLeader(final String path,
                                                                          final HttpResponse.BodyHandler<T> bodyHandler,
                                                                          final Consumer<HttpRequest.Builder> customizer) {
        final InetAddress address = this.kubernetesSupport.podNameToClusterIP(this.leader);
        final String host = this.kubernetesSupport.addressToHostname(address);

        final URI uri = URI.create("http://%s:%d%s".formatted(host, this.managementPort, path));
        final HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri);

        customizer.accept(builder);

        return this.kubernetesSupport.httpClient.sendAsync(builder.build(), bodyHandler);
    }

}
