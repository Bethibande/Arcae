package com.bethibande.repository.repository.maven;

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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class MavenRepository implements ManagedRepository {

    public static final String POM_FILE_EXTENSION = ".pom";
    public static final long MAX_POM_FILE_SIZE = 5_000_000L;

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

    public StreamHandle get(final User user, final String path) {
        if (!this.info.canView(user)) throw new UnauthorizedException();

        return this.backend.get("%s/%s".formatted(info.name, path));
    }

    public void put(final User user, final String path, final StreamHandle handle) {
        if(!this.info.canWrite(user)) throw new UnauthorizedException();

        final String namespacedPath = "%s/%s".formatted(info.name, path);

        if (!config.allowRedeployments() && this.backend.head(namespacedPath)) {
            throw new BadRequestException("File already exists");
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
