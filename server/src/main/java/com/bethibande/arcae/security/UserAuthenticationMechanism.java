package com.bethibande.arcae.security;

import com.bethibande.arcae.security.system.SystemAuthenticationRequest;
import com.bethibande.arcae.web.management.ManagementServer;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.credential.PasswordCredential;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.Cookie;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.http.HttpHeaders;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Base64;
import java.util.Optional;

@ApplicationScoped
public class UserAuthenticationMechanism implements HttpAuthenticationMechanism {

    public static final String COOKIE_NAME = "Identity";

    @ConfigProperty(name = ManagementServer.MANAGEMENT_PORT_PROPERTY)
    protected int managementPort;

    protected AuthenticationRequest cookieAuth(final Cookie cookie, final SocketAddress address) {
        final String text = cookie.getValue();
        return new SessionAuthenticationRequest(text, address);
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

        if (context.request().localAddress().port() == managementPort) {
            request = new SystemAuthenticationRequest();
        }

        final Cookie cookie = context.request().getCookie(COOKIE_NAME);
        if (cookie != null) {
            request = cookieAuth(cookie, context.request().remoteAddress());
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
