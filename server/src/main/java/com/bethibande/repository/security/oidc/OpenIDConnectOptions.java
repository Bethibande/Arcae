package com.bethibande.repository.security.oidc;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenIDConnectOptions(
        @JsonProperty("authorization_endpoint")
        String authorizationEndpoint,

        @JsonProperty("token_endpoint")
        String tokenEndpoint,

        @JsonProperty("userinfo_endpoint")
        String userInfoEndpoint
) {
}
