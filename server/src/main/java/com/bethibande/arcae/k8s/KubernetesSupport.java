package com.bethibande.arcae.k8s;

import com.bethibande.arcae.jpa.repository.PackageManager;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReview;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReviewBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.*;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.readiness.Readiness;
import io.quarkus.runtime.Startup;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
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
import java.util.List;
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

    @ConfigProperty(name = "arcae.management.port")
    protected int managementPort;

    protected WebClient webClient;

    protected boolean kubernetesSupport = true;
    protected boolean canManageHttpRoutes = false;
    protected boolean canElectLeader = false;
    protected boolean canListPods = false;

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
        if (this.client == null) {
            this.kubernetesSupport = false;
            LOGGER.warn("Failed to inject kubernetes client");
            return;
        }
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
                && hasPermission("get", "pods", "")
                && hasPermission("watch", "pods", "");

        this.webClient = WebClient.create(this.vertx);
    }

    public KubernetesClient getClient() {
        return this.client;
    }

    public String getNamespace() {
        return this.namespace;
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
                                                  final String gatewayNamespace,
                                                  final String... subpaths) {
        final List<HTTPRouteRule> rules = new ArrayList<>();
        for (int i = 0; i < subpaths.length; i++) { // This subpaths logic is a quick fix for issues with the cilium gateway implementation
            final String subpath = subpaths[i];

            rules.add(new HTTPRouteRuleBuilder()
                    .addNewMatch()
                    .withNewPath()
                    .withType("PathPrefix")
                    .withValue("/%s".formatted(subpath))
                    .endPath()
                    .endMatch()
                    .addNewFilter()
                    .withType("URLRewrite")
                    .withNewUrlRewrite()
                    .withNewPath()
                    .withType("ReplacePrefixMatch")
                    .withReplacePrefixMatch("/repositories/%s/%s/%s".formatted(packageManager.name().toLowerCase(), repository, subpath))
                    .endPath()
                    .endUrlRewrite()
                    .endFilter()
                    .addNewBackendRef()
                    .withName(targetService)
                    .withPort(targetPort)
                    .endBackendRef()
                    .build());
        }

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
                .withRules(rules)
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

    public String addressToHostname(final InetAddress address) {
        return address instanceof Inet6Address
                ? "[%s]".formatted(address.getHostAddress())
                : address.getHostAddress();
    }

    public InetAddress podToAddress(final Pod pod) {
        return InetAddress.ofLiteral(pod.getStatus().getPodIP());
    }

    public Future<HttpResponse<Buffer>> sendRequest(final InetAddress address,
                                                    final BiFunction<String, WebClient, Future<HttpResponse<Buffer>>> fn) {
        final String hostname = addressToHostname(address);
        final String baseUrl = "http://%s:%d".formatted(hostname, this.managementPort);

        return fn.apply(baseUrl, this.webClient);
    }

}
