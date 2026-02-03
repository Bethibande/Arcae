package com.bethibande.repository.security;

import com.bethibande.repository.jpa.security.UserSession;
import com.bethibande.repository.jpa.user.UserRole;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.stream.Collectors;

@ApplicationScoped
public class CustomTokenIdentityProvider implements IdentityProvider<TokenAuthenticationRequest> {

    @Inject
    protected UserSessionService userSessionService;

    @Override
    public Class<TokenAuthenticationRequest> getRequestType() {
        return TokenAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(final TokenAuthenticationRequest request, final AuthenticationRequestContext context) {
        return context.runBlocking(() -> {
            final String token = request.getToken().getToken();

            final UserSession session = userSessionService.getSessionByToken(token);
            if (!userSessionService.isValid(session)) throw new AuthenticationFailedException("Invalid session");

            return QuarkusSecurityIdentity.builder()
                    .setPrincipal(session.user)
                    .addRoles(session.user.roles.stream()
                            .map(UserRole::toString)
                            .collect(Collectors.toSet()))
                    .addAttribute(SecurityAttributes.SESSION, session)
                    .build();
        });
    }
}
