package com.bethibande.arcae.web.api;

import com.bethibande.arcae.jpa.security.OpenIDConnectLogo;
import com.bethibande.arcae.jpa.security.OpenIDConnectProvider;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.vertx.VertxContextSupport;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.jsoup.Jsoup;

import java.net.URI;
import java.time.Instant;

@ApplicationScoped
public class OpenIDConnectLogoHelper {

    public static final String CONTENT_TYPE_NOT_FOUND = "NOT_FOUND";

    @Inject
    protected WebClient webClient;

    @Transactional
    public Uni<OpenIDConnectLogo> getOrFetchLogo(final String providerName) {
        final OpenIDConnectProvider entity = OpenIDConnectProvider.find("name = ?1", providerName).firstResult();
        if (entity == null) throw new NotFoundException("Unknown provider");

        final OpenIDConnectLogo existingLogo = OpenIDConnectLogo.find("provider = ?1", entity).firstResult();
        if (existingLogo != null && !existingLogo.isExpired()) return Uni.createFrom().item(existingLogo);

        return fetchFromRemote(entity);
    }

    protected Uni<OpenIDConnectLogo> fetchFromRemote(final OpenIDConnectProvider entity) {
        final String url = (entity.discoveryUrl != null && !entity.discoveryUrl.isBlank())
                ? entity.discoveryUrl
                : entity.authorizationEndpoint;

        if (url == null) return Uni.createFrom().failure(new NotFoundException("No discovery URL"));

        final URI uri = URI.create(url);
        final String rootUrl = uri.getScheme() + "://" + uri.getHost();
        ;

        return Uni.createFrom().completionStage(
                this.webClient.getAbs(rootUrl)
                        .send()
                        .flatMap(resp -> {
                            final String html = resp.bodyAsString();
                            final String iconPath = Jsoup.parse(html).select("link[rel=icon]").attr("href");
                            if (iconPath.isBlank()) return Future.succeededFuture(null);

                            final String iconUri = iconPath.matches("^https?://.*") ? iconPath : rootUrl + iconPath;

                            return this.webClient.getAbs(iconUri).send();
                        })
                        .toCompletionStage()
        ).flatMap(resp -> storeLogo(resp, entity));
    }

    protected Uni<OpenIDConnectLogo> storeLogo(final HttpResponse<Buffer> resp, final OpenIDConnectProvider provider) {
        return VertxContextSupport.executeBlocking(() -> QuarkusTransaction.requiringNew().call(() -> {
            final OpenIDConnectLogo logo = new OpenIDConnectLogo();
            logo.provider = provider;
            logo.data = resp != null ? resp.bodyAsBuffer().getBytes() : new byte[0];
            logo.contentType = resp != null ? resp.getHeader("Content-Type") : CONTENT_TYPE_NOT_FOUND;
            logo.createdAt = Instant.now();

            logo.persist();

            return logo;
        }));
    }

}
