package com.bethibande.arcae.repository.oci.index;

import com.bethibande.arcae.jpa.artifact.ArtifactVersion;
import com.bethibande.arcae.jpa.files.OCISubject;
import com.bethibande.arcae.jpa.files.StoredFile;

public record OCIManifestIndexResult(
        StoredFile file,
        ArtifactVersion version,
        OCISubject subject
) {
}
