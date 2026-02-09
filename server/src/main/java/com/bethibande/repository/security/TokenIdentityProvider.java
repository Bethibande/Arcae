package com.bethibande.repository.security;

import com.bethibande.repository.jpa.security.UserSession;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TokenIdentityProvider implements IdentityProvider<TokenAuthenticationRequest> {

    private final UserSessionService userSessionService;

    @Inject
    public TokenIdentityProvider(final UserSessionService userSessionService) {
        this.userSessionService = userSessionService;
    }

    @Override
    public Class<TokenAuthenticationRequest> getRequestType() {
        return TokenAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(final TokenAuthenticationRequest request,
                                              final AuthenticationRequestContext context) {
        return context.runBlocking(() -> {
            final String token = request.getToken().getToken();

            final UserSession session = userSessionService.getSessionByToken(token);
            if (!userSessionService.isValid(session)) throw new AuthenticationFailedException("Invalid session");

            return QuarkusSecurityIdentity.builder()
                    .setPrincipal(session.user)
                    .addRoles(session.user.getRolesAsString())
                    .addAttribute(SecurityAttributes.SESSION, session)
                    .build();
        });
    }
}
