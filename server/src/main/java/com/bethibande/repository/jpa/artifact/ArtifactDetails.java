package com.bethibande.repository.jpa.artifact;

import java.util.List;

public record ArtifactDetails(
        String description,
        String url,
        List<Author> authors,
        List<License> licenses,
        Object additionalData
) {

    public record Author(String name, String email) {
    }

    public record License(String name, String url) {
    }

}
