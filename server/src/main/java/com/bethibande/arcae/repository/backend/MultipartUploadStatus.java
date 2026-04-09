package com.bethibande.arcae.repository.backend;

public record MultipartUploadStatus(
        long offset,
        int partNumber
) {
}
