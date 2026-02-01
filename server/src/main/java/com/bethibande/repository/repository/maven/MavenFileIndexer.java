package com.bethibande.repository.repository.maven;

import com.bethibande.repository.jpa.StoredFile;
import com.bethibande.repository.jpa.artifact.Artifact;
import com.bethibande.repository.jpa.artifact.ArtifactDetails;
import com.bethibande.repository.jpa.artifact.ArtifactVersion;
import com.bethibande.repository.jpa.repository.Repository;
import com.bethibande.repository.repository.StreamHandle;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenFileIndexer {

    public static final Set<String> HASH_FILE_EXTENSIONS = Set.of("sha1", "sha256", "sha512", "md5");
    public static final long MAX_HASH_FILE_SIZE = 128L;

    public static final Pattern GAV_PATH_PATTERN = Pattern.compile("^(?<groupId>.+)/(?<artifactId>[^/]+)/(?<version>[^/]+)/(?<filename>[^/]+)$");
    public static final Pattern GA_PATH_PATTERN = Pattern.compile("^(?<groupId>.+)/(?<artifactId>[^/]+)/(?<filename>[^/]+)$");

    public static final String METADATA_FILE_NAME = "maven-metadata.xml";

    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final Repository info;

    public MavenFileIndexer(final Repository info) {
        this.info = info;
    }

    public StoredFile getGAMetadataFile(final Artifact artifact) {
        final List<StoredFile> files = artifact.files;
        for (int i = 0; i < files.size(); i++) {
            final StoredFile file = files.get(i);
            if (file.key.endsWith(METADATA_FILE_NAME)) return file;
        }
        return null;
    }

    public ArtifactVersion getLatestVersion(final Artifact artifact) {
        return ArtifactVersion.<ArtifactVersion>find("artifact = ?1 order by updated desc", artifact).firstResult();
    }

    public String removeVersionFromMetadata(final String fileContent, final ArtifactVersion version) {
        final XmlMapper mapper = new XmlMapper();
        mapper.configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true);
        try {
            final JsonNode root = mapper.readTree(fileContent);
            final ObjectNode versioningNode = (ObjectNode) root.get("versioning");
            final ArrayNode versionsNode = (ArrayNode) versioningNode.get("versions").get("version");
            versionsNode.removeIf(node -> node.asText().equalsIgnoreCase(version.version));

            final ArtifactVersion latestVersion = getLatestVersion(version.artifact);
            versioningNode.set("latest", new TextNode(latestVersion.version));

            final Instant now = Instant.now();
            final String dateText = DATE_TIME_FORMATTER.format(now.atZone(ZoneOffset.UTC));
            versioningNode.set("lastUpdated", new TextNode(dateText));

            return mapper.writerWithDefaultPrettyPrinter()
                    .withRootName("metadata")
                    .writeValueAsString(root);
        } catch (final JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }

    public boolean isHash(final String path) {
        final String extension = path.substring(path.lastIndexOf('.') + 1);
        return HASH_FILE_EXTENSIONS.contains(extension);
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
            // Shouldn't be possible, but you never know...
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
        final String hash = new String(handle.readAllBytes());

        if (file.hashes == null) file.hashes = new HashMap<>();
        file.hashes.put(hashType, hash);
        return true;
    }

    public ArtifactDetails getDetails(final JsonNode node) {
        // TODO: Refactor this
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
    public void indexPom(final byte[] bytes) {
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
