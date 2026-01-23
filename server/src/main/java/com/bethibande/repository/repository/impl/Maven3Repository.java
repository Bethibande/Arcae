package com.bethibande.repository.repository.impl;

import com.bethibande.repository.jpa.Artifact;
import com.bethibande.repository.jpa.ArtifactVersion;
import com.bethibande.repository.jpa.Repository;
import com.bethibande.repository.repository.ArtifactDescriptor;
import com.bethibande.repository.repository.IRepository;
import com.bethibande.repository.repository.backend.IRepositoryBackend;
import com.bethibande.repository.repository.backend.S3Backend;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import jakarta.ws.rs.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public class Maven3Repository implements IRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(Maven3Repository.class);

    public static final String POM_FILE_EXTENSION = ".pom";
    public static final long MAX_POM_FILE_SIZE_BYTES = 1_000_000L;

    protected final Repository info;
    protected final Maven3RepositoryConfig config;
    protected final IRepositoryBackend backend;

    public Maven3Repository(final Repository info, final ObjectMapper objectMapper) throws JsonProcessingException {
        this.info = info;
        this.config = objectMapper.readValue(info.settings, Maven3RepositoryConfig.class);
        this.backend = switch (info.backend.type) {
            case S3 -> new S3Backend(info.backend, objectMapper);
            case PROXY -> throw new UnsupportedOperationException("Not yet implemented");
        };
    }

    protected byte[] readAll(final InputStream stream, final int length) {
        final byte[] data = new byte[(int) length];
        int read = 0;
        try {
            while (read < length) {
                read += stream.read(data, read, data.length - read);
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        return data;
    }

    public CompletableFuture<Void> uploadFile(final String path,
                                              final InputStream stream,
                                              final long contentLength,
                                              final String contentType) {
        final String[] pathSegments = path.split("/");
        final String fileName = pathSegments[pathSegments.length - 1];

        if (fileName.endsWith(POM_FILE_EXTENSION)) {
            if (contentLength > MAX_POM_FILE_SIZE_BYTES) {
                throw new BadRequestException("POM is too large, max allowed is " + MAX_POM_FILE_SIZE_BYTES);
            }
            final byte[] data = readAll(stream, (int) contentLength);

            try {
                processPom(new String(data, StandardCharsets.UTF_8));
            } catch (final JsonProcessingException ex) {
                LOGGER.error("Failed to parse .pom file", ex);
            }

            return backend.put(new ArtifactDescriptor(
                    path,
                    contentLength,
                    contentType,
                    new ByteArrayInputStream(data)
            ));
        }

        return backend.put(new ArtifactDescriptor(
                path,
                contentLength,
                contentType,
                stream
        ));
    }

    protected void processPom(final String content) throws JsonProcessingException {
        final XmlMapper mapper = new XmlMapper();
        final JsonNode root = mapper.readTree(content);

        final String groupId = root.findValue("groupId").asText();
        final String artifactId = root.findValue("artifactId").asText();
        final String version = root.findValue("version").asText();

        Artifact artifact = Artifact.find("groupId = ?1 AND artifactId = ?2", groupId, artifactId).firstResult();
        if (artifact == null) {
            artifact = new Artifact();
            artifact.repository = info;
            artifact.groupId = groupId;
            artifact.artifactId = artifactId;

            artifact.persist();
        }

        ArtifactVersion artifactVersion = ArtifactVersion.find("artifact = ?1 AND version = ?2", artifact, version).firstResult();
        final Instant now = Instant.now();
        if (artifactVersion == null) {
            artifactVersion = new ArtifactVersion();
            artifactVersion.artifact = artifact;
            artifactVersion.version = version;
            artifactVersion.created = now;
            artifactVersion.updated = now;
            artifactVersion.persist();

            LOGGER.info("Created {}:{}:{}", groupId, artifactId, version);
        } else {
            artifactVersion.updated = now;
            artifactVersion.persist();

            LOGGER.info("Re-deployed {}:{}:{}", groupId, artifactId, version);
        }
    }

    @Override
    public Repository getRepositoryInfo() {
        return this.info;
    }

    @Override
    public IRepositoryBackend getBackend() {
        return this.backend;
    }
}
