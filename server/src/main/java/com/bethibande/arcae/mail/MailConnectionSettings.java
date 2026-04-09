package com.bethibande.arcae.mail;

import jakarta.validation.constraints.NotNull;

public record MailConnectionSettings(
        @NotNull String host,
        @NotNull int port,
        @NotNull MailEncryption tls
) {
}
