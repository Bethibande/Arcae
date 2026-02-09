package com.bethibande.repository.repository.backend;

public record MultipartUploadStatus(
        long offset,
        int partNumber
) {
}
