package com.bethibande.repository.repository.oci;

import com.bethibande.repository.jpa.StoredFile;
import com.bethibande.repository.jpa.artifact.Artifact;
import com.bethibande.repository.jpa.artifact.ArtifactDetails;
import com.bethibande.repository.jpa.artifact.ArtifactVersion;
import com.bethibande.repository.jpa.repository.Repository;
import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.repository.ArtifactAndGroupId;
import com.bethibande.repository.repository.ManagedRepository;
import com.bethibande.repository.repository.StreamHandle;
import com.bethibande.repository.repository.backend.S3Backend;
import com.bethibande.repository.repository.oci.details.OCILayerReference;
import com.bethibande.repository.repository.oci.details.OCIManifestDetails;
import com.bethibande.repository.repository.oci.details.OCIManifestReference;
import com.bethibande.repository.web.exception.RequestTooLongException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.BadRequestException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

public class OCIRepository implements ManagedRepository {

    public static final long MAX_MANIFEST_SIZE = 10_000_000L;

    private final Repository info;
    private final OCIRepositoryConfig config;

    private final S3Backend backend;

    public OCIRepository(final Repository info, final ObjectMapper mapper) throws JsonProcessingException {
        this(info, mapper.readValue(info.settings, OCIRepositoryConfig.class));
    }

    public OCIRepository(final Repository info, final OCIRepositoryConfig config) {
        this.info = info;
        this.config = config;

        this.backend = new S3Backend(config.s3Config());
    }

    @Override
    public Repository getInfo() {
        return info;
    }

    protected String toBlobKey(final String namespace, final String path) {
        return "%s/%s/blobs/%s".formatted(info.name, namespace, path);
    }

    protected String toPendingBlobKey(final String namespace, final String path) {
        return "%s/%s/pending/%s".formatted(info.name, namespace, path);
    }

    protected String toManifestKey(final String namespace, final String digest) {
        return "%s/%s/manifests/%s".formatted(info.name, namespace, digest);
    }

    protected ArtifactAndGroupId extractArtifactAndGroupId(final String namespace) {
        final String artifactId = namespace.substring(namespace.lastIndexOf('/') + 1);
        final String groupId = namespace.lastIndexOf('/') > 0 ? namespace.substring(0, namespace.lastIndexOf('/')) : "";

        return new ArtifactAndGroupId(artifactId, groupId);
    }

    protected StoredFile findManifestFileByTag(final String namespace, final String tag) {
        final ArtifactAndGroupId artifactAndGroupId = extractArtifactAndGroupId(namespace);
        final String groupId = artifactAndGroupId.groupId();
        final String artifactId = artifactAndGroupId.artifactId();

        final Artifact artifact = Artifact.find("groupId = ?1 and artifactId = ?2 and repository.id = ?3", groupId, artifactId, info.id).firstResult();
        if (artifact == null) return null;

        final ArtifactVersion version = ArtifactVersion.find("artifact = ?1 and version = ?2", artifact, tag).firstResult();
        if (version == null) return null;

        return version.manifest;
    }

    protected boolean isDigest(final String reference) {
        return reference.matches("^sha256:[0-9a-fA-F]{64}$|^sha512:[0-9a-fA-F]{128}$");
    }

    public OCIStreamHandle getManifest(final User user, final String namespace, final String reference) {
        if (!info.canView(user)) throw new UnauthorizedException();

        if (isDigest(reference)) {
            return new OCIStreamHandle(
                    this.backend.get(toManifestKey(namespace, reference)),
                    reference
            );
        } else {
            final StoredFile file = findManifestFileByTag(namespace, reference);
            if (file == null) return null;

            final String digest = file.key.substring(file.key.lastIndexOf('/') + 1);

            return new OCIStreamHandle(
                    this.backend.get(file.key),
                    digest
            );
        }
    }

    public OCIContentInfo getManifestInfo(final User user, final String namespace, final String reference) {
        if (!info.canView(user)) throw new UnauthorizedException();

        if (reference.matches("^sha256:[0-9a-fA-F]{64}$|^sha512:[0-9a-fA-F]{128}$")) {
            final long size = this.backend.headSize(toManifestKey(namespace, reference));
            if (size < 0) return null;
            return new OCIContentInfo(
                    reference,
                    size
            );
        } else {
            final StoredFile file = findManifestFileByTag(namespace, reference);
            if (file == null) return null;

            final String digest = file.key.substring(file.key.lastIndexOf('/') + 1);

            return new OCIContentInfo(
                    digest,
                    this.backend.headSize(file.key)
            );
        }
    }

    public OCIContentInfo getBlobInfo(final User user, final String namespace, final String digest) {
        if (!info.canView(user)) throw new UnauthorizedException();

        final long size = this.backend.headSize(toBlobKey(namespace, digest));
        if (size < 0) return null;
        return new OCIContentInfo(digest, size);
    }

    public StreamHandle getBlob(final User user, final String namespace, final String digest) {
        if (!info.canView(user)) throw new UnauthorizedException();
        return this.backend.get(toBlobKey(namespace, digest));
    }

    public void uploadBlob(final User user, final String namespace, final String digest, final StreamHandle stream) {
        if (!info.canWrite(user)) throw new UnauthorizedException();

        this.backend.put(toBlobKey(namespace, digest), stream);
    }

    public UploadSessionHandle startUploadSession(final User user, final String namespace) {
        if (!info.canWrite(user)) throw new UnauthorizedException();

        final UUID sessionId = UUID.randomUUID();
        final String filePath = toPendingBlobKey(namespace, sessionId.toString());
        final String uploadId = this.backend.createMultipartUpload(filePath);

        return new UploadSessionHandle(sessionId, uploadId);
    }

    public void uploadPart(final User user,
                           final String namespace,
                           final UploadSessionHandle handle,
                           final int number,
                           final StreamHandle stream) {
        if (!info.canWrite(user)) throw new UnauthorizedException();

        this.backend.uploadPart(handle.uploadId(), toPendingBlobKey(namespace, handle.sessionId().toString()), number, stream);
    }

    public byte[] moveAndDigest(final String source,
                                final String destination,
                                final String algorithm) {
        final StreamHandle blob = this.backend.get(source);

        try {
            final MessageDigest md = MessageDigest.getInstance(switch (algorithm) {
                case "sha256" -> "SHA-256";
                case "sha512" -> "SHA-512";
                default -> throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
            });

            try (InputStream is = blob.stream();
                 DigestInputStream dis = new DigestInputStream(is, md)) {
                dis.transferTo(OutputStream.nullOutputStream());
            }

            this.backend.move(source, destination);

            return md.digest();
        } catch (final NoSuchAlgorithmException | IOException ex) {
            throw new RuntimeException(ex); // TODO: Proper error handling
        }
    }

    public void completeUploadSession(final User user,
                                      final String namespace,
                                      final String digest,
                                      final UploadSessionHandle handle) {
        if (!info.canWrite(user)) throw new UnauthorizedException();

        final String pendingKey = toPendingBlobKey(namespace, handle.sessionId().toString());
        final String blobKey = toBlobKey(namespace, digest);

        this.backend.completeMultipartUpload(handle.uploadId(), pendingKey);

        final String[] digestParts = digest.split(":");
        final String algorithm = digestParts[0];
        final String hash = digestParts[1];

        final byte[] finalDigest = moveAndDigest(pendingKey, blobKey, algorithm);
        final String digestString = Hex.encodeHexString(finalDigest);
        if (!Objects.equals(hash, digestString)) {
            abortUpload(user, handle.uploadId(), namespace, handle.sessionId());
            throw new BadRequestException("Digest mismatch");
        }

        final StoredFile file = new StoredFile();
        file.key = toBlobKey(namespace, digest);
        file.repository = info;
        file.created = Instant.now();
        file.updated = Instant.now();
        file.hashes = Map.of(algorithm, hash);
        file.persist();
    }

    public void abortUpload(final User user, final String uploadId, final String namespace, final UUID sessionId) {
        if (!info.canWrite(user)) throw new UnauthorizedException();

        this.backend.abortMultipartUpload(uploadId, toPendingBlobKey(namespace, sessionId.toString()));
    }

    @Override
    public void delete(final User user, final ArtifactVersion version, final boolean skipAuth) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    protected void putFile(final String key, final byte[] contents, final String contentType) {
        final ByteArrayInputStream stream = new ByteArrayInputStream(contents);
        this.backend.put(key, new StreamHandle(stream, contentType, contents.length));
    }

    protected ArtifactVersion updateOrCreateArtifactAndVersion(final Instant now, final String namespace, final String reference) {
        final ArtifactAndGroupId artifactAndGroupId = extractArtifactAndGroupId(namespace);
        final String groupId = artifactAndGroupId.groupId();
        final String artifactId = artifactAndGroupId.artifactId();

        Artifact artifact = Artifact.find("groupId = ?1 and artifactId = ?2 and repository.id = ?3", groupId, artifactId, info.id).firstResult();
        if (artifact == null) {
            artifact = new Artifact();
            artifact.groupId = groupId;
            artifact.artifactId = artifactId;
            artifact.repository = info;
            artifact.lastUpdated = now;
            artifact.persist();
        } else {
            artifact.lastUpdated = now;
        }

        ArtifactVersion version = ArtifactVersion.find("artifact = ?1 and version = ?2", artifact, reference).firstResult();
        if (version == null) {
            version = new ArtifactVersion();
            version.artifact = artifact;
            version.version = reference;
            version.created = now;
            version.updated = now;
            version.persist();
        } else {
            version.updated = now;
        }

        version.files = new ArrayList<>();
        return version;
    }

    /**
     * Computes the SHA-256 hash of the given data and validates it against a provided reference.
     * If the reference is a digest string (e.g., "sha256:<hash>" or "sha512:<hash>"), the method
     * verifies that the hash of the data matches the reference. If validation fails, a {@link BadRequestException}
     * is thrown. If the reference doesn't contain a digest, no validation is performed.
     *
     * @param data      The byte array containing the data to hash and validate.
     * @param reference The reference string, potentially containing a digest (e.g., "sha256:<hash>"
     *                  or "sha512:<hash>"), to validate against.
     * @return The computed SHA-256 hash of the input data as a hexadecimal string.
     * @throws BadRequestException If the computed hash does not match the digest in the reference string.
     */
    public String hashAndValidate(final byte[] data, final String reference) {
        final String hash = DigestUtils.sha256Hex(data);
        if (isDigest(reference)) {
            final String refHash = reference.substring(7);
            final String checkHash = reference.startsWith("sha256") ? hash : DigestUtils.sha512Hex(data);

            if (!Objects.equals(checkHash, refHash)) throw new BadRequestException("Digest mismatch");
        }
        return hash;
    }

    public StoredFile storeOrUpdateFileReference(final String fileKey, final Instant now, final String hash) {
        StoredFile file = StoredFile.find("key = ?1 and repository.id = ?2", fileKey, info.id).firstResult();
        if (file == null) {
            file = new StoredFile();
            file.key = fileKey;
            file.repository = info;
            file.created = now;
            file.updated = now;
            file.hashes = Map.of("sha256", hash);
            file.persist();
        } else {
            file.updated = now;
            file.hashes = Map.of("sha256", hash);
        }

        return file;
    }

    protected List<OCIManifestReference> collectionManifests(final JsonNode root) {
        if (!root.has("manifests")) return Collections.emptyList();

        final List<OCIManifestReference> manifests = new ArrayList<>();
        root.get("manifests")
                .elements()
                .forEachRemaining(manifestNode -> {
                    final String digest = manifestNode.get("digest").textValue();

                    final JsonNode platformNode = manifestNode.get("platform");
                    final String architecture = platformNode.get("architecture").textValue();
                    final String os = platformNode.get("os").textValue();

                    manifests.add(new OCIManifestReference(digest, architecture, os));
                });

        return manifests;
    }

    protected List<OCILayerReference> collectLayers(final JsonNode root) {
        if (!root.has("layers")) return Collections.emptyList();

        final List<OCILayerReference> layers = new ArrayList<>();
        root.get("layers")
                .elements()
                .forEachRemaining(layerNode -> layers.add(new OCILayerReference(layerNode.get("digest").textValue())));

        return layers;
    }

    protected Map<String, String> collectAnnotations(final JsonNode root) {
        if (!root.has("annotations")) return Collections.emptyMap();

        final Map<String, String> annotations = new HashMap<>();
        if (root.has("annotations")) {
            root.get("annotations")
                    .properties()
                    .forEach(entry -> annotations.put(entry.getKey(), entry.getValue().textValue()));
        }

        return annotations;
    }

    protected String tryGetConfigDigest(final JsonNode root) {
        if (!root.has("config")) return null;
        return root.get("config").get("digest").textValue();
    }

    protected ArtifactDetails parseDetails(final byte[] manifest) throws IOException {
        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root = mapper.readTree(manifest);

        final String configDigest = tryGetConfigDigest(root);
        final List<OCIManifestReference> manifests = collectionManifests(root);
        final List<OCILayerReference> layers = collectLayers(root);

        final Map<String, String> annotations = collectAnnotations(root);

        final String url = annotations.getOrDefault("org.opencontainers.image.url", annotations.get("org.opencontainers.image.source"));

        // TODO: Parse author and license information

        return new ArtifactDetails(
                annotations.get("org.opencontainers.image.description"),
                url,
                null,
                null,
                new OCIManifestDetails(configDigest, manifests, layers)
        );
    }

    protected List<StoredFile> collectAdditionalFileReferences(final ArtifactVersion version,
                                                               final String namespace,
                                                               final OCIManifestDetails details) {
        final List<StoredFile> files = new ArrayList<>();

        for (int i = 0; i < details.manifests().size(); i++) {
            final OCIManifestReference manifestReference = details.manifests().get(i);
            final String key = toManifestKey(namespace, manifestReference.digest());

            final StoredFile manifestFile = StoredFile.find("key = ?1 and repository.id = ?2", key, info.id).firstResult();
            if (manifestFile != null) version.files.add(manifestFile);
        }

        for (int i = 0; i < details.layers().size(); i++) {
            final OCILayerReference layerReference = details.layers().get(i);
            final String key = toBlobKey(namespace, layerReference.digest());

            final StoredFile layerFile = StoredFile.find("key = ?1 and repository.id = ?2", key, info.id).firstResult();
            if (layerFile != null) version.files.add(layerFile);
        }

        return files;
    }

    public void putManifest(final User user,
                            final String namespace,
                            final String reference,
                            final StreamHandle stream) {
        if (!info.canWrite(user)) throw new UnauthorizedException();
        if (stream.contentLength() > MAX_MANIFEST_SIZE) throw new RequestTooLongException();

        final Instant now = Instant.now();
        final ArtifactVersion version = updateOrCreateArtifactAndVersion(now, namespace, reference);

        final byte[] contents = stream.readAllBytes();

        final String hash = hashAndValidate(contents, reference);
        final String fileKey = toManifestKey(namespace, "sha256:" + hash);
        putFile(fileKey, contents, stream.contentType());

        final StoredFile file = storeOrUpdateFileReference(fileKey, now, hash);
        version.files.add(file);
        version.manifest = file;

        try {
            version.details = parseDetails(contents);

            if (version.details.additionalData() instanceof OCIManifestDetails manifestDetails) {
                final List<StoredFile> references = collectAdditionalFileReferences(version, namespace, manifestDetails);
                version.files.addAll(references);
            }
        } catch (final IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
