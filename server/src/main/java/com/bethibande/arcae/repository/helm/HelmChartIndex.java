package com.bethibande.arcae.repository.helm;

import com.bethibande.arcae.jpa.files.HelmMetadata;
import com.bethibande.arcae.repository.StreamHandle;
import com.bethibande.arcae.repository.oci.details.OCIManifestDetails;
import com.bethibande.arcae.repository.oci.index.OCIImageIndex;
import com.bethibande.arcae.repository.oci.index.OCIManifestIndexResult;
import com.bethibande.arcae.repository.security.AuthContext;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class HelmChartIndex extends OCIImageIndex {

    private final ObjectMapper objectMapper;

    public HelmChartIndex(final HelmRepository repository, final ObjectMapper objectMapper) {
        super(repository);
        this.objectMapper = objectMapper;
    }

    @Override
    public OCIManifestIndexResult putManifest(final String namespace,
                                              final String reference,
                                              final String digest,
                                              final byte[] contents,
                                              final String contentType,
                                              final long contentLength,
                                              final boolean isMirrorRequest) throws IOException {
        final OCIManifestIndexResult result = super.putManifest(namespace, reference, digest, contents, contentType, contentLength, isMirrorRequest);
        if (result.version() == null) return result;

        final OCIManifestDetails details = (OCIManifestDetails) result.version().details.additionalData();

        try (StreamHandle config = super.repository.getBlob(AuthContext.ofSystem(), namespace, details.configDigest())) {
            final byte[] configBytes = config.readAllBytes();
            HelmIndexEntry metadata = this.objectMapper.readValue(configBytes, HelmIndexEntry.class);

            if (metadata == null) {
                throw new IOException("Failed to parse Helm metadata from OCI blob");
            }

            metadata = new HelmIndexEntry(
                    metadata.created() != null ? metadata.created() : result.version().updated,
                    metadata.description(),
                    metadata.digest() != null ? metadata.digest() : details.layers().getFirst().digest().substring(7),
                    metadata.home(),
                    metadata.name(),
                    metadata.sources(),
                    metadata.urls(),
                    metadata.version()
            );

            final HelmMetadata metadataEntity = new HelmMetadata();
            metadataEntity.version = result.version();
            metadataEntity.data = metadata;
            metadataEntity.persist();
        }

        return result;
    }


}
