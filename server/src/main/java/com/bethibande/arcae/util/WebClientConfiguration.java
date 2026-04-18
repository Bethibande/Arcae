package com.bethibande.arcae.util;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;

public class WebClientConfiguration {

    @ApplicationScoped
    public WebClient createWebClient(final Vertx vertx) {
        return WebClient.create(vertx);
    }

}
