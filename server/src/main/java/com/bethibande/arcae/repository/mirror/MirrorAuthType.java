package com.bethibande.arcae.repository.mirror;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public enum MirrorAuthType {

    // Internal authentication
    APPLY_USER_AUTH,
    APPLY_SYSTEM_AUTH,
    
    // External authentication
    NONE,
    BASIC,
    BEARER

}
