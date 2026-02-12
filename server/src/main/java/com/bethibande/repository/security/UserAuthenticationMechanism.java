package com.bethibande.repository.security;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.credential.PasswordCredential;
import io.quarkus.security.credential.TokenCredential;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.Cookie;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Optional;

@ApplicationScoped
public class UserAuthenticationMechanism implements HttpAuthenticationMechanism {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserAuthenticationMechanism.class);

    public static final String COOKIE_NAME = "Identity";

    protected AuthenticationRequest cookieAuth(final Cookie cookie) {
        final String text = cookie.getValue();
        return new TokenAuthenticationRequest(new TokenCredential(text, "user"));
    }

    protected AuthenticationRequest basicAuth(final String value) {
        final String decoded = new String(Base64.getDecoder().decode(value));
        final String[] parts = decoded.split(":");

        final String username = parts[0];
        final String password = parts[1];

        return new UsernamePasswordAuthenticationRequest(username, new PasswordCredential(password.toCharArray()));
    }

    @Override
    public Uni<SecurityIdentity> authenticate(final RoutingContext context, final IdentityProviderManager identityProviderManager) {
        AuthenticationRequest request = null;

        final Cookie cookie = context.request().getCookie(COOKIE_NAME);
        if (cookie != null) {
            request = cookieAuth(cookie);
        }

        final String authorization = context.request().getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null) {
            if (authorization.startsWith("Basic ")) {
                request = basicAuth(authorization.substring(6));
            }
            if (authorization.startsWith("Bearer ")) {
                request = new BearerTokenAuthenticationRequest(authorization.substring(7));
            }
        }

        if (request != null) {
            return identityProviderManager.authenticate(request)
                    .onFailure(AuthenticationFailedException.class)
                    .recoverWithUni(Uni.createFrom().optional(Optional.empty()));
        }

        return Uni.createFrom().optional(Optional.empty());
    }

    @Override
    public Uni<ChallengeData> getChallenge(final RoutingContext context) {
        return Uni.createFrom().nullItem();
    }

}
