package com.bethibande.repository.repository.oci;

import com.bethibande.repository.jpa.artifact.ArtifactVersion;
import com.bethibande.repository.jpa.files.OCISubject;
import com.bethibande.repository.jpa.files.StoredFile;
import org.jspecify.annotations.Nullable;

public record OCIPutManifestResult(
        StoredFile file,
        String digest,
        @Nullable ArtifactVersion version,
        @Nullable OCISubject subject
) {
}
