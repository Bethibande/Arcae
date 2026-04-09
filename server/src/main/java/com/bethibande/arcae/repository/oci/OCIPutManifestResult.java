package com.bethibande.arcae.repository.oci;

import com.bethibande.arcae.jpa.artifact.ArtifactVersion;
import com.bethibande.arcae.jpa.files.OCISubject;
import com.bethibande.arcae.jpa.files.StoredFile;
import org.jspecify.annotations.Nullable;

public record OCIPutManifestResult(
        StoredFile file,
        String digest,
        @Nullable ArtifactVersion version,
        @Nullable OCISubject subject
) {
}
