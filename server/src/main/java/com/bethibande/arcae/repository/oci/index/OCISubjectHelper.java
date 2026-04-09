package com.bethibande.arcae.repository.oci.index;

import com.bethibande.arcae.jpa.files.OCISubject;
import com.bethibande.arcae.jpa.files.StoredFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.function.BiFunction;

/**
 * A helper class used to extract subject information from OCI manifests.
 * @see OCISubject
 */
public class OCISubjectHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(OCISubjectHelper.class);

    public static OCISubject createSubjectInfo(final String namespace,
                                               final StoredFile source,
                                               final long repositoryId,
                                               final BiFunction<String, String, String> toManifestKey,
                                               final byte[] contents) {
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
                final String subjectKey = toManifestKey.apply(namespace, subject.subjectDigest);
                subject.subject = StoredFile.find("key = ?1 and repository.id = ?2", subjectKey, repositoryId).firstResult();

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

}
