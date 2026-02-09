package com.bethibande.repository.security;

import com.bethibande.repository.jpa.security.AccessToken;
import com.bethibande.repository.jpa.security.AccessTokenService;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

@ApplicationScoped
public class BasicAuthIdentityProvider implements IdentityProvider<UsernamePasswordAuthenticationRequest> {

    private final AccessTokenService accessTokenService;

    @Inject
    public BasicAuthIdentityProvider(final AccessTokenService accessTokenService) {
        this.accessTokenService = accessTokenService;
    }

    @Override
    public Class<UsernamePasswordAuthenticationRequest> getRequestType() {
        return UsernamePasswordAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(final UsernamePasswordAuthenticationRequest request,
                                              final AuthenticationRequestContext context) {
        return context.runBlocking(() -> {
            final AccessToken token = accessTokenService.getToken(request.getUsername(), new String(request.getPassword().getPassword()));
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
