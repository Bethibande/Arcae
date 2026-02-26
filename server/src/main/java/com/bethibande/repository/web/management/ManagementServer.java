package com.bethibande.repository.web.management;

import com.bethibande.repository.k8s.KubernetesSupport;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.vertx.http.runtime.VertxHttpRecorder;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class ManagementServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManagementServer.class);

    public static final String MANAGEMENT_PORT_PROPERTY = "repository.management.port";
    public static final String DISTRIBUTED_PROPERTY = "repository.distributed";

    private final AtomicBoolean created = new AtomicBoolean(false);
    protected volatile HttpServer server;

    protected void onStartup(final @Observes StartupEvent startupEvent,
                             final @ConfigProperty(name = "quarkus.http.host") String host,
                             final @ConfigProperty(name = DISTRIBUTED_PROPERTY) boolean distributedAllowed,
                             final @ConfigProperty(name = MANAGEMENT_PORT_PROPERTY) int port,
                             final Vertx vertx,
                             final KubernetesSupport kubernetesSupport) {
        if (!distributedAllowed || !kubernetesSupport.isEnabled()) return;

        final HttpServerOptions options = new HttpServerOptions();
        options.setPort(port);
        options.setHost(host);
        options.setCompressionSupported(true);
        options.setUseAlpn(true);

        this.server = vertx.createHttpServer(options)
                .requestHandler(VertxHttpRecorder.getRootHandler())
                .listen((result) -> {
                    if (result.succeeded()) {
                        created.set(true);
                        LOGGER.info("Management server started on: http://{}:{}", host, port);
                    } else {
                        LOGGER.error("Failed to start management server", result.cause());
                    }
                });
    }

    protected void onShutdown(final @Observes ShutdownEvent shutdownEvent) {
        if (created.get()) {
            server.close();
        }
    }

}
