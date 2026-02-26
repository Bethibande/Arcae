package com.bethibande.repository.security.system;

import com.bethibande.repository.jpa.user.UserRole;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.stream.Collectors;

@ApplicationScoped
public class SystemAuthenticationProvider implements IdentityProvider<SystemAuthenticationRequest> {

    @Override
    public Class<SystemAuthenticationRequest> getRequestType() {
        return SystemAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(final SystemAuthenticationRequest request,
                                              final AuthenticationRequestContext context) {
        return Uni.createFrom()
                .item(QuarkusSecurityIdentity.builder()
                        .addRoles(Arrays.stream(UserRole.values())
                                .map(UserRole::name)
                                .collect(Collectors.toSet()))
                        .setPrincipal(SystemPrincipal.INSTANCE)
                        .build());
    }
}
