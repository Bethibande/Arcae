package com.bethibande.repository.repository.oci.index;

import com.bethibande.repository.jpa.artifact.Artifact;
import com.bethibande.repository.jpa.artifact.ArtifactDetails;
import com.bethibande.repository.jpa.artifact.ArtifactVersion;
import com.bethibande.repository.jpa.files.FileUploadSession;
import com.bethibande.repository.jpa.files.OCISubject;
import com.bethibande.repository.jpa.files.StoredFile;
import com.bethibande.repository.repository.ArtifactAndGroupId;
import com.bethibande.repository.repository.oci.details.OCIDetailsHelper;
import com.bethibande.repository.repository.oci.OCIDigestHelper;
import com.bethibande.repository.repository.oci.OCIRepository;
import com.bethibande.repository.repository.oci.details.OCILayerReference;
import com.bethibande.repository.repository.oci.details.OCIManifestDetails;
import com.bethibande.repository.repository.oci.details.OCIManifestReference;
import com.bethibande.repository.web.repositories.oci.OCIError;
import com.bethibande.repository.web.repositories.oci.OCIErrorCodes;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.TransactionSemantics;
import jakarta.persistence.LockModeType;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

/**
 * A utility class for building and querying the index for OCI artifacts and files.
 */
public class OCIImageIndex {

    // TODO: Configuration
    public static final Duration MIRROR_TTL = Duration.ofHours(1);

    private final OCIRepository repository;

    public OCIImageIndex(final OCIRepository repository) {
        this.repository = repository;
    }

    protected StoredFile putFile(final String key, final String digest, final String contentType, final long contentLength) {
        final Number id = (Number) StoredFile.getEntityManager().createNativeQuery("""
                            INSERT INTO StoredFile (id, repository_id, key, created, updated, contenttype, contentlength)
                            VALUES (nextval('storedfile_seq'), :repo, :key, :now, :now, :type, :length)
                            ON CONFLICT (key)
                            DO UPDATE SET updated = EXCLUDED.updated, contenttype = EXCLUDED.contenttype, contentlength = EXCLUDED.contentlength
                            RETURNING id
                        """)
                .setParameter("repo", this.repository.getInfo().id)
                .setParameter("key", key)
                .setParameter("now", Instant.now())
                .setParameter("type", contentType)
                .setParameter("length", contentLength)
                .getSingleResult();

        final StoredFile file = StoredFile.findById(id);
        final String[] digestParts = digest.split(":");

        if (file.hashes == null) file.hashes = new HashMap<>();
        file.hashes.put(digestParts[0], digestParts[1]);

        return file;
    }

    public void putBlob(final String key, final String digest, final long contentLength) {
        this.putFile(key, digest, "application/octet-stream", contentLength);
    }

    protected ArtifactVersion updateOrCreateArtifactAndVersion(final Instant now, final String namespace, final String reference) {
        final ArtifactAndGroupId artifactAndGroupId = this.repository.extractArtifactAndGroupId(namespace);
        final String groupId = artifactAndGroupId.groupId();
        final String artifactId = artifactAndGroupId.artifactId();

        final Artifact artifact = this.repository.getOrCreateArtifact(groupId, artifactId, now);
        final ArtifactVersion version = this.repository.getOrCreateArtifactVersion(artifact, reference, now);

        if (version.files != null) {
            final List<StoredFile> oldFiles = new ArrayList<>(version.files);
            version.files.clear();

            for (int i = 0; i < oldFiles.size(); i++) {
                final StoredFile file = oldFiles.get(i);
                this.repository.tryDeleteFile(file);
            }
        } else {
            version.files = new ArrayList<>();
        }

        return version;
    }

    protected <T> void listFiles(final List<StoredFile> output,
                                 final List<T> values,
                                 final Function<T, String> toDigest,
                                 final Function<String, String> toKey,
                                 final boolean isMirrorRequest) {
        for (int i = 0; i < values.size(); i++) {
            final T value = values.get(i);
            final String digest = toDigest.apply(value);
            final String key = toKey.apply(digest);

            final StoredFile file = StoredFile.find("key = ?1 and repository.id = ?2", key, this.repository.getInfo().id).firstResult();
            if (file != null) {
                output.add(file);
            } else if (isMirrorRequest) { // Mirror requests pull the manifest first, and then the blobs
                // Create a placeholder file to ensure the blobs/manifests are properly linked once they are pulled.
                // Otherwise, they will be GCed by our cleanup job
                this.putFile(key, digest, "application/octet-stream", 0);
            } else {
                throw new WebApplicationException(Response.status(Response.Status.CONFLICT)
                        .entity(OCIError.of(OCIErrorCodes.MANIFEST_BLOB_UNKNOWN, "A referenced manifest or layer is unknown", "The manifest or layer %s is unknown".formatted(digest)))
                        .build());

            }
        }
    }

    protected List<StoredFile> collectReferencedFiles(final String namespace,
                                                      final OCIManifestDetails details,
                                                      final boolean isMirrorRequest) {
        final List<StoredFile> files = new ArrayList<>();

        final List<OCIManifestReference> manifests = details.manifests();
        listFiles(
                files,
                manifests,
                OCIManifestReference::digest,
                digest -> this.repository.toManifestKey(namespace, digest),
                isMirrorRequest
        );

        final List<OCILayerReference> layers = details.layers();
        listFiles(
                files,
                layers,
                OCILayerReference::digest,
                digest -> this.repository.toBlobKey(namespace, digest),
                isMirrorRequest
        );

        final String configKey = this.repository.toBlobKey(namespace, details.configDigest());
        final StoredFile config = StoredFile.find("key = ?1 and repository.id = ?2", configKey, this.repository.getInfo().id).firstResult();
        if (config != null) files.add(config);

        return files;
    }

    public OCIManifestIndexResult putManifest(final String namespace,
                                              final String reference,
                                              final String digest,
                                              final byte[] contents,
                                              final String contentType,
                                              final long contentLength,
                                              final boolean isMirrorRequest) throws IOException {
        final String key = this.repository.toManifestKey(namespace, digest);

        final StoredFile manifestFile = this.putFile(key, digest, contentType, contentLength);
        final OCISubject subject = OCISubjectHelper.createSubjectInfo(
                namespace,
                manifestFile,
                this.repository.getInfo().id,
                this.repository::toManifestKey,
                contents
        );

        if (!OCIDigestHelper.isDigest(reference)) {
            final ArtifactVersion version = updateOrCreateArtifactAndVersion(Instant.now(), namespace, reference);
            version.files.add(manifestFile);
            version.manifest = manifestFile;

            final ArtifactDetails details = OCIDetailsHelper.parseDetails(contents);
            version.details = details;

            final OCIManifestDetails manifestDetails = (OCIManifestDetails) details.additionalData();
            version.files.addAll(collectReferencedFiles(namespace, manifestDetails, isMirrorRequest));

            return new OCIManifestIndexResult(manifestFile, version, subject);
        }

        return new OCIManifestIndexResult(manifestFile, null, subject);
    }

    public ArtifactVersion getArtifactVersionByDigest(final String namespace,
                                                      final String digest,
                                                      final LockModeType lockMode) {
        final String key = this.repository.toManifestKey(namespace, digest);

        return QuarkusTransaction.joiningExisting().call(() -> ArtifactVersion.find("manifest.key = ?1", key)
                .withLock(lockMode)
                .firstResult());
    }

    public ArtifactVersion getArtifactVersionByTag(final String namespace, final String tag, final LockModeType lockMode) {
        final ArtifactAndGroupId artifactAndGroupId = this.repository.extractArtifactAndGroupId(namespace);
        final String groupId = artifactAndGroupId.groupId();
        final String artifactId = artifactAndGroupId.artifactId();

        return QuarkusTransaction.runner(TransactionSemantics.JOIN_EXISTING).call(() -> {
            final Artifact artifact = Artifact.find("groupId = ?1 and artifactId = ?2 and repository.id = ?3", groupId, artifactId, this.repository.getInfo().id)
                    .firstResult();

            if (artifact == null) return null;

            return ArtifactVersion.find("artifact = ?1 and version = ?2", artifact, tag).withLock(lockMode).firstResult();
        });
    }

    public ArtifactVersion getArtifactVersionByReference(final String namespace,
                                                         final String reference,
                                                         final LockModeType lockMode) {
        return OCIDigestHelper.isDigest(reference)
                ? getArtifactVersionByDigest(namespace, reference, lockMode)
                : getArtifactVersionByTag(namespace, reference, lockMode);
    }

    public StoredFile findManifestFileByReference(final String namespace, final String reference) {
        if (OCIDigestHelper.isDigest(reference)) {
            final String key = this.repository.toManifestKey(namespace, reference);
            return StoredFile.find("key = ?1 and repository.id = ?2", key, this.repository.getInfo().id).firstResult();
        } else {
            return findManifestFileByTag(namespace, reference);
        }
    }

    public StoredFile findManifestFileByTag(final String namespace, final String tag) {
        return QuarkusTransaction.runner(TransactionSemantics.JOIN_EXISTING).call(() -> {
            final ArtifactVersion version = getArtifactVersionByTag(namespace, tag, LockModeType.NONE);
            return version != null
                    ? version.manifest
                    : null;
        });
    }

    public void recordUploadSession(final Instant createdAt, final String fileKey, final String uploadId) {
        final FileUploadSession session = new FileUploadSession();
        session.repository = this.repository.getInfo();
        session.fileKey = fileKey;
        session.createdAt = createdAt;
        session.uploadSessionId = uploadId;

        session.persist();
    }

    public void endUploadSession(final String uploadId) {
        FileUploadSession.delete("uploadSessionId = ?1", uploadId);
    }

    public String fetchUploadSessionHashState(final String uploadId) {
        return QuarkusTransaction.runner(TransactionSemantics.JOIN_EXISTING).call(() -> {
            final FileUploadSession session = FileUploadSession.find("uploadSessionId = ?1", uploadId).firstResult();
            if (session == null) return null;
            return session.hashingState;
        });
    }

    public void updateUploadSessionHashState(final String uploadId, final String state) {
        QuarkusTransaction.requiringNew().run(() -> {
            FileUploadSession.update("hashingState = ?1 where uploadSessionId = ?2", state, uploadId);
        });
    }

    public void updateVersionMirrorTTL(final long versionId) {
        QuarkusTransaction.requiringNew().run(() -> {
            ArtifactVersion.update(
                    "mirrorTTL = ?1 where id = ?2",
                    Instant.now().plus(MIRROR_TTL),
                    versionId
            );
        });
    }

}
