package com.bethibande.repository.repository.maven;

import com.bethibande.repository.jpa.StoredFile;
import com.bethibande.repository.jpa.artifact.Artifact;
import com.bethibande.repository.jpa.artifact.ArtifactDetails;
import com.bethibande.repository.jpa.artifact.ArtifactVersion;
import com.bethibande.repository.jpa.repository.Repository;
import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.repository.ManagedRepository;
import com.bethibande.repository.repository.StreamHandle;
import com.bethibande.repository.repository.backend.RepositoryBackend;
import com.bethibande.repository.repository.backend.S3Backend;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
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
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenRepository implements ManagedRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenRepository.class);

    public static final String POM_FILE_EXTENSION = ".pom";
    public static final long MAX_POM_FILE_SIZE = 5_000_000L;

    public static final Set<String> HASH_FILE_EXTENSIONS = Set.of("sha1", "sha256", "sha512", "md5");
    public static final long MAX_HASH_FILE_SIZE = 128L;

    public static final Pattern GAV_PATH_PATTERN = Pattern.compile("^(?<groupId>.+)/(?<artifactId>[^/]+)/(?<version>[^/]+)/(?<filename>[^/]+)$");
    public static final Pattern GA_PATH_PATTERN = Pattern.compile("^(?<groupId>.+)/(?<artifactId>[^/]+)/(?<filename>[^/]+)$");

    public static final String METADATA_FILE_NAME = "maven-metadata.xml";

    private final Repository info;
    private final MavenRepositoryConfig config;

    private final RepositoryBackend backend;

    public MavenRepository(final Repository info, final ObjectMapper mapper) throws JsonProcessingException {
        this(info, mapper.readValue(info.settings, MavenRepositoryConfig.class));
    }

    public MavenRepository(final Repository info, final MavenRepositoryConfig config) {
        this.info = info;
        this.config = config;
        this.backend = new S3Backend(config.s3Config());
    }

    private static byte[] readAllBytes(final StreamHandle handle) {
        try (final InputStream stream = handle.stream()) {
            final byte[] data = new byte[(int) handle.contentLength()];
            int read = 0;
            while (read < data.length) {
                final int bytesRead = stream.read(data, read, data.length - read);
                if (bytesRead == -1) break;
                read += bytesRead;
            }
            return data;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read stream bytes", e);
        }
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

    protected boolean isHash(final String path) {
        final String extension = path.substring(path.lastIndexOf('.') + 1);
        return HASH_FILE_EXTENSIONS.contains(extension);
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

        final StreamHandle result = isHash(path)
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

        if (indexFile(path, handle)) {
            return; // Hash was uploaded to db instead of s3
        }

        if (path.endsWith(POM_FILE_EXTENSION)) {
            if (handle.contentLength() > MAX_POM_FILE_SIZE)
                throw new BadRequestException("POM file size exceeds maximum allowed size");

            final byte[] bytes = readAllBytes(handle);

            this.backend.put(
                    namespacedPath,
                    new StreamHandle(
                            new ByteArrayInputStream(bytes),
                            handle.contentType(),
                            handle.contentLength()
                    )
            );

            indexPom(bytes);
        } else {
            this.backend.put(namespacedPath, handle);
        }
    }

    /**
     * Indexes a file by determining its type (hash file or regular file) and appropriately
     * updating or creating entries in the database.
     * <p>
     * If the file is a hash file (determined by its extension), it updates the stored hashes
     * for the corresponding source file. If it is a regular file, it ensures that a record
     * exists in the database for the file, creating a new entry if none exists, and updates
     * the record's timestamps.
     *
     * @param path   The file path, used to determine the file type and for identifying
     *               or creating a corresponding database record.
     * @param handle A {@link StreamHandle} representing the file's content and metadata,
     *               used when processing the file content.
     * @return {@code true} if this method consumed the given {@code StreamHandle}.
     */
    protected boolean indexFile(final String path, final StreamHandle handle) {
        if (isHash(path)) {
            return updateHash(path, handle);
        } else {
            final StoredFile file = StoredFile.find("key = ?1 and repository.id = ?2", path, info.id).firstResult();
            final Instant now = Instant.now();
            if (file == null) {
                final StoredFile newFile = new StoredFile();
                newFile.key = path;
                newFile.repository = info;
                newFile.created = now;
                newFile.updated = now;
                newFile.persist();

                tryLinkFile(path, newFile);
            } else {
                file.updated = now;
            }
        }
        return false;
    }

    protected boolean tryLinkToArtifact(final String path, final StoredFile file) {
        final Matcher matcher = GA_PATH_PATTERN.matcher(path);
        if (!matcher.matches()) return false;

        final String groupId = matcher.group("groupId").replaceAll("/", ".");
        final String artifactId = matcher.group("artifactId");

        final Artifact artifact = Artifact.find("groupId = ?1 and artifactId = ?2 and repository.id = ?3", groupId, artifactId, info.id).firstResult();
        if (artifact == null) return false;

        if (artifact.files == null) artifact.files = new ArrayList<>();
        artifact.files.add(file);
        artifact.lastUpdated = Instant.now();

        return true;
    }

    protected void tryLinkToVersion(final String path, final StoredFile file) {
        final Matcher matcher = GAV_PATH_PATTERN.matcher(path);
        if (!matcher.matches()) return;

        final String groupId = matcher.group("groupId").replaceAll("/", ".");
        final String artifactId = matcher.group("artifactId");
        final String version = matcher.group("version");

        if (version.equalsIgnoreCase(METADATA_FILE_NAME)) {
            // Shouldn't be possible but you never know...
            throw new InternalServerErrorException("How'd we get here?");
        }

        final Instant now = Instant.now();

        Artifact artifact = Artifact.find("groupId = ?1 and artifactId = ?2 and repository.id = ?3", groupId, artifactId, info.id).firstResult();
        if (artifact == null) {
            artifact = new Artifact();
            artifact.groupId = groupId;
            artifact.artifactId = artifactId;
            artifact.repository = info;
            artifact.files = Collections.emptyList();
            artifact.lastUpdated = now;
            artifact.persist();
        } else {
            artifact.lastUpdated = now;
        }

        ArtifactVersion versionEntity = ArtifactVersion.find("artifact = ?1 and version = ?2", artifact, version).firstResult();
        if (versionEntity == null) {
            versionEntity = new ArtifactVersion();
            versionEntity.artifact = artifact;
            versionEntity.version = version;
            versionEntity.created = now;
            versionEntity.updated = now;
            versionEntity.files = new ArrayList<>();
            versionEntity.files.add(file);

            versionEntity.persist();
        } else {
            versionEntity.updated = now;

            if (versionEntity.files == null) versionEntity.files = new ArrayList<>();
            versionEntity.files.add(file);
        }
    }

    protected void tryLinkFile(final String path, final StoredFile file) {
        if (path.endsWith(METADATA_FILE_NAME) && tryLinkToArtifact(path, file)) return;
        tryLinkToVersion(path, file);
    }

    protected boolean updateHash(final String path, final StreamHandle handle) {
        final String sourcePath = path.substring(0, path.lastIndexOf('.'));
        final String hashType = path.substring(path.lastIndexOf('.') + 1).toLowerCase();

        final StoredFile file = StoredFile.find("key = ?1 and repository.id = ?2", sourcePath, info.id).firstResult();
        if (file == null) return false;

        if (handle.contentLength() > MAX_HASH_FILE_SIZE)
            throw new BadRequestException("Hash file size exceeds maximum allowed size");
        final String hash = new String(readAllBytes(handle));

        if (file.hashes == null) file.hashes = new HashMap<>();
        file.hashes.put(hashType, hash);
        return true;
    }

    public ArtifactDetails getDetails(final JsonNode node) {
        final JsonNode urlNode = node.get("url");
        final String url = urlNode != null ? urlNode.asText() : null;

        final JsonNode descriptionNode = node.get("description");
        final String description = descriptionNode != null ? descriptionNode.asText() : null;

        final List<ArtifactDetails.Author> authors = new ArrayList<>();
        if (node.has("developers")) {
            node.get("developers").elements().forEachRemaining(dev -> {
                final JsonNode name = dev.get("name");
                final JsonNode email = dev.get("email");
                authors.add(new ArtifactDetails.Author(name != null ? name.asText() : null, email != null ? email.asText() : null));
            });
        }
        final List<ArtifactDetails.License> licenses = new ArrayList<>();
        if (node.has("licenses")) {
            node.get("licenses").elements().forEachRemaining(license -> {
                final JsonNode name = license.get("name");
                final JsonNode licenseUrl = license.get("url");

                licenses.add(new ArtifactDetails.License(name != null ? name.asText() : null, licenseUrl != null ? licenseUrl.asText() : null));
            });
        }

        return new ArtifactDetails(description, url, authors, licenses);
    }

    /**
     * Indexes a POM (Project Object Model) by extracting artifact and version information
     * from the provided XML byte array, and updating or creating corresponding entries
     * in the database.
     *
     * @param bytes A byte array representing the POM file in XML format. The POM contains
     *              information about the project, such as its groupId, artifactId, and version.
     *              This method parses the data, manages the persistence of its related entities
     *              (Artifact and ArtifactVersion), and updates timestamps where applicable.
     */
    private void indexPom(final byte[] bytes) {
        try {
            final JsonNode node = new XmlMapper().readTree(bytes);

            final String groupId = node.get("groupId").asText();
            final String artifactId = node.get("artifactId").asText();
            final String version = node.get("version").asText();

            final Instant now = Instant.now();
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

            ArtifactVersion versionEntity = ArtifactVersion.find("artifact = ?1 and version = ?2", artifact, version).firstResult();
            if (versionEntity == null) {
                versionEntity = new ArtifactVersion();
                versionEntity.artifact = artifact;
                versionEntity.version = version;
                versionEntity.created = now;
                versionEntity.updated = now;
                versionEntity.details = getDetails(node);

                versionEntity.persist();
            } else {
                versionEntity.updated = now;
                versionEntity.details = getDetails(node);
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
