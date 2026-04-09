package com.bethibande.arcae.repository.oci.details;

import com.bethibande.arcae.jpa.artifact.ArtifactDetails;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;

/**
 * A helper class used to extract additional metadata from OCI manifests, such as referenced blobs and manifests,
 * but also other metadata like descriptions, authors, and so on.
 */
public class OCIDetailsHelper {

    protected static List<OCIManifestReference> collectionManifests(final JsonNode root) {
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

    protected static List<OCILayerReference> collectLayers(final JsonNode root) {
        if (!root.has("layers")) return Collections.emptyList();

        final List<OCILayerReference> layers = new ArrayList<>();
        root.get("layers")
                .elements()
                .forEachRemaining(layerNode -> layers.add(new OCILayerReference(layerNode.get("digest").textValue())));

        return layers;
    }

    protected static Map<String, String> collectAnnotations(final JsonNode root) {
        if (!root.has("annotations")) return Collections.emptyMap();

        final Map<String, String> annotations = new HashMap<>();
        if (root.has("annotations")) {
            root.get("annotations")
                    .properties()
                    .forEach(entry -> annotations.put(entry.getKey(), entry.getValue().textValue()));
        }

        return annotations;
    }

    protected static String tryGetConfigDigest(final JsonNode root) {
        if (!root.has("config")) return null;
        return root.get("config").get("digest").textValue();
    }

    public static ArtifactDetails parseDetails(final byte[] manifest) throws IOException {
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

}
