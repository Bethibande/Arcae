package com.bethibande.repository.security;

import com.bethibande.repository.jpa.security.UserSession;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Objects;

@ApplicationScoped
public class SessionIdentityProvider implements IdentityProvider<SessionAuthenticationRequest> {

    private final UserSessionService userSessionService;

    @Inject
    public SessionIdentityProvider(final UserSessionService userSessionService) {
        this.userSessionService = userSessionService;
    }

    @Override
    public Class<SessionAuthenticationRequest> getRequestType() {
        return SessionAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(final SessionAuthenticationRequest request,
                                              final AuthenticationRequestContext context) {
        return context.runBlocking(() -> {
            final String token = request.getToken();

            final UserSession session = this.userSessionService.getSessionByToken(token);
            if (!this.userSessionService.isValid(session)) throw new AuthenticationFailedException("Invalid session");
            if (!Objects.equals(session.address, request.getRemoteAddress().hostAddress())) throw new AuthenticationFailedException("Invalid session");

            return QuarkusSecurityIdentity.builder()
                    .setPrincipal(session.user)
                    .addRoles(session.user.getRolesAsString())
                    .addAttribute(SecurityAttributes.SESSION, session)
                    .build();
        });
    }
}
