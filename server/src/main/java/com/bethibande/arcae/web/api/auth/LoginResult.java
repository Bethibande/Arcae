package com.bethibande.arcae.web.api.auth;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public enum LoginResult {

    LOGGED_IN,
    REQUIRES_2FA

}
