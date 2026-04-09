package com.bethibande.arcae.security.oidc;

import com.bethibande.arcae.jpa.security.OpenIDConnectProvider;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;

@ApplicationScoped
public class OpenIdConnectService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenIdConnectService.class);

    private final WebClient client;

    public OpenIdConnectService(final Vertx vertx) {
        this.client = WebClient.create(vertx);
    }

    public Future<OpenIDConnectOptions> fetchWellKnown(final String discoveryUrl) {
        return this.client.getAbs(discoveryUrl)
                .send()
                .map(response -> {
                    if (response.statusCode() != 200) {
                        LOGGER.error("Failed to fetch well-known config from OIDC provider {}, status {}", discoveryUrl, response.statusCode());
                        throw new RuntimeException("Failed to fetch well-known config from OIDC provider");
                    }

                    return response.bodyAsJsonObject()
                            .mapTo(OpenIDConnectOptions.class);
                }).onFailure(error -> LOGGER.error("Failed to fetch well-known config from OIDC provider", error));
    }

    public Future<String> getSubjectFromCode(final String code,
                                             final OpenIDConnectProvider provider,
                                             final String redirectUri) {
        final String credentials = Base64.getEncoder().encodeToString((provider.clientId + ":" + provider.clientSecret).getBytes());

        return this.client.postAbs(provider.tokenEndpoint)
                        .putHeader("Authorization", "Basic " + credentials)
                        .putHeader("Accept", "application/json")
                        .sendForm(MultiMap.caseInsensitiveMultiMap()
                                .add("grant_type", "authorization_code")
                                .add("code", code)
                                .add("redirect_uri", redirectUri)
                                .add("client_id", provider.clientId)
                                .add("client_secret", provider.clientSecret))
                .map(response -> {
                    if (response.statusCode() != 200) {
                        LOGGER.warn("Failed to fetch access token from OIDC provider, status: {} - {}", response.statusCode(), response.bodyAsString());
                        throw new IllegalStateException("Failed to fetch access token from OIDC provider");
                    }
                    return response.bodyAsJsonObject().getString("access_token");
                })
                .flatMap(token -> this.client.getAbs(provider.userInfoEndpoint)
                        .putHeader("Authorization", "Bearer " + token)
                        .putHeader("Accept", "application/json")
                        .send())
                .map(response -> {
                    if (response.statusCode() != 200) {
                        LOGGER.warn("Failed to fetch user info from OIDC provider, status: {} - {}", response.statusCode(), response.bodyAsString());
                        throw new IllegalStateException("Failed to fetch user info from OIDC provider");
                    }

                    final JsonObject body = response.bodyAsJsonObject();
                    if (body.containsKey("sub")) return body.getString("sub");
                    final Object id = body.getValue("id");
                    if (id != null) return id.toString();

                    throw new IllegalStateException("Failed to retrieve subject from OIDC provider");
                })
                .recover(error -> {
                    LOGGER.error("Failed to retrieve subject from OIDC provider", error);
                    return Future.failedFuture(error);
                });
    }

}
