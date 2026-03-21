package com.bethibande.repository.k8s;

import com.bethibande.repository.jpa.repository.PackageManager;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReview;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReviewBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRoute;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteRuleBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.ParentReferenceBuilder;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.readiness.Readiness;
import io.quarkus.runtime.Startup;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.WebClient;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

@Startup
@ApplicationScoped
public class KubernetesSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesSupport.class);

    public static final String GATEWAY_API_GROUP = "gateway.networking.k8s.io";
    public static final String HTTP_ROUTE_NAME = "httproutes";

    public static final String COORDINATION_API_GROUP = "coordination.k8s.io";
    public static final String LEASE_NAME = "leases";

    @Inject
    protected KubernetesClient client;

    @Inject
    protected Vertx vertx;

    private String namespace;

    @ConfigProperty(name = "repository.scheduler.discovery-service")
    protected String discoveryService;

    @ConfigProperty(name = "repository.scheduler.cluster-zone")
    protected String clusterDomain;

    @ConfigProperty(name = "repository.management.port")
    protected int managementPort;

    @ConfigProperty(name = "repository.distributed")
    protected boolean distributedAllowed;

    protected WebClient webClient;

    protected boolean kubernetesSupport = true;
    protected boolean serviceDiscovery = false;
    protected boolean canManageHttpRoutes = false;
    protected boolean canElectLeader = false;
    protected boolean canListPods = false;
    protected boolean canInspectServices = false;

    private Map<String, String> labels;

    private final Cache<String, InetAddress> podIPCache = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    protected boolean canAccessApi() {
        try (final KubernetesClient timeoutClient = new KubernetesClientBuilder()
                .withConfig(new ConfigBuilder()
                        .withConnectionTimeout(200)
                        .withRequestTimeout(200)
                        .withRequestRetryBackoffLimit(0)
                        .build())
                .build()) {

            timeoutClient.getApiGroups();
            return true;
        } catch (final Throwable _) {
            return false;
        }
    }

    @PostConstruct
    protected void init() {
        if (this.client == null) return;
        if (!canAccessApi()) {
            this.kubernetesSupport = false;
            LOGGER.info("Disabled kubernetes support");
            return;
        }

        this.namespace = client.getNamespace();

        this.canManageHttpRoutes = hasPermission("create", HTTP_ROUTE_NAME, GATEWAY_API_GROUP)
                && hasPermission("delete", HTTP_ROUTE_NAME, GATEWAY_API_GROUP)
                && hasPermission("get", HTTP_ROUTE_NAME, GATEWAY_API_GROUP)
                && hasPermission("patch", HTTP_ROUTE_NAME, GATEWAY_API_GROUP)
                && hasPermission("list", HTTP_ROUTE_NAME, GATEWAY_API_GROUP);

        this.canElectLeader = hasPermission("create", LEASE_NAME, COORDINATION_API_GROUP)
                && hasPermission("get", LEASE_NAME, COORDINATION_API_GROUP)
                && hasPermission("update", LEASE_NAME, COORDINATION_API_GROUP)
                && hasPermission("delete", LEASE_NAME, COORDINATION_API_GROUP)
                && hasPermission("list", LEASE_NAME, COORDINATION_API_GROUP)
                && hasPermission("watch", LEASE_NAME, COORDINATION_API_GROUP)
                && hasPermission("patch", LEASE_NAME, COORDINATION_API_GROUP);

        this.canListPods = hasPermission("list", "pods", "")
                && hasPermission("get", "pods", "");

        this.canInspectServices = hasPermission("get", "services", "");

        this.webClient = WebClient.create(this.vertx);

        initDiscovery();
    }

    protected void initDiscovery() {
        if (!this.canListPods() || !this.canInspectServices() || !this.distributedAllowed) return;

        final Service service = this.client.services()
                .inNamespace(getNamespace())
                .withName(this.discoveryService)
                .get();

        if (service == null) {
            LOGGER.error("Discovery service {} not found", discoveryService);
            LOGGER.warn("You can specify your own DNS-only service by setting the property \"repository.scheduler.discovery-service\"");
            return;
        }

        this.labels = service.getSpec().getSelector();
        this.serviceDiscovery = true;
    }

    public String getNamespace() {
        return this.namespace;
    }

    public String getClusterDomain() {
        return this.clusterDomain;
    }

    public boolean isEnabled() {
        return this.kubernetesSupport;
    }

    public boolean hasHttpRouteSupport() {
        return this.canManageHttpRoutes;
    }

    public boolean hasLeaderElectionSupport() {
        return this.canElectLeader;
    }

    public boolean canListPods() {
        return this.canListPods;
    }

    public boolean canInspectServices() {
        return this.canInspectServices;
    }

    public boolean isServiceDiscoveryEnabled() {
        return this.serviceDiscovery;
    }

    protected String toHttpRouteName(final String repository, final PackageManager packageManager) {
        return "%s-%s".formatted(packageManager.name().toLowerCase(), repository);
    }

    public void deleteRepositoryHttpRouteIfExists(final String repository, final PackageManager packageManager) {
        this.client.resources(HTTPRoute.class)
                .inNamespace(this.namespace)
                .withName(toHttpRouteName(repository, packageManager))
                .delete();
    }

    public void createOrUpdateRepositoryHttpRoute(final String repository,
                                                  final PackageManager packageManager,
                                                  final String host,
                                                  final String targetService,
                                                  final int targetPort,
                                                  final String gateway,
                                                  final String gatewayNamespace) {
        final HTTPRoute route = new HTTPRouteBuilder()
                .withNewMetadata()
                .withName(toHttpRouteName(repository, packageManager))
                .withNamespace(this.namespace)
                .endMetadata()
                .withNewSpec()
                .withHostnames(host)
                .withParentRefs(new ParentReferenceBuilder()
                        .withName(gateway)
                        .withNamespace(gatewayNamespace)
                        .withGroup(GATEWAY_API_GROUP)
                        .withKind("Gateway")
                        .build())
                .withRules(new HTTPRouteRuleBuilder()
                        .addNewMatch()
                        .withNewPath()
                        .withType("PathPrefix")
                        .withValue("/v2")
                        .endPath()
                        .endMatch()
                        .addNewFilter()
                        .withType("URLRewrite")
                        .withNewUrlRewrite()
                        .withNewPath()
                        .withType("ReplacePrefixMatch")
                        .withReplacePrefixMatch("/repositories/%s/%s/v2".formatted(packageManager.name().toLowerCase(), repository))
                        .endPath()
                        .endUrlRewrite()
                        .endFilter()
                        .addNewBackendRef()
                        .withName(targetService)
                        .withPort(targetPort)
                        .endBackendRef()
                        .build())
                .endSpec()
                .build();

        client.resource(route).serverSideApply();
    }

    protected boolean hasPermission(final String verb, final String resource, final String group) {
        final SelfSubjectAccessReview review = new SelfSubjectAccessReviewBuilder()
                .withNewSpec()
                .withNewResourceAttributes()
                .withNamespace(this.namespace)
                .withVerb(verb)
                .withGroup(group)
                .withResource(resource)
                .endResourceAttributes()
                .endSpec()
                .build();

        final SelfSubjectAccessReview result = client.resource(review).create();

        return result.getStatus().getAllowed();
    }

    public InetAddress podNameToClusterIP(final String name) {
        return this.podIPCache.get(name, (_) -> {
            final Pod pod = this.client.pods()
                    .inNamespace(this.getNamespace())
                    .withName(name)
                    .get();

            if (pod == null) throw new IllegalStateException("Pod no longer available: " + name);
            if (!Readiness.isPodReady(pod)) throw new IllegalStateException("Pod not ready: " + name);

            final String ip = pod.getStatus().getPodIP();
            return InetAddress.ofLiteral(ip);
        });
    }

    public List<String> getAllReplicaPodNames() {
        try {
            return this.client.pods()
                    .inNamespace(this.getNamespace())
                    .withLabels(this.labels)
                    .list()
                    .getItems()
                    .stream()
                    .filter(Readiness::isPodReady)
                    .map(pod -> pod.getMetadata().getName())
                    .toList();
        } catch (final Exception ex) {
            throw new RuntimeException("Failed to resolve discovery service addresses", ex);
        }
    }

    public List<InetAddress> getAllPodClusterIPs() {
        try {
            return this.client.pods()
                    .inNamespace(this.getNamespace())
                    .withLabels(this.labels)
                    .list()
                    .getItems()
                    .stream()
                    .filter(Readiness::isPodReady)
                    .map(pod -> pod.getStatus().getPodIP())
                    .map(InetAddress::ofLiteral)
                    .toList();
        } catch (final Exception ex) {
            throw new RuntimeException("Failed to resolve discovery service addresses", ex);
        }
    }

    public String addressToHostname(final InetAddress address) {
        return address instanceof Inet6Address
                ? "[%s]".formatted(address.getHostAddress())
                : address.getHostAddress();
    }

    /**
     * Broadcasts an HTTP request to the management port of all replicas.
     * The function will supply the base URL for each endpoint and the webclient that should be used to send the request.
     */
    public List<Future<io.vertx.ext.web.client.HttpResponse<Buffer>>> broadcastHttp(final BiFunction<String, WebClient, Future<io.vertx.ext.web.client.HttpResponse<Buffer>>> fn) {
        if (!this.serviceDiscovery) return Collections.emptyList();

        final List<Future<io.vertx.ext.web.client.HttpResponse<Buffer>>> futures = new ArrayList<>();
        final List<InetAddress> addresses = getAllPodClusterIPs();
        for (int i = 0; i < addresses.size(); i++) {
            final InetAddress address = addresses.get(i);
            futures.add(this.sendRequest(address, fn));
        }

        return futures;
    }

    public Future<io.vertx.ext.web.client.HttpResponse<Buffer>> sendRequest(final InetAddress address,
                                                                            final BiFunction<String, WebClient, Future<io.vertx.ext.web.client.HttpResponse<Buffer>>> fn) {
        final String hostname = addressToHostname(address);
        final String baseUrl = "http://%s:%d".formatted(hostname, this.managementPort);

        return fn.apply(baseUrl, this.webClient);
    }

}
