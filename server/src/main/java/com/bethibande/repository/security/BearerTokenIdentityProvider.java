package com.bethibande.repository.security;

import com.bethibande.repository.jpa.security.AccessToken;
import com.bethibande.repository.jpa.security.AccessTokenService;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

@ApplicationScoped
public class BearerTokenIdentityProvider implements IdentityProvider<BearerTokenAuthenticationRequest> {

    public static final String ANONYMOUS_TOKEN = "anonymous";

    private final AccessTokenService accessTokenService;

    @Inject
    public BearerTokenIdentityProvider(final AccessTokenService accessTokenService) {
        this.accessTokenService = accessTokenService;
    }

    @Override
    public Class<BearerTokenAuthenticationRequest> getRequestType() {
        return BearerTokenAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(final BearerTokenAuthenticationRequest request,
                                              final AuthenticationRequestContext context) {
        return context.runBlocking(() -> {
            final String tokenString = request.getAccessToken();
            if (ANONYMOUS_TOKEN.equals(tokenString)) throw new AuthenticationFailedException(); // Will result in an anonymous identity

            final AccessToken token = accessTokenService.getToken(tokenString);
            final Instant now = Instant.now();

            if (token == null || token.isExpired(now)) throw new AuthenticationFailedException("Invalid credentials");

            return QuarkusSecurityIdentity.builder()
                    .setPrincipal(token.owner)
                    .addRoles(token.owner.getRolesAsString())
                    .addAttribute(SecurityAttributes.ACCESS_TOKEN, token)
                    .build();
        });
    }

}
