package com.bethibande.repository.mail;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public enum MailEncryption {

    NONE,
    SSL,
    START_TLS

}
