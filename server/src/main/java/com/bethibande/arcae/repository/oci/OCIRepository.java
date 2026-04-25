package com.bethibande.arcae.repository.oci;

import com.bethibande.arcae.jpa.artifact.Artifact;
import com.bethibande.arcae.jpa.artifact.ArtifactVersion;
import com.bethibande.arcae.jpa.files.FileUploadSession;
import com.bethibande.arcae.jpa.files.OCISubject;
import com.bethibande.arcae.jpa.files.StoredFile;
import com.bethibande.arcae.jpa.repository.Repository;
import com.bethibande.arcae.jpa.repository.RepositoryManager;
import com.bethibande.arcae.k8s.KubernetesSupport;
import com.bethibande.arcae.repository.*;
import com.bethibande.arcae.repository.*;
import com.bethibande.arcae.repository.backend.MultipartUploadStatus;
import com.bethibande.arcae.repository.backend.ObjectInfo;
import com.bethibande.arcae.repository.backend.S3Backend;
import com.bethibande.arcae.repository.oci.client.OCIMirrorSupport;
import com.bethibande.arcae.repository.oci.config.OCIRepositoryConfig;
import com.bethibande.arcae.repository.oci.config.OCIRoutingConfig;
import com.bethibande.arcae.repository.oci.index.OCIImageIndex;
import com.bethibande.arcae.repository.oci.index.OCIManifestIndexResult;
import com.bethibande.arcae.repository.security.AuthContext;
import com.bethibande.arcae.util.CopyingInputStream;
import com.bethibande.arcae.web.exception.RequestTooLongException;
import com.bethibande.arcae.web.repositories.oci.OCIError;
import com.bethibande.arcae.web.repositories.oci.OCIErrorCodes;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.TransactionSemantics;
import io.quarkus.security.UnauthorizedException;
import jakarta.persistence.LockModeType;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.utils.Lazy;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;

public class OCIRepository implements RepositoryUpdatedNotifier, HasUploadSessions {

    public static final long MAX_MANIFEST_SIZE = 10_000_000L;

    private static final Logger LOGGER = LoggerFactory.getLogger(OCIRepository.class);

    private final Repository info;
    private final OCIRepositoryConfig config;
    private final KubernetesSupport kubernetesSupport;

    private final Lazy<S3Backend> backend;
    private final OCIMirrorSupport mirrorSupport;
    protected OCIImageIndex index;

    private final Executor executor;

    public OCIRepository(final Repository info, final RepositoryApplicationContext ctx) throws JsonProcessingException {
        this(
                info,
                ctx.objectMapper().readValue(info.settings, OCIRepositoryConfig.class),
                ctx.kubernetesSupport(),
                ctx.executor(),
                ctx.repositoryManager()
        );
    }

    public OCIRepository(final Repository info,
                         final OCIRepositoryConfig config,
                         final KubernetesSupport kubernetesSupport,
                         final Executor executor,
                         final RepositoryManager repositoryManager) {
        this.info = info;
        this.config = config;
        this.kubernetesSupport = kubernetesSupport;
        this.executor = executor;

        this.backend = new Lazy<>(() -> new S3Backend(config.s3Config()));
        this.mirrorSupport = new OCIMirrorSupport(config, info, repositoryManager);
        this.index = new OCIImageIndex(this);
    }

    @Override
    public Map<String, Object> generateMetadata() {
        return Map.of(
                "HOST_NAME", config.externalHostname()
        );
    }

    @Override
    public void delete(final StoredFile file) {
        this.backend.getValue().delete(file.key);
        StoredFile.deleteById(file.id);
    }

    protected String[] getRoutedSubpaths() {
        return new String[]{"v2"};
    }

    @Override
    public void processUpdate(final UpdateType type) {
        final OCIRoutingConfig routing = this.config.routingConfig();

        if (!this.kubernetesSupport.hasHttpRouteSupport()) return;

        if (routing.enabled() && type != UpdateType.DELETE) {
            this.kubernetesSupport.createOrUpdateRepositoryHttpRoute(
                    info.name,
                    info.packageManager,
                    config.externalHostname(),
                    routing.targetService(),
                    routing.targetPort(),
                    routing.gatewayName(),
                    routing.gatewayNamespace(),
                    getRoutedSubpaths()
            );
        }
        if (!routing.enabled() || type == UpdateType.DELETE) {
            this.kubernetesSupport.deleteRepositoryHttpRouteIfExists(
                    info.name,
                    info.packageManager
            );
        }
    }

    @Override
    public Repository getInfo() {
        return info;
    }

    protected void checkViewAccess(final AuthContext auth) {
        if (!info.canView(auth)) {
            if (auth.isAnonymous()) throw new UnauthorizedException();
            throw new ForbiddenException();
        }
    }

    protected void checkWriteAccess(final AuthContext auth) {
        if (!info.canWrite(auth)) {
            if (auth.isAnonymous()) throw new UnauthorizedException();
            throw new ForbiddenException();
        }
    }

    public Artifact getArtifact(final AuthContext auth, final String namespace) {
        checkViewAccess(auth);

        final ArtifactAndGroupId artifactAndGroupId = extractArtifactAndGroupId(namespace);
        return Artifact.find(
                "groupId = ?1 and artifactId = ?2 and repository.id = ?3",
                artifactAndGroupId.groupId(),
                artifactAndGroupId.artifactId(),
                info.id
        ).firstResult();
    }

    public List<Artifact> getArtifacts(final AuthContext auth, final String groupId) {
        checkViewAccess(auth);;
        return Artifact.find(
                "groupId = ?1 and repository.id = ?2",
                groupId,
                info.id
        ).list();
    }

    public void deleteBlob(final AuthContext auth, final String namespace, final String digest) {
        checkWriteAccess(auth);

        final String key = toBlobKey(namespace, digest);
        final StoredFile file = StoredFile.find("key = ?1 and repository.id = ?2", key, info.id).firstResult();
        if (file == null) return;

        file.clearOwners();
        tryDeleteFile(file);
    }

    public void deleteManifest(final AuthContext auth, final String namespace, final String reference) {
        checkWriteAccess(auth);

        final StoredFile file = this.index.findManifestFileByReference(namespace, reference);
        if (file == null) return;

        if (!OCIDigestHelper.isDigest(reference)) {
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

            delete(AuthContext.ofSystem(auth), version);
        } else {
            file.clearOwners();
        }

        tryDeleteFile(file);
    }

    public String toBlobKey(final String namespace, final String path) {
        return "%s/%s/blobs/%s".formatted(info.name, namespace, path);
    }

    protected String toPendingBlobKey(final String namespace, final String path) {
        return "%s/%s/pending/%s".formatted(info.name, namespace, path);
    }

    public String toManifestKey(final String namespace, final String digest) {
        return "%s/%s/manifests/%s".formatted(info.name, namespace, digest);
    }

    public ArtifactAndGroupId extractArtifactAndGroupId(final String namespace) {
        final String artifactId = namespace.substring(namespace.lastIndexOf('/') + 1);
        final String groupId = namespace.lastIndexOf('/') > 0 ? namespace.substring(0, namespace.lastIndexOf('/')) : "";

        return new ArtifactAndGroupId(artifactId, groupId);
    }

    private OCIStreamHandle getManifestInternal(final String namespace, final String reference) {
        final S3Backend backend = this.backend.getValue();
        if (OCIDigestHelper.isDigest(reference)) {
            final StreamHandle handle = backend.get(toManifestKey(namespace, reference));
            if (handle == null) return null;

            return new OCIStreamHandle(
                    handle,
                    reference
            );
        } else {
            final StoredFile file = this.index.findManifestFileByTag(namespace, reference);
            if (file == null) return null;

            final String digest = file.key.substring(file.key.lastIndexOf('/') + 1);

            return new OCIStreamHandle(
                    backend.get(file.key),
                    digest
            );
        }
    }

    private boolean mirrorManifest(final AuthContext auth,
                                   final String namespace,
                                   final String reference) {
        final OCIStreamHandle remote = this.mirrorSupport.getManifestFromMirror(auth, namespace, reference);
        if (remote != null) {
            final OCIPutManifestResult result = putManifest(
                    AuthContext.ofSystem(auth),
                    namespace,
                    reference,
                    remote.streamHandle(),
                    true
            );

            QuarkusTransaction.requiringNew().run(() -> {
                if (result.version() != null) {
                    final long versionId = result.version().id;
                    this.index.updateVersionMirrorTTL(versionId);
                }
            });

            return true;
        }

        return false;
    }

    private boolean shouldUpdate(final String namespace, final String reference) {
        final ArtifactVersion version = this.index.getArtifactVersionByReference(namespace, reference, LockModeType.NONE);
        return version == null || (!version.isLocalArtifact() && version.mirrorTTLExpired(Instant.now()));
    }

    public OCIStreamHandle getManifest(final AuthContext auth, final String namespace, final String reference) {
        checkViewAccess(auth);

        if (OCIDigestHelper.isDigest(reference)) { // Digest can't change so we can skip the mirror if the data is present
            final OCIStreamHandle result = getManifestInternal(namespace, reference);
            if (result != null) return result;
        }

        if (this.mirrorSupport.isMirroringEnabled()
                && this.mirrorSupport.canMirror(auth)
                && shouldUpdate(namespace, reference)) {
            if (!this.mirrorSupport.isStoreArtifacts()) {
                return this.mirrorSupport.getManifestFromMirror(auth, namespace, reference);
            }

            mirrorManifest(auth, namespace, reference);
        }

        return getManifestInternal(namespace, reference);
    }

    public OCIContentInfo getManifestInfo(final AuthContext auth, final String namespace, final String reference) {
        checkViewAccess(auth);

        if (OCIDigestHelper.isDigest(reference)) {
            final OCIContentInfo result = getManifestInfoInternal(namespace, reference);
            if (result != null) return result;
        }

        if (this.mirrorSupport.isMirroringEnabled()
                && this.mirrorSupport.canMirror(auth)
                && shouldUpdate(namespace, reference)) {
            if (!this.mirrorSupport.isStoreArtifacts()) {
                return this.mirrorSupport.headManifestFromMirror(auth, namespace, reference);
            }

            if (!mirrorManifest(auth, namespace, reference)) return null;
        }

        return getManifestInfoInternal(namespace, reference);
    }

    private OCIContentInfo getManifestInfoInternal(final String namespace, final String reference) {
        final S3Backend backend = this.backend.getValue();
        if (OCIDigestHelper.isDigest(reference)) {
            final ObjectInfo info = backend.headObject(toManifestKey(namespace, reference));
            if (info == null) return null;
            return new OCIContentInfo(
                    reference,
                    info.contentLength(),
                    info.contentType()
            );
        } else {
            final StoredFile file = this.index.findManifestFileByTag(namespace, reference);
            if (file == null) return null;

            final String digest = file.key.substring(file.key.lastIndexOf('/') + 1);
            final ObjectInfo info = backend.headObject(file.key);
            if (info == null) return null;

            return new OCIContentInfo(
                    digest,
                    info.contentLength(),
                    info.contentType()
            );
        }
    }

    public OCIContentInfo getBlobInfo(final AuthContext auth, final String namespace, final String digest) {
        checkViewAccess(auth);

        final ObjectInfo info = this.backend.getValue().headObject(toBlobKey(namespace, digest));
        if (info != null) return new OCIContentInfo(digest, info.contentLength(), info.contentType());

        if (this.mirrorSupport.isMirroringEnabled() && this.mirrorSupport.canMirror(auth)) {
            return this.mirrorSupport.headBlobFromMirror(auth, namespace, digest);
        }

        return null;
    }

    public StreamHandle getBlob(final AuthContext auth, final String namespace, final String digest) {
        checkViewAccess(auth);
        final StreamHandle handle = this.backend.getValue().get(toBlobKey(namespace, digest));
        if (handle != null) return handle;

        if (this.mirrorSupport.isMirroringEnabled() && this.mirrorSupport.canMirror(auth)) {
            final StreamHandle result = this.mirrorSupport.getBlobFromMirror(auth, namespace, digest);
            if (result != null && this.mirrorSupport.isStoreArtifacts()) {
                final InputStream pipe = pipeAndStoreData(namespace, digest, result);

                return new StreamHandle(
                        pipe,
                        result.contentType(),
                        result.contentLength()
                );
            }

            return result;
        }

        return null;
    }

    private InputStream pipeAndStoreData(final String namespace, final String digest, final StreamHandle handle) {
        try {
            final PipedInputStream sink = new PipedInputStream(1024 * 1024);
            final PipedOutputStream pipe = new PipedOutputStream(sink);

            final CopyingInputStream source = new CopyingInputStream(handle.stream(), pipe);
            this.executor.execute(() -> {
                final StreamHandle driverHandle = new StreamHandle(
                        source,
                        handle.contentType(),
                        handle.contentLength()
                );
                this.uploadBlob(AuthContext.ofSystem(), namespace, digest, driverHandle);
            });

            return sink;
        } catch (final IOException ex) {
            LOGGER.error("Failed to pipe stream when mirroring data");
            throw new RuntimeException(ex);
        }
    }

    private StreamHandle mirrorBlobRange(final AuthContext auth,
                                         final String namespace,
                                         final String digest,
                                         final long offset,
                                         final long length) {
        return this.mirrorSupport.getBlobRangeFromMirror(
                auth,
                namespace,
                digest,
                offset,
                offset + length - 1
        );
    }

    public StreamHandle getBlob(final AuthContext auth,
                                final String namespace,
                                final String digest,
                                final long offset,
                                final long length) {
        checkViewAccess(auth);

        final String key = toBlobKey(namespace, digest);
        final StreamHandle handle = this.backend.getValue().get(key, offset, length);

        if (handle == null && this.mirrorSupport.isMirroringEnabled()) {
            return mirrorBlobRange(auth, namespace, digest, offset, length); // Can't store this here as it is just a part of the blob
        }

        return handle;
    }

    public WebApplicationException createBlobExistsException() {
        return new WebApplicationException(Response.status(Response.Status.CONFLICT)
                .entity(OCIError.of(OCIErrorCodes.DENIED, "Blob already exists", "The blob already exists in the repository and cannot be overwritten"))
                .build());
    }

    public void uploadBlob(final AuthContext auth,
                           final String namespace,
                           final String digest,
                           final StreamHandle stream) {
        checkWriteAccess(auth);

        final S3Backend backend = this.backend.getValue();
        final String key = toBlobKey(namespace, digest);
        if (this.config.allowRedeployments() != null
                && !this.config.allowRedeployments()
                && backend.head(key)) {
            throw createBlobExistsException();
        }

        QuarkusTransaction.runner(TransactionSemantics.JOIN_EXISTING).run(() -> {
            this.index.putBlob(key, digest, stream.contentLength());
        });

        backend.put(key, stream);
    }

    @Override
    public void abortUploadSession(final FileUploadSession session) {
        final S3Backend backend = this.backend.getValue();
        backend.abortMultipartUpload(session.uploadSessionId, session.fileKey);
        backend.delete(session.fileKey); // In case the upload in S3 was completed but not in the database
    }

    public UploadSessionHandle startUploadSession(final AuthContext auth, final String namespace) {
        checkWriteAccess(auth);

        final UUID sessionId = UUID.randomUUID();
        final String filePath = toPendingBlobKey(namespace, sessionId.toString());
        final String uploadId = this.backend.getValue().createMultipartUpload(filePath);

        QuarkusTransaction.requiringNew().run(() -> this.index.recordUploadSession(Instant.now(), filePath, uploadId));

        return new UploadSessionHandle(sessionId, uploadId);
    }

    public void uploadPart(final AuthContext auth,
                           final String namespace,
                           final UploadSessionHandle sessionHandle,
                           final int number,
                           final StreamHandle handle) {
        checkWriteAccess(auth);

        final String previousState = this.index.fetchUploadSessionHashState(sessionHandle.uploadId());
        final SHA256Digest digest = previousState != null
                ? new SHA256Digest(Base64.getDecoder().decode(previousState))
                : new SHA256Digest();

        final InputStream stream = new org.bouncycastle.crypto.io.DigestInputStream(handle.stream(), digest);
        this.backend.getValue().uploadPart(
                sessionHandle.uploadId(),
                toPendingBlobKey(namespace, sessionHandle.sessionId().toString()),
                number,
                new StreamHandle(
                        stream,
                        handle.contentType(),
                        handle.contentLength()
                )
        );

        final byte[] state = digest.getEncodedState();
        final String newHashSate = Base64.getEncoder().encodeToString(state);
        this.index.updateUploadSessionHashState(sessionHandle.uploadId(), newHashSate);
    }

    public MultipartUploadStatus getUploadStatus(final AuthContext auth,
                                                 final String namespace,
                                                 final UploadSessionHandle handle) {
        checkWriteAccess(auth);

        return this.backend.getValue().headUpload(handle.uploadId(), toPendingBlobKey(namespace, handle.sessionId().toString()));
    }

    private WebApplicationException createMultiPartAlgorithmNotAllowedException(final String algorithm) {
        return new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                .entity(OCIError.of(
                        OCIErrorCodes.DIGEST_INVALID,
                        "multi-part %s not allowed".formatted(algorithm),
                        "%s is not allowed for multi-part uploads, only sha256 is permitted".formatted(algorithm)
                ))
                .build());
    }

    protected BadRequestException createInvalidDigestException() {
        return new BadRequestException(Response.status(Response.Status.BAD_REQUEST)
                .entity(OCIError.of(OCIErrorCodes.DIGEST_INVALID, "Digest mismatch", "The provided digest does not match the actual digest of the uploaded data"))
                .build());
    }

    private void verifyDigestAndAlgorithm(final AuthContext auth,
                                          final String namespace,
                                          final String digest,
                                          final UploadSessionHandle handle) {
        final String[] digestParts = digest.split(":");
        final String algorithm = digestParts[0];
        final String hash = digestParts[1];

        if (!Objects.equals(algorithm.toLowerCase(), "sha256")) {
            this.abortUpload(auth, handle.uploadId(), namespace, handle.sessionId());
            throw createMultiPartAlgorithmNotAllowedException(algorithm);
        }

        final String hashState = this.index.fetchUploadSessionHashState(handle.uploadId());
        final SHA256Digest md = new SHA256Digest(Base64.getDecoder().decode(hashState));
        final byte[] finalDigest = new byte[md.getDigestSize()];
        md.doFinal(finalDigest, 0);

        final String digestString = Hex.encodeHexString(finalDigest);
        if (!Objects.equals(hash, digestString)) {
            abortUpload(auth, handle.uploadId(), namespace, handle.sessionId());
            throw createInvalidDigestException();
        }
    }

    public void completeUploadSession(final AuthContext auth,
                                      final String namespace,
                                      final String digest,
                                      final UploadSessionHandle handle) {
        checkWriteAccess(auth);

        final S3Backend backend = this.backend.getValue();
        final String pendingKey = toPendingBlobKey(namespace, handle.sessionId().toString());
        final String blobKey = toBlobKey(namespace, digest);

        if (this.config.allowRedeployments() != null
                && !this.config.allowRedeployments()
                && backend.head(blobKey)) {
            backend.abortMultipartUpload(handle.uploadId(), pendingKey);
            throw createBlobExistsException();
        }

        verifyDigestAndAlgorithm(auth, namespace, digest, handle);

        backend.completeMultipartUpload(handle.uploadId(), pendingKey);

        final ObjectInfo blobInfo = backend.headObject(pendingKey);

        QuarkusTransaction.runner(TransactionSemantics.JOIN_EXISTING).run(() -> {
            this.index.putBlob(blobKey, digest, blobInfo.contentLength());
        });

        backend.move(pendingKey, blobKey);

        QuarkusTransaction.joiningExisting().run(() -> this.index.endUploadSession(handle.uploadId()));
    }

    public void abortUpload(final AuthContext auth,
                            final String uploadId,
                            final String namespace,
                            final UUID sessionId) {
        checkWriteAccess(auth);

        QuarkusTransaction.joiningExisting().run(() -> this.index.endUploadSession(uploadId));
        this.backend.getValue().abortMultipartUpload(uploadId, toPendingBlobKey(namespace, sessionId.toString()));
    }

    @Override
    public void delete(final AuthContext auth, final ArtifactVersion version) {
        if (!info.canWrite(auth)) throw new UnauthorizedException();

        final List<StoredFile> files = new ArrayList<>(version.files);
        version.files.clear();
        version.manifest = null;
        for (int i = 0; i < files.size(); i++) {
            final StoredFile file = files.get(i);
            tryDeleteFile(file);
        }

        version.delete();
        if (version.artifact.countVersions() <= 0) {
            delete(AuthContext.ofSystem(auth), version.artifact);
        }
    }

    public void delete(final AuthContext auth, final Artifact artifact) {
        if (!info.canWrite(auth)) throw new UnauthorizedException();

        ArtifactVersion.<ArtifactVersion>find("artifact = ?1", artifact)
                .stream()
                .forEach(version -> delete(AuthContext.ofSystem(auth), version));

        Artifact.deleteById(artifact.id);
    }

    protected void putFile(final String key, final byte[] contents, final String contentType) {
        final ByteArrayInputStream stream = new ByteArrayInputStream(contents);
        this.backend.getValue().put(key, new StreamHandle(stream, contentType, contents.length));
    }

    public void tryDeleteFile(final StoredFile file) {
        if (file.usages() > 0) return;

        OCISubject.delete("source = ?1 OR subject = ?1", file);

        StoredFile.deleteById(file.id);
        this.backend.getValue().delete(file.key);
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
        if (OCIDigestHelper.isDigest(reference)) {
            final String refHash = reference.substring(7);
            final String checkHash = reference.startsWith("sha256") ? hash : DigestUtils.sha512Hex(data);

            if (!Objects.equals(checkHash, refHash)) throw createInvalidDigestException();
        }
        return hash;
    }

    public OCIPutManifestResult putManifest(final AuthContext auth,
                                            final String namespace,
                                            final String reference,
                                            final StreamHandle stream,
                                            final boolean isMirrorRequest) {
        checkWriteAccess(auth);
        if (stream.contentLength() > MAX_MANIFEST_SIZE) throw new RequestTooLongException();

        final byte[] contents = stream.readAllBytes();

        final String hash = hashAndValidate(contents, reference);
        final String digest = "sha256:" + hash;
        final String fileKey = toManifestKey(namespace, digest);

        if (this.config.allowRedeployments() != null
                && !this.config.allowRedeployments()
                && this.backend.getValue().head(fileKey)) {
            throw new WebApplicationException(Response.status(Response.Status.CONFLICT)
                    .entity(OCIError.of(OCIErrorCodes.DENIED, "Manifest already exists", "The manifest already exists in the repository"))
                    .build());
        }

        final OCIManifestIndexResult result = QuarkusTransaction.requiringNew()
                .call(() -> this.index.putManifest(
                        namespace,
                        reference,
                        digest,
                        contents,
                        stream.contentType(),
                        stream.contentLength(),
                        isMirrorRequest
                ));

        putFile(fileKey, contents, stream.contentType());

        return new OCIPutManifestResult(
                result.file(),
                digest,
                result.version(),
                result.subject()
        );
    }

    @Override
    public void close() {
        this.backend.close();
        this.mirrorSupport.close();
    }
}
