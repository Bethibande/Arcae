package com.bethibande.arcae.security;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.security.identity.request.BaseAuthenticationRequest;
import io.vertx.core.net.SocketAddress;

@RegisterForReflection
public class SessionAuthenticationRequest extends BaseAuthenticationRequest {

    private final String token;

    private final SocketAddress remoteAddress;

    public SessionAuthenticationRequest(final String token, final SocketAddress remoteAddress) {
        this.token = token;
        this.remoteAddress = remoteAddress;
    }

    public String getToken() {
        return token;
    }

    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }
}
