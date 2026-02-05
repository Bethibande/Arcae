package com.bethibande.repository.security;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.credential.TokenCredential;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.Cookie;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class UserAuthenticationMechanism implements HttpAuthenticationMechanism {

    public static final String COOKIE_NAME = "Identity";

    @Override
    public Uni<SecurityIdentity> authenticate(final RoutingContext context, final IdentityProviderManager identityProviderManager) {
        final Cookie cookie = context.request().getCookie(COOKIE_NAME);
        final String authCookie = cookie != null
                ? cookie.getValue()
                : null;

        if (authCookie != null) {
            return identityProviderManager.authenticate(new TokenAuthenticationRequest(new TokenCredential(authCookie, "user")))
                    .onFailure(AuthenticationFailedException.class)
                    .recoverWithUni(Uni.createFrom().optional(Optional.empty()));
        }

        return Uni.createFrom().optional(Optional.empty());
    }

    @Override
    public Uni<ChallengeData> getChallenge(final RoutingContext context) {
        return Uni.createFrom().nullItem();
    }

    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(final RoutingContext context) {
        return Uni.createFrom().item(new HttpCredentialTransport(HttpCredentialTransport.Type.COOKIE, COOKIE_NAME));
    }
}
