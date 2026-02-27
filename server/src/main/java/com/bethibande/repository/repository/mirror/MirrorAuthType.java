package com.bethibande.repository.repository.mirror;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public enum MirrorAuthType {

    NONE,
    BASIC,
    BEARER

}
