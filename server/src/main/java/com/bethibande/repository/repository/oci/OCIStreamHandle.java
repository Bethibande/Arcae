package com.bethibande.repository.repository.oci;

import com.bethibande.repository.repository.StreamHandle;

public record OCIStreamHandle(
        StreamHandle streamHandle,
        String digest
) {
}
