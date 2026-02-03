package com.bethibande.repository.repository.maven;

import com.bethibande.repository.jpa.StoredFile;
import com.bethibande.repository.jpa.artifact.Artifact;
import com.bethibande.repository.jpa.artifact.ArtifactVersion;
import com.bethibande.repository.jpa.repository.Repository;
import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.repository.ManagedRepository;
import com.bethibande.repository.repository.StreamHandle;
import com.bethibande.repository.repository.backend.RepositoryBackend;
import com.bethibande.repository.repository.backend.S3Backend;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class MavenRepository implements ManagedRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenRepository.class);

    public static final String POM_FILE_EXTENSION = ".pom";
    public static final long MAX_POM_FILE_SIZE = 5_000_000L;

    private final Repository info;
    private final MavenRepositoryConfig config;
    private final MavenFileIndexer fileIndexer;

    private final RepositoryBackend backend;

    public MavenRepository(final Repository info, final ObjectMapper mapper) throws JsonProcessingException {
        this(info, mapper.readValue(info.settings, MavenRepositoryConfig.class));
    }

    public MavenRepository(final Repository info, final MavenRepositoryConfig config) {
        this.info = info;
        this.config = config;
        this.backend = new S3Backend(config.s3Config());
        this.fileIndexer = new MavenFileIndexer(info, this);
    }

    protected StreamHandle mirrorGet(final User user, final String path, final MirrorConnectionSettings mirror) {
        try (final HttpClient client = HttpClient.newHttpClient()) {
            final String remoteUrl = "%s/%s".formatted(mirror.url(), path).replaceAll("//", "/");

            final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(remoteUrl))
                    .GET();

            switch (mirror.authType()) {
                case BASIC -> {
                    final String value = "Basic " + Base64.getEncoder().encodeToString("%s:%s".formatted(mirror.username(), mirror.password()).getBytes());
                    builder.header(HttpHeaders.AUTHORIZATION, value);
                }
                case BEARER -> builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + mirror.password());
            }

            final HttpRequest request = builder.build();

            final HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 404) return null;

            final InputStream stream = response.body();
            final long contentLength = response.headers()
                    .firstValueAsLong(HttpHeaders.CONTENT_LENGTH)
                    .orElse(-1L);
            final String contentType = response.headers()
                    .firstValue(HttpHeaders.CONTENT_TYPE)
                    .orElse(null);

            final StreamHandle handle = new StreamHandle(stream, contentType, contentLength);

            if (this.config.mirrorConfig().storeArtifacts()) {
                put(user, path, handle, true);
                return get(user, path);
            }

            return handle;
        } catch (final IOException | InterruptedException e) {
            LOGGER.error("Failed to fetch remote artifact for path {} on repository {}", path, info.name, e);
            return null;
        }
    }

    protected StreamHandle mirrorGet(final User user, final String path) {
        final MavenMirrorConfig mirrors = this.config.mirrorConfig();
        for (int i = 0; i < mirrors.connections().size(); i++) {
            final MirrorConnectionSettings mirror = mirrors.connections().get(i);
            final StreamHandle result = mirrorGet(user, path, mirror);
            if (result != null) return result;
        }

        throw new NotFoundException("Artifact not found");
    }

    protected StreamHandle fetchHash(final String path) {
        final String filePath = path.substring(0, path.lastIndexOf('.'));
        final StoredFile file = StoredFile.find("key = ?1 and repository.id = ?2", filePath, info.id).firstResult();
        if (file == null) return null;

        final String hashType = path.substring(path.lastIndexOf('.') + 1).toLowerCase();
        final String hash = file.hashes.get(hashType);
        if (hash == null) return null;

        final byte[] bytes = hash.getBytes();
        final ByteArrayInputStream stream = new ByteArrayInputStream(bytes);

        return new StreamHandle(stream, ContentType.APPLICATION_OCTET_STREAM.getMimeType(), bytes.length);
    }

    public StreamHandle get(final User user, final String path) {
        if (!this.info.canView(user)) throw new UnauthorizedException();

        final StreamHandle result = this.fileIndexer.isHash(path)
                ? fetchHash(path)
                : this.backend.get("%s/%s".formatted(info.name, path));

        if (result == null
                && this.config.mirrorConfig() != null
                && this.config.mirrorConfig().enabled()) {
            return mirrorGet(user, path);
        }

        return result;
    }

    public void put(final User user, final String path, final StreamHandle handle, final boolean skipAuth) {
        if (!this.info.canWrite(user) && !skipAuth) throw new UnauthorizedException();

        final String namespacedPath = "%s/%s".formatted(info.name, path);

        if (!config.allowRedeployments() && this.backend.head(namespacedPath)) {
            throw new BadRequestException("File already exists");
        }

        if (fileIndexer.indexFile(path, handle)) {
            return; // Hash was uploaded to db instead of s3
        }

        if (path.endsWith(POM_FILE_EXTENSION)) {
            if (handle.contentLength() > MAX_POM_FILE_SIZE)
                throw new BadRequestException("POM file size exceeds maximum allowed size");

            final byte[] bytes = handle.readAllBytes();

            this.backend.put(
                    namespacedPath,
                    new StreamHandle(
                            new ByteArrayInputStream(bytes),
                            handle.contentType(),
                            handle.contentLength()
                    )
            );

            this.fileIndexer.indexPom(bytes);
        } else {
            this.backend.put(namespacedPath, handle);
        }
    }

    protected void delete0(final User user, final List<StoredFile> files, final boolean skipAuth) {
        if (!skipAuth && !this.info.canWrite(user)) throw new UnauthorizedException();

        for (int i = 0; i < files.size(); i++) {
            final StoredFile file = files.get(i);

            final String namespacedPath = "%s/%s".formatted(info.name, file.key);
            this.backend.delete(namespacedPath);
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
    public void delete(final User user,
                       final ArtifactVersion version,
                       final boolean skipAuth) {
        delete(user, version, true, skipAuth);
    }

    public void delete(final User user,
                       final ArtifactVersion version,
                       final boolean updateMavenMetadata,
                       final boolean skipAuth) {
        delete0(user, version.files, skipAuth);
        version.delete();

        if (version.artifact.countVersions() <= 0) {
            delete(user, version.artifact, skipAuth);
        } else if (updateMavenMetadata) {
            final StoredFile metadataFile = fileIndexer.getGAMetadataFile(version.artifact);
            if (metadataFile == null)
                throw new IllegalStateException("Metadata file not found for artifact: " + version.artifact);

            final StreamHandle metadataHandle = get(user, metadataFile.key);
            final String fileContent = new String(metadataHandle.readAllBytes());

            final String result = this.fileIndexer.removeVersionFromMetadata(fileContent, version);

            final StreamHandle newMetadataHandle = stringToStreamHandle(result, metadataHandle.contentType());
            put(user, metadataFile.key, newMetadataHandle, true);
        }
    }

    public void delete(final User user, final Artifact artifact, final boolean skipAuth) {
        delete0(user, artifact.files, skipAuth);

        ArtifactVersion.<ArtifactVersion>find("artifact = ?1", artifact)
                .stream()
                .forEach(version -> delete(user, version, false, true));

        Artifact.deleteById(artifact.id);
    }

}
