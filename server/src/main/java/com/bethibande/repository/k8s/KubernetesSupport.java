package com.bethibande.repository.k8s;

import com.bethibande.repository.jpa.repository.PackageManager;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReview;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReviewBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRoute;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteRuleBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.ParentReferenceBuilder;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Startup
@ApplicationScoped
public class KubernetesSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesSupport.class);

    public static final String GATEWAY_API_GROUP = "gateway.networking.k8s.io";
    public static final String HTTP_ROUTE_NAME = "httproutes";

    @Inject
    protected KubernetesClient client;

    private String namespace;

    protected boolean kubernetesSupport = true;
    protected boolean canManageHttpRoutes = false;

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
    }

    public boolean isEnabled() {
        return this.kubernetesSupport;
    }

    public boolean hasHttpRouteSupport() {
        return this.canManageHttpRoutes;
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

}
