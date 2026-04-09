package com.bethibande.arcae.security;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.security.identity.request.BaseAuthenticationRequest;

@RegisterForReflection
public class BearerTokenAuthenticationRequest extends BaseAuthenticationRequest {

    private final String accessToken;

    public BearerTokenAuthenticationRequest(final String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessToken() {
        return this.accessToken;
    }
}
