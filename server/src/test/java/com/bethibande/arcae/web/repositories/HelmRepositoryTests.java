package com.bethibande.arcae.web.repositories;

import com.bethibande.arcae.jpa.repository.PackageManager;
import com.bethibande.arcae.repository.mirror.StandardMirrorConfig;
import com.bethibande.arcae.repository.oci.config.OCIRepositoryConfig;
import com.bethibande.arcae.repository.oci.config.OCIRoutingConfig;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class HelmRepositoryTests extends AbstractRepositoryTests {

    public static final String REPOSITORY_NAME = "helm";

    public static final String METADATA_CONTENT = """
            {
              "apiVersion": "v2",
              "name": "test",
              "version": "1.0.0",
              "description": "An integration test chart",
              "type": "application"
            }
            """;

    public static final String MANIFEST_CONTENT = """
            {
              "schemaVersion": 2,
              "mediaType": "application/vnd.oci.image.manifest.v1+json",
              "config": {
                "mediaType": "application/vnd.cncf.helm.config.v1+json",
                "digest": "sha256:%s",
                "size": 135
              },
              "layers": [
                {
                  "mediaType": "application/vnd.cncf.helm.chart.content.v1.tar+gzip",
                  "digest": "sha256:%s",
                  "size": 4
                }
              ]
            }
            """.formatted(digest(METADATA_CONTENT), digest("test"));

    private static long repositoryId = 0;

    @Test
    @Order(0)
    public void testCreateRepository() {
        repositoryId = createRepository(
                REPOSITORY_NAME,
                PackageManager.HELM,
                new OCIRepositoryConfig(
                        getS3Config(),
                        new OCIRoutingConfig(
                                false,
                                null,
                                0,
                                null,
                                null
                        ),
                        true,
                        new StandardMirrorConfig(
                                List.of(),
                                false,
                                false,
                                false
                        ),
                        "localhost:8080"
                ),
                List.of()
        ).id;
    }

    @Test
    @Order(1)
    public void testUploadBlobs() {
        givenAdminSession()
                .body("test")
                .post("/repositories/helm/" + REPOSITORY_NAME + "/v2/bethibande/test/blobs/uploads?digest=sha256:%s".formatted(digest("test")))
                .then()
                .statusCode(201);

        givenAdminSession()
                .body(METADATA_CONTENT)
                .post("/repositories/helm/" + REPOSITORY_NAME + "/v2/bethibande/test/blobs/uploads?digest=sha256:%s".formatted(digest(METADATA_CONTENT)))
                .then()
                .statusCode(201);
    }

    @Test
    @Order(2)
    public void testUploadManifest() {
        givenAdminSession()
                .body(MANIFEST_CONTENT)
                .put("/repositories/helm/" + REPOSITORY_NAME + "/v2/bethibande/test/manifests/1.0.0")
                .then()
                .statusCode(201);
    }

    // Rest-Assured cannot read YAML and the configuration doesn't seem to make it possible...
    @Test
    @Order(3)
    public void testHelmAPIIndex() {
        givenAdminSession()
                .get("/repositories/helm/" + REPOSITORY_NAME + "/repo/bethibande/test/index.yaml")
                .then()
                .statusCode(200)
                // RestAssured cannot parse YAML, the configuration to make it work doesn't work, so this is disabled for now.
                /*.body("apiVersion", equalTo("v1"))
                .body("generated", notNullValue())
                .body("entries.size()", equalTo(1))
                .body("entries.test.size()", equalTo(1))
                .body("entries.test[0].description", equalTo("An integration test chart"))
                .body("entries.test[0].digest", equalTo("sha256:" + digest("test")))
                .body("entries.test[0].version", equalTo("sha256:" + digest("1.0.0")))
                .body("entries.test[0].urls", Matchers.hasItem("http://localhost:8080/repo/bethibande/test/9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08/test-1.0.0.tgz"))*/;
    }

    @Test
    @Order(3)
    public void testHelmAPIDownload() {
        givenAdminSession()
                .get("repositories/helm/" + REPOSITORY_NAME + "/repo/bethibande/test/9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08/test-1.0.0.tgz")
                .then()
                .statusCode(200)
                .body(equalTo("test"));
    }

}
