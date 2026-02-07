package com.bethibande.repository.repository.oci;

import com.bethibande.repository.repository.S3Config;

public record OCIRepositoryConfig(
        S3Config s3Config
) {
}
