package com.bethibande.repository.repository.maven;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public enum MavenMirrorAuthType {

    NONE,
    BASIC,
    BEARER

}
