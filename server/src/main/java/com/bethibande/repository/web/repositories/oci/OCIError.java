package com.bethibande.repository.web.repositories.oci;

import java.util.List;

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
