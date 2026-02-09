package com.bethibande.repository.repository.oci;

import java.util.UUID;

public record UploadSessionHandle(
        UUID sessionId,
        String uploadId
) {
}
