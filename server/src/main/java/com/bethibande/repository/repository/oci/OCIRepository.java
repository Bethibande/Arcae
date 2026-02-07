package com.bethibande.repository.repository.oci;

import com.bethibande.repository.jpa.StoredFile;
import com.bethibande.repository.jpa.artifact.ArtifactVersion;
import com.bethibande.repository.jpa.repository.Repository;
import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.repository.ManagedRepository;
import com.bethibande.repository.repository.StreamHandle;
import com.bethibande.repository.repository.backend.S3Backend;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.BadRequestException;
import org.apache.commons.codec.binary.Hex;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class OCIRepository implements ManagedRepository {

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

    protected String toBlobKey(final String namespace, final String path) {
        return "%s/blobs/%s/%s".formatted(info.name, namespace, path);
    }

    protected String toPendingBlobKey(final String namespace, final String path) {
        return "%s/pending/%s/%s".formatted(info.name, namespace, path);
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
}
