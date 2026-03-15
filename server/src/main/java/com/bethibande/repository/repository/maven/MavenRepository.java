package com.bethibande.repository.repository.maven;

import com.bethibande.repository.jpa.artifact.Artifact;
import com.bethibande.repository.jpa.artifact.ArtifactVersion;
import com.bethibande.repository.jpa.files.StoredFile;
import com.bethibande.repository.jpa.repository.Repository;
import com.bethibande.repository.repository.ManagedRepository;
import com.bethibande.repository.repository.RepositoryApplicationContext;
import com.bethibande.repository.repository.StreamHandle;
import com.bethibande.repository.repository.backend.S3Backend;
import com.bethibande.repository.repository.security.AuthContext;
import com.bethibande.repository.util.CopyingInputStream;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.TransactionSemantics;
import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.BadRequestException;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.utils.Lazy;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class MavenRepository implements ManagedRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenRepository.class);

    public static final String POM_FILE_EXTENSION = ".pom";
    public static final long MAX_POM_FILE_SIZE = 5_000_000L;

    private final Repository info;
    private final MavenRepositoryConfig config;
    private final MavenFileIndexer fileIndexer;
    private final MavenMirrorSupport mirrorSupport;

    private final Lazy<S3Backend> backend;
    private final Executor executor;

    public MavenRepository(final Repository info, final RepositoryApplicationContext ctx) throws JsonProcessingException {
        this(info, ctx.objectMapper().readValue(info.settings, MavenRepositoryConfig.class), ctx.executor());
    }

    public MavenRepository(final Repository info,
                           final MavenRepositoryConfig config,
                           final Executor executor) {
        this.info = info;
        this.config = config;
        this.backend = new Lazy<>(() -> new S3Backend(config.s3Config()));
        this.fileIndexer = new MavenFileIndexer(info, this);
        this.mirrorSupport = new MavenMirrorSupport(this, config.mirrorConfig());
        this.executor = executor;
    }

    @Override
    public Repository getInfo() {
        return info;
    }

    @Override
    public Map<String, Object> generateMetadata() {
        return Collections.emptyMap();
    }

    @Override
    public void delete(final StoredFile file) {
        this.backend.getValue().delete("%s/%s".formatted(info.name, file.key));
        StoredFile.deleteById(file.id);
    }

    /**
     * Pipes the given stream to the backend and sinks a copy of the data into the returned stream.
     * Closing the returned stream will not cause the store operation to abort or otherwise fail.
     */
    protected StreamHandle pipeAndStore(final AuthContext auth, final StreamHandle handle, final String path) throws IOException {
        final PipedInputStream sink = new PipedInputStream(1024 * 1024);
        final PipedOutputStream pipe = new PipedOutputStream(sink);

        final CopyingInputStream source = new CopyingInputStream(handle.stream(), pipe);

        this.executor.execute(() -> this.put(
                AuthContext.ofSystem(auth),
                path,
                new StreamHandle(source, handle.contentType(), handle.contentLength())
        ));

        return new StreamHandle(
                sink, // Pass sink to the client to ensure a fail client connection doesn't abort the store operation
                handle.contentType(),
                handle.contentLength()
        );
    }

    protected StreamHandle fetchHash(final String path) {
        return QuarkusTransaction.runner(TransactionSemantics.JOIN_EXISTING).call(() -> {
            final String filePath = path.substring(0, path.lastIndexOf('.'));
            final StoredFile file = StoredFile.find("key = ?1 and repository.id = ?2", filePath, info.id).firstResult();
            if (file == null) return null;

            final String hashType = path.substring(path.lastIndexOf('.') + 1).toLowerCase();
            final String hash = file.hashes.get(hashType);
            if (hash == null) return null;

            final byte[] bytes = hash.getBytes();
            final ByteArrayInputStream stream = new ByteArrayInputStream(bytes);

            return new StreamHandle(stream, ContentType.APPLICATION_OCTET_STREAM.getMimeType(), bytes.length);
        });
    }

    protected StreamHandle getFromMirror(final AuthContext auth, final String path) {
        final StreamHandle result = this.mirrorSupport.getFileFromMirror(auth, path);
        if (result != null && this.mirrorSupport.shouldStore(auth)) {
            try {
                return this.pipeAndStore(auth, result, path);
            } catch (final IOException ex) {
                LOGGER.error("Failed to store file from mirror", ex);
            }
        }
        return result;
    }

    public StreamHandle get(final AuthContext auth, final String path) {
        if (!this.info.canView(auth)) throw new UnauthorizedException();

        final StreamHandle result = this.fileIndexer.isHash(path)
                ? fetchHash(path)
                : this.backend.getValue().get("%s/%s".formatted(info.name, path));

        if (result == null && this.mirrorSupport.enabled()) {
            return getFromMirror(auth, path);
        }

        return result;
    }

    public void put(final AuthContext auth, final String path, final StreamHandle handle) {
        if (!this.info.canWrite(auth)) throw new UnauthorizedException();

        final String namespacedPath = "%s/%s".formatted(info.name, path);
        final S3Backend backend = this.backend.getValue();

        if (!config.allowRedeployments() && backend.head(namespacedPath)) {
            throw new BadRequestException("File already exists");
        }

        if (fileIndexer.indexFile(path, handle)) {
            return; // Hash was uploaded to db instead of s3
        }

        if (path.endsWith(POM_FILE_EXTENSION)) {
            if (handle.contentLength() > MAX_POM_FILE_SIZE)
                throw new BadRequestException("POM file size exceeds maximum allowed size");

            final byte[] bytes = handle.readAllBytes();

            backend.put(
                    namespacedPath,
                    new StreamHandle(
                            new ByteArrayInputStream(bytes),
                            handle.contentType(),
                            handle.contentLength()
                    )
            );

            this.fileIndexer.indexPom(bytes);
        } else {
            backend.put(namespacedPath, handle);
        }
    }

    protected void deleteFilesFromStorage(final AuthContext auth, final List<StoredFile> files) {
        if (!this.info.canWrite(auth)) throw new UnauthorizedException();

        for (int i = 0; i < files.size(); i++) {
            final StoredFile file = files.get(i);

            final String namespacedPath = "%s/%s".formatted(info.name, file.key);
            this.backend.getValue().delete(namespacedPath);
            file.delete();
        }

        files.clear();
    }

    public StreamHandle stringToStreamHandle(final String string, final String contentType) {
        final byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        final InputStream stream = new ByteArrayInputStream(bytes);
        return new StreamHandle(stream, contentType, bytes.length);
    }

    @Override
    public void delete(final AuthContext auth,
                       final ArtifactVersion version) {
        delete(auth, version, true);
    }

    public void delete(final AuthContext auth,
                       final ArtifactVersion version,
                       final boolean updateMavenMetadata) {
        deleteFilesFromStorage(auth, version.files);
        version.delete();

        if (version.artifact.countVersions() <= 0) {
            delete(auth, version.artifact);
        } else if (updateMavenMetadata) {
            final StoredFile metadataFile = fileIndexer.getGAMetadataFile(version.artifact);
            if (metadataFile == null) return;

            final StreamHandle metadataHandle = get(auth, metadataFile.key);
            final String fileContent = new String(metadataHandle.readAllBytes());

            final String result = this.fileIndexer.removeVersionFromMetadata(fileContent, version);

            final StreamHandle newMetadataHandle = stringToStreamHandle(result, metadataHandle.contentType());
            put(AuthContext.ofSystem(auth), metadataFile.key, newMetadataHandle);
        }
    }

    public void delete(final AuthContext auth, final Artifact artifact) {
        deleteFilesFromStorage(auth, artifact.files);

        ArtifactVersion.<ArtifactVersion>find("artifact = ?1", artifact)
                .stream()
                .forEach(version -> delete(AuthContext.ofSystem(), version, false));

        Artifact.deleteById(artifact.id);
    }

    @Override
    public void close() {
        this.backend.close();
        this.mirrorSupport.close();
    }
}
