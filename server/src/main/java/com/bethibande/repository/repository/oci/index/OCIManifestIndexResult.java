package com.bethibande.repository.repository.oci.index;

import com.bethibande.repository.jpa.artifact.ArtifactVersion;
import com.bethibande.repository.jpa.files.OCISubject;
import com.bethibande.repository.jpa.files.StoredFile;

public record OCIManifestIndexResult(
        StoredFile file,
        ArtifactVersion version,
        OCISubject subject
) {
}
