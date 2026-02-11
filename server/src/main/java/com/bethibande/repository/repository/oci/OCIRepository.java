package com.bethibande.repository.repository.oci;

import com.bethibande.repository.jpa.artifact.Artifact;
import com.bethibande.repository.jpa.artifact.ArtifactDetails;
import com.bethibande.repository.jpa.artifact.ArtifactVersion;
import com.bethibande.repository.jpa.files.OCISubject;
import com.bethibande.repository.jpa.files.StoredFile;
import com.bethibande.repository.jpa.repository.Repository;
import com.bethibande.repository.jpa.repository.RepositoryMetadataKey;
import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.k8s.KubernetesSupport;
import com.bethibande.repository.repository.*;
import com.bethibande.repository.repository.backend.MultipartUploadStatus;
import com.bethibande.repository.repository.backend.ObjectInfo;
import com.bethibande.repository.repository.backend.S3Backend;
import com.bethibande.repository.repository.oci.config.OCIRepositoryConfig;
import com.bethibande.repository.repository.oci.config.OCIRoutingConfig;
import com.bethibande.repository.repository.oci.details.OCILayerReference;
import com.bethibande.repository.repository.oci.details.OCIManifestDetails;
import com.bethibande.repository.repository.oci.details.OCIManifestReference;
import com.bethibande.repository.web.exception.RequestTooLongException;
import com.bethibande.repository.web.repositories.oci.OCIError;
import com.bethibande.repository.web.repositories.oci.OCIErrorCodes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.TransactionSemantics;
import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.Response;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

public class OCIRepository implements ManagedRepository, RepositoryUpdatedNotifier {

    public static final long MAX_MANIFEST_SIZE = 10_000_000L;

    private static final Logger LOGGER = LoggerFactory.getLogger(OCIRepository.class);

    private final Repository info;
    private final OCIRepositoryConfig config;
    private final KubernetesSupport kubernetesSupport;

    private final S3Backend backend;

    public OCIRepository(final Repository info, final RepositoryApplicationContext ctx) throws JsonProcessingException {
        this(info, ctx.objectMapper().readValue(info.settings, OCIRepositoryConfig.class), ctx.kubernetesSupport());
    }

    public OCIRepository(final Repository info,
                         final OCIRepositoryConfig config,
                         final KubernetesSupport kubernetesSupport) {
        this.info = info;
        this.config = config;
        this.kubernetesSupport = kubernetesSupport;

        this.backend = new S3Backend(config.s3Config());
    }

    @Override
    public void processUpdate(final UpdateType type) {
        final OCIRoutingConfig routing = this.config.routingConfig();

        if (!this.kubernetesSupport.hasHttpRouteSupport()) return;

        if (routing.enabled() && type != UpdateType.DELETE) {
            this.kubernetesSupport.createOrUpdateRepositoryHttpRoute(
                    info.name,
                    info.packageManager,
                    info.getMetadata(RepositoryMetadataKey.HOST_NAME),
                    routing.targetService(),
                    routing.targetPort(),
                    routing.gatewayName(),
                    routing.gatewayNamespace()
            );
        }
        if (!routing.enabled() || type == UpdateType.DELETE) {
            this.kubernetesSupport.deleteRepositoryHttpRouteIfExists(
                    info.name,
                    info.packageManager
            );
        }
        info.persist();
    }

    @Override
    public Repository getInfo() {
        return info;
    }

    protected void checkViewAccess(final User user) {
        if (!info.canView(user)) {
            if (user == null) throw new UnauthorizedException();
            throw new ForbiddenException();
        }
    }

    protected void checkWriteAccess(final User user) {
        if (!info.canWrite(user)) {
            if (user == null) throw new UnauthorizedException();
            throw new ForbiddenException();
        }
    }

    public Artifact getArtifact(final User user, final String namespace) {
        checkViewAccess(user);

        final ArtifactAndGroupId artifactAndGroupId = extractArtifactAndGroupId(namespace);
        return Artifact.find(
                "groupId = ?1 and artifactId = ?2 and repository.id = ?3",
                artifactAndGroupId.groupId(),
                artifactAndGroupId.artifactId(),
                info.id
        ).firstResult();
    }

    public void deleteBlob(final User user, final String namespace, final String digest) {
        checkWriteAccess(user);

        final String key = toBlobKey(namespace, digest);
        final StoredFile file = StoredFile.find("key = ?1 and repository.id = ?2", key, info.id).firstResult();
        if (file == null) return;

        file.clearOwners();
        tryDeleteFile(file);
    }

    public void deleteManifest(final User user, final String namespace, final String reference) {
        checkWriteAccess(user);

        final StoredFile file = findManifestFileByReference(namespace, reference);
        if (file == null) return;

        if (!isDigest(reference)) {
            final ArtifactAndGroupId artifactAndGroupId = extractArtifactAndGroupId(namespace);
            final Artifact artifact = Artifact.find(
                    "groupId = ?1 and artifactId = ?2 and repository.id = ?3",
                    artifactAndGroupId.groupId(),
                    artifactAndGroupId.artifactId(),
                    info.id
            ).firstResult();
            if (artifact == null) return;

            final ArtifactVersion version = ArtifactVersion.find("artifact = ?1 and version = ?2", artifact, reference).firstResult();
            if (version == null) return;

            delete(user, version, true);
        } else {
            file.clearOwners();
        }

        tryDeleteFile(file);
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

    protected StoredFile findManifestFileByReference(final String namespace, final String reference) {
        if (isDigest(reference)) {
            final String key = toManifestKey(namespace, reference);
            return StoredFile.find("key = ?1 and repository.id = ?2", key, info.id).firstResult();
        } else {
            return findManifestFileByTag(namespace, reference);
        }
    }

    protected StoredFile findManifestFileByTag(final String namespace, final String tag) {
        final ArtifactAndGroupId artifactAndGroupId = extractArtifactAndGroupId(namespace);
        final String groupId = artifactAndGroupId.groupId();
        final String artifactId = artifactAndGroupId.artifactId();

        return QuarkusTransaction.runner(TransactionSemantics.JOIN_EXISTING).call(() -> {
            final Artifact artifact = Artifact.find("groupId = ?1 and artifactId = ?2 and repository.id = ?3", groupId, artifactId, info.id).firstResult();
            if (artifact == null) return null;

            final ArtifactVersion version = ArtifactVersion.find("artifact = ?1 and version = ?2", artifact, tag).firstResult();
            if (version == null) return null;

            return version.manifest;
        });
    }

    protected boolean isDigest(final String reference) {
        return reference.matches("^sha256:[0-9a-fA-F]{64}$|^sha512:[0-9a-fA-F]{128}$");
    }

    public OCIStreamHandle getManifest(final User user, final String namespace, final String reference) {
        checkViewAccess(user);

        if (isDigest(reference)) {
            final StreamHandle handle = this.backend.get(toManifestKey(namespace, reference));
            if (handle == null) return null;

            return new OCIStreamHandle(
                    handle,
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
        checkViewAccess(user);

        if (reference.matches("^sha256:[0-9a-fA-F]{64}$|^sha512:[0-9a-fA-F]{128}$")) {
            final ObjectInfo info = this.backend.headObject(toManifestKey(namespace, reference));
            if (info == null) return null;
            return new OCIContentInfo(
                    reference,
                    info.contentLength(),
                    info.contentType()
            );
        } else {
            final StoredFile file = findManifestFileByTag(namespace, reference);
            if (file == null) return null;

            final String digest = file.key.substring(file.key.lastIndexOf('/') + 1);
            final ObjectInfo info = this.backend.headObject(file.key);
            if (info == null) return null;

            return new OCIContentInfo(
                    digest,
                    info.contentLength(),
                    info.contentType()
            );
        }
    }

    public OCIContentInfo getBlobInfo(final User user, final String namespace, final String digest) {
        checkViewAccess(user);

        final ObjectInfo info = this.backend.headObject(toBlobKey(namespace, digest));
        if (info == null) return null;
        return new OCIContentInfo(digest, info.contentLength(), info.contentType());
    }

    public StreamHandle getBlob(final User user, final String namespace, final String digest) {
        checkViewAccess(user);
        return this.backend.get(toBlobKey(namespace, digest));
    }

    public StreamHandle getBlob(final User user,
                                final String namespace,
                                final String digest,
                                final long offset,
                                final long length) {
        checkViewAccess(user);
        return this.backend.get(toBlobKey(namespace, digest), offset, length);
    }

    public void uploadBlob(final User user, final String namespace, final String digest, final StreamHandle stream) {
        checkWriteAccess(user);

        final String key = toBlobKey(namespace, digest);
        this.backend.put(key, stream);

        QuarkusTransaction.runner(TransactionSemantics.JOIN_EXISTING).run(() -> {
            final String[] digestParts = digest.split(":");

            final Instant now = Instant.now();
            final StoredFile file = new StoredFile();
            file.key = key;
            file.repository = info;
            file.created = now;
            file.updated = now;
            file.contentType = stream.contentType();
            file.contentLength = stream.contentLength();
            file.hashes = Map.of(digestParts[0], digestParts[1]);
            file.persist();
        });
    }

    public UploadSessionHandle startUploadSession(final User user, final String namespace) {
        checkWriteAccess(user);

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
        checkWriteAccess(user);

        this.backend.uploadPart(handle.uploadId(), toPendingBlobKey(namespace, handle.sessionId().toString()), number, stream);
    }

    public MultipartUploadStatus getUploadStatus(final User user,
                                                 final String namespace,
                                                 final UploadSessionHandle handle) {
        checkWriteAccess(user);

        return this.backend.headUpload(handle.uploadId(), toPendingBlobKey(namespace, handle.sessionId().toString()));
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
        checkWriteAccess(user);

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
            throw new BadRequestException(Response.status(Response.Status.BAD_REQUEST)
                    .entity(OCIError.of(OCIErrorCodes.DIGEST_INVALID, "Digest mismatch", "The provided digest does not match the actual digest of the uploaded data"))
                    .build());
        }

        final ObjectInfo blobInfo = this.backend.headObject(blobKey);

        QuarkusTransaction.runner(TransactionSemantics.JOIN_EXISTING).run(() -> {
            final StoredFile file = new StoredFile();
            file.key = toBlobKey(namespace, digest);
            file.repository = info;
            file.created = Instant.now();
            file.updated = Instant.now();
            file.contentType = "application/octet-stream";
            file.contentLength = blobInfo.contentLength();
            file.hashes = Map.of(algorithm, hash);
            file.persist();
        });

    }

    public void abortUpload(final User user, final String uploadId, final String namespace, final UUID sessionId) {
        checkWriteAccess(user);

        this.backend.abortMultipartUpload(uploadId, toPendingBlobKey(namespace, sessionId.toString()));
    }

    @Override
    public void delete(final User user, final ArtifactVersion version, final boolean skipAuth) {
        if (!skipAuth && !info.canWrite(user)) throw new UnauthorizedException();

        final List<StoredFile> files = new ArrayList<>(version.files);
        version.files.clear();
        version.manifest = null;
        for (int i = 0; i < files.size(); i++) {
            final StoredFile file = files.get(i);
            tryDeleteFile(file);
        }

        version.delete();
        if (version.artifact.countVersions() <= 0) {
            delete(user, version.artifact, true);
        }
    }

    public void delete(final User user, final Artifact artifact, final boolean skipAuth) {
        if (!skipAuth && !info.canWrite(user)) throw new UnauthorizedException();

        ArtifactVersion.<ArtifactVersion>find("artifact = ?1", artifact)
                .stream()
                .forEach(version -> delete(user, version, true));

        Artifact.deleteById(artifact.id);
    }

    protected void putFile(final String key, final byte[] contents, final String contentType) {
        final ByteArrayInputStream stream = new ByteArrayInputStream(contents);
        this.backend.put(key, new StreamHandle(stream, contentType, contents.length));
    }

    protected void tryDeleteFile(final StoredFile file) {
        if (file.usages() > 0) return;

        OCISubject.delete("source = ?1 OR subject = ?1", file);

        StoredFile.deleteById(file.id);
        this.backend.delete(file.key);
    }

    protected ArtifactVersion updateOrCreateArtifactAndVersion(final Instant now, final String namespace, final String reference) {
        final ArtifactAndGroupId artifactAndGroupId = extractArtifactAndGroupId(namespace);
        final String groupId = artifactAndGroupId.groupId();
        final String artifactId = artifactAndGroupId.artifactId();

        final Artifact artifact = getOrCreateArtifact(groupId, artifactId, now);
        final ArtifactVersion version = getOrCreateArtifactVersion(artifact, reference, now);

        if (version.files != null) {
            final List<StoredFile> oldFiles = new ArrayList<>(version.files);
            version.files.clear();

            for (int i = 0; i < oldFiles.size(); i++) {
                final StoredFile file = oldFiles.get(i);
                tryDeleteFile(file);
            }
        } else {
            version.files = new ArrayList<>();
        }

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

    public StoredFile storeOrUpdateFileReference(final String fileKey,
                                                 final Instant now,
                                                 final String hash,
                                                 final String contentType,
                                                 final long contentLength) {
        StoredFile file = StoredFile.find("key = ?1 and repository.id = ?2", fileKey, info.id).firstResult();
        if (file == null) {
            file = new StoredFile();
            file.key = fileKey;
            file.repository = info;
            file.created = now;
            file.updated = now;
            file.contentType = contentType;
            file.contentLength = contentLength;
            file.hashes = Map.of("sha256", hash);
            file.persist();
        } else {
            file.updated = now;
            file.contentType = contentType;
            file.contentLength = contentLength;
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

    protected List<StoredFile> collectAdditionalFileReferences(final String namespace,
                                                               final OCIManifestDetails details) {
        final List<StoredFile> files = new ArrayList<>();

        for (int i = 0; i < details.manifests().size(); i++) {
            final OCIManifestReference manifestReference = details.manifests().get(i);
            final String key = toManifestKey(namespace, manifestReference.digest());

            final StoredFile manifestFile = StoredFile.find("key = ?1 and repository.id = ?2", key, info.id).firstResult();
            if (manifestFile != null) {
                files.add(manifestFile);

                try (final StreamHandle handle = this.backend.get(manifestFile.key)) {
                    final ArtifactDetails childDetails = parseDetails(handle.readAllBytes());
                    files.addAll(collectAdditionalFileReferences(namespace, (OCIManifestDetails) childDetails.additionalData()));
                } catch (final IOException ex) {
                    LOGGER.error("Failed to parse manifest details for {}", manifestFile.key, ex);
                }
            }
        }

        for (int i = 0; i < details.layers().size(); i++) {
            final OCILayerReference layerReference = details.layers().get(i);
            final String key = toBlobKey(namespace, layerReference.digest());

            final StoredFile layerFile = StoredFile.find("key = ?1 and repository.id = ?2", key, info.id).firstResult();
            if (layerFile != null) files.add(layerFile);
        }

        final StoredFile configFile = StoredFile.find("key = ?1 and repository.id = ?2", toBlobKey(namespace, details.configDigest()), info.id).firstResult();
        if (configFile != null) files.add(configFile);

        return files;
    }

    protected OCISubject createSubjectInfo(final String namespace, final StoredFile source, final byte[] contents) {
        try {
            final JsonNode root = new ObjectMapper().readTree(contents);

            if (root.has("subject")) {
                // Check if we already have metadata for this source file to avoid duplicates
                final OCISubject subject = OCISubject.<OCISubject>find("source = ?1", source)
                        .firstResultOptional()
                        .orElseGet(OCISubject::new);

                subject.source = source;
                subject.sourceDigest = source.key.substring(source.key.lastIndexOf('/') + 1);
                subject.namespace = namespace;

                subject.subjectDigest = root.get("subject").get("digest").textValue();
                final String subjectKey = toManifestKey(namespace, subject.subjectDigest);
                subject.subject = StoredFile.find("key = ?1 and repository.id = ?2", subjectKey, info.id).firstResult();

                if (subject.subject == null) {
                    LOGGER.warn("Subject file {} not found for referrer {}", subjectKey, source.key);
                }

                if (root.has("artifactType")) {
                    subject.artifactType = root.get("artifactType").textValue();
                } else if (root.has("config") && root.get("config").has("mediaType")) {
                    subject.artifactType = root.get("config").get("mediaType").textValue();
                } else {
                    subject.artifactType = source.contentType;
                }

                if (root.has("annotations")) {
                    if (subject.annotations == null) {
                        subject.annotations = new HashMap<>();
                    } else {
                        subject.annotations.clear();
                    }
                    root.get("annotations").properties().forEach(entry -> {
                        subject.annotations.put(entry.getKey(), entry.getValue().asText());
                    });
                }

                subject.persist();
                return subject;
            }
        } catch (final IOException ex) {
            LOGGER.error("Failed to parse manifest JSON for {}", source.key, ex);
        }
        return null;
    }

    public PutOCIManifestResult putManifest(final User user,
                                            final String namespace,
                                            final String reference,
                                            final StreamHandle stream) {
        checkWriteAccess(user);
        if (stream.contentLength() > MAX_MANIFEST_SIZE) throw new RequestTooLongException();

        final byte[] contents = stream.readAllBytes();

        final String hash = hashAndValidate(contents, reference);
        final String fileKey = toManifestKey(namespace, "sha256:" + hash);
        putFile(fileKey, contents, stream.contentType());

        return QuarkusTransaction.runner(TransactionSemantics.JOIN_EXISTING).call(() -> {
            final Instant now = Instant.now();
            final StoredFile file = storeOrUpdateFileReference(
                    fileKey,
                    now,
                    hash,
                    stream.contentType(),
                    stream.contentLength()
            );
            final OCISubject subject = createSubjectInfo(namespace, file, contents);

            if (!isDigest(reference)) {
                final ArtifactVersion version = updateOrCreateArtifactAndVersion(now, namespace, reference);
                version.files.add(file);
                version.manifest = file;

                try {
                    version.details = parseDetails(contents);

                    if (version.details.additionalData() instanceof OCIManifestDetails manifestDetails) {
                        version.files.addAll(collectAdditionalFileReferences(namespace, manifestDetails));
                    }
                } catch (final IOException ex) {
                    throw new RuntimeException(ex);
                }

                return new PutOCIManifestResult(
                        file,
                        version,
                        subject
                );
            }

            return new PutOCIManifestResult(file, null, subject);
        });
    }
}
