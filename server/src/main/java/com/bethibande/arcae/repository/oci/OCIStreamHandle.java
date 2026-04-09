package com.bethibande.arcae.repository.oci;

import com.bethibande.arcae.repository.StreamHandle;

public record OCIStreamHandle(
        StreamHandle streamHandle,
        String digest
) {
}
