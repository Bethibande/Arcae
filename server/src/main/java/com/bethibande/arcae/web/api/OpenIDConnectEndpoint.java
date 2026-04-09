package com.bethibande.arcae.web.api;

import com.bethibande.arcae.jpa.security.OpenIDConnectProvider;
import com.bethibande.arcae.jpa.security.OpenIDConnection;
import com.bethibande.arcae.jpa.security.*;
import com.bethibande.arcae.jpa.user.User;
import com.bethibande.arcae.security.oidc.OpenIDConnectOptions;
import com.bethibande.arcae.security.oidc.OpenIdConnectService;
import com.bethibande.arcae.web.AuthenticatedUser;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.security.Authenticated;
import io.quarkus.vertx.VertxContextSupport;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Path("/api/v1/oidc")
public class OpenIDConnectEndpoint {

    public static final String OIDC_STATE_COOKIE_NAME = "oidc_state";

    @Inject
    protected OpenIdConnectService service;

    @ConfigProperty(name = "external.host")
    protected String host;

    @Inject
    protected AuthenticationEndpoint authenticationEndpoint;

    @Inject
    protected AuthenticatedUser authenticatedUser;

    @POST
    @Transactional
    @Path("/provider")
    @RolesAllowed("ADMIN")
    public OpenIDConnectProviderDTO createProvider(final OpenIDConnectProviderDTOWithoutId dto) {
        final OpenIDConnectProvider entity = new OpenIDConnectProvider();
        entity.name = dto.name();
        entity.clientId = dto.clientId();
        entity.clientSecret = dto.clientSecret();
        entity.discoveryUrl = dto.discoveryUrl();
        entity.authorizationEndpoint = dto.authorizationEndpoint();
        entity.tokenEndpoint = dto.tokenEndpoint();
        entity.userInfoEndpoint = dto.userInfoEndpoint();
        entity.persist();

        return OpenIDConnectProviderDTO.from(entity);
    }

    @PUT
    @Transactional
    @Path("/provider")
    @RolesAllowed("ADMIN")
    public OpenIDConnectProviderDTO updateProvider(final OpenIDConnectProviderDTO dto) {
        final OpenIDConnectProvider entity = OpenIDConnectProvider.findById(dto.id());
        if (entity == null) throw new NotFoundException("Unknown provider");

        entity.name = dto.name();
        entity.clientId = dto.clientId();
        entity.clientSecret = dto.clientSecret();
        entity.discoveryUrl = dto.discoveryUrl();
        entity.authorizationEndpoint = dto.authorizationEndpoint();
        entity.tokenEndpoint = dto.tokenEndpoint();
        entity.userInfoEndpoint = dto.userInfoEndpoint();
        entity.persist();

        return OpenIDConnectProviderDTO.from(entity);
    }

    @GET
    @Transactional
    @Path("/providers")
    @RolesAllowed("ADMIN")
    public @NotNull PagedResponse<OpenIDConnectProviderDTO> listProviders(final @QueryParam("p") @Min(0) int page,
                                                                          final @QueryParam("s") @Min(1) @Max(100) int size) {
        final PanacheQuery<OpenIDConnectProvider> query = OpenIDConnectProvider.findAll().page(page, size);

        return PagedResponse.from(
                query,
                page,
                OpenIDConnectProviderDTO::from
        );
    }

    @DELETE
    @Transactional
    @RolesAllowed("ADMIN")
    @Path("/providers/{id}")
    public void deleteProvider(final @PathParam("id") long id) {
        OpenIDConnection.delete("provider.id = ?1", id);
        OpenIDConnectProvider.deleteById(id);
    }

    @GET
    @Transactional
    @RolesAllowed("ADMIN")
    public Uni<OpenIDConnectOptions> fetchOptions(final @QueryParam("configUri") String discoveryUrl) {
        return Uni.createFrom()
                .completionStage(this.service.fetchWellKnown(discoveryUrl).toCompletionStage());
    }

    @GET
    @Transactional
    @Path("/login/{provider}")
    @APIResponse(
            responseCode = "200",
            description = "Returns a valid redirect URL to the given OIDC provider",
            content = @Content(mediaType = "text/plain", schema = @Schema(type = SchemaType.STRING))
    )
    public Response prepareLogin(final @PathParam("provider") String provider) {
        return prepareAuthorization(
                provider,
                "/login/oidc/complete/" + provider,
                "/api/v1/oidc/login/complete/" + provider
        );
    }

    @GET
    @Transactional
    @Authenticated
    @Path("/link/{provider}")
    @APIResponse(
            responseCode = "200",
            description = "Returns a valid redirect URL to the given OIDC provider",
            content = @Content(mediaType = "text/plain", schema = @Schema(type = SchemaType.STRING))
    )
    public Response prepareLink(final @PathParam("provider") String provider) {
        return prepareAuthorization(
                provider,
                "/login/oidc/complete/" + provider,
                "/api/v1/oidc/link/complete/" + provider
        );
    }

    private Response prepareAuthorization(final String provider,
                                          final String callbackPath,
                                          final String statePath) {
        final OpenIDConnectProvider entity = OpenIDConnectProvider.find("name = ?1", provider).firstResult();
        if (entity == null) throw new NotFoundException("Unknown provider");

        final String state = UUID.randomUUID().toString();
        final String redirectUri = this.host.replaceAll("/$", "") + callbackPath;

        final String url = "%s?client_id=%s&redirect_uri=%s&response_type=code&scope=%s&state=%s".formatted(
                entity.authorizationEndpoint,
                entity.clientId,
                URLEncoder.encode(redirectUri, StandardCharsets.UTF_8),
                "openid",
                state
        );

        return Response.ok(url)
                .cookie(new NewCookie.Builder(OIDC_STATE_COOKIE_NAME)
                        .value(state)
                        .maxAge((int) Duration.ofMinutes(10).toSeconds())
                        .secure(this.host.startsWith("https://"))
                        .sameSite(NewCookie.SameSite.LAX)
                        .path(statePath)
                        .httpOnly(true)
                        .build())
                .build();
    }

    protected Uni<OpenIDConnectProvider> withProvider(final String provider) {
        return VertxContextSupport.executeBlocking(() -> QuarkusTransaction.requiringNew().call(() -> {
            final OpenIDConnectProvider entity = OpenIDConnectProvider.find("name = ?1", provider).firstResult();
            if (entity == null) throw new NotFoundException("Provider not found");

            return entity;
        }));
    }

    @POST
    @Path("/login/complete/{provider}")
    public Uni<Response> completeLogin(final @PathParam("provider") String provider,
                                       final @CookieParam(OIDC_STATE_COOKIE_NAME) String state,
                                       final @QueryParam("code") String code,
                                       final @QueryParam("state") String stateFromQuery,
                                       final @Context HttpServerRequest request) {
        if (!Objects.equals(state, stateFromQuery)) throw new BadRequestException("Invalid state");

        return withProvider(provider)
                .flatMap(entity -> {
                    final String redirectUri = this.host.replaceAll("/$", "") + "/login/oidc/complete/" + provider;

                    return Uni.createFrom()
                            .completionStage(this.service.getSubjectFromCode(code, entity, redirectUri).toCompletionStage())
                            .flatMap(subj -> VertxContextSupport.executeBlocking(() -> QuarkusTransaction.requiringNew().call(() -> {
                                final OpenIDConnection connection = OpenIDConnection.find("provider = ?1 AND subject = ?2", entity, subj).firstResult();
                                if (connection == null) return Response.status(Response.Status.NOT_FOUND).build();

                                return authenticationEndpoint.doLogin(connection.user, request.remoteAddress().hostAddress());
                            })));
                });
    }

    @POST
    @Authenticated
    @Path("/link/complete/{provider}")
    public Uni<Response> completeLink(final @PathParam("provider") String provider,
                                      final @CookieParam(OIDC_STATE_COOKIE_NAME) String state,
                                      final @QueryParam("code") String code,
                                      final @QueryParam("state") String stateFromQuery) {
        if (!Objects.equals(state, stateFromQuery)) throw new BadRequestException("Invalid state");

        return withProvider(provider)
                .flatMap(entity -> {
                    final String redirectUri = this.host.replaceAll("/$", "") + "/login/oidc/complete/" + provider;

                    return Uni.createFrom()
                            .completionStage(this.service.getSubjectFromCode(code, entity, redirectUri).toCompletionStage())
                            .flatMap(subj -> VertxContextSupport.executeBlocking(() -> QuarkusTransaction.requiringNew().call(() -> {
                                final OpenIDConnection connection = OpenIDConnection.find("provider = ?1 AND subject = ?2", entity, subj).firstResult();
                                if (connection != null) return Response.status(Response.Status.CONFLICT).build();

                                final User self = this.authenticatedUser.getSelf();

                                final OpenIDConnection newConnection = new OpenIDConnection();
                                newConnection.provider = entity;
                                newConnection.subject = subj;
                                newConnection.user = self;
                                newConnection.persist();

                                return Response.ok().build();
                            })));
                });
    }

    @GET
    @Transactional
    @Authenticated
    @Path("/links")
    public @NotNull List<@NotNull OpenIDConnectionDTO> getLinks() {
        final User self = this.authenticatedUser.getSelf();
        final List<OpenIDConnection> links = OpenIDConnection.find("user = ?1", self).list();

        return links.stream()
                .map(OpenIDConnectionDTO::from)
                .toList();
    }

    @DELETE
    @Transactional
    @Authenticated
    @Path("/link/{id}")
    public void deleteLink(final @PathParam("id") long id) {
        final User self = this.authenticatedUser.getSelf();

        OpenIDConnection.delete("id = ?1 AND user = ?2", id, self);
    }

}
