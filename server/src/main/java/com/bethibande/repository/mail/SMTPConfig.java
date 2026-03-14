package com.bethibande.repository.mail;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotNull;

@RegisterForReflection
public record SMTPConfig(
        @NotNull boolean enabled,
        String from,
        String password,
        String host,
        int port,
        MailEncryption encryption
) {
}
