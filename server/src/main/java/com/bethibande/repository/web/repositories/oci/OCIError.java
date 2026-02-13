package com.bethibande.repository.web.repositories.oci;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record OCIError(
        List<OCIErrorEntry> errors
) {

    public static OCIError of(final String code, final String message, final String details) {
        return of(new OCIErrorEntry(code, message, details));
    }

    public static OCIError of(final OCIErrorEntry entry) {
        return new OCIError(List.of(entry));
    }

}
