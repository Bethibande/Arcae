package com.bethibande.repository.security;

import io.quarkus.security.identity.request.BaseAuthenticationRequest;

public class BearerTokenAuthenticationRequest extends BaseAuthenticationRequest {

    private final String accessToken;

    public BearerTokenAuthenticationRequest(final String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessToken() {
        return this.accessToken;
    }
}
