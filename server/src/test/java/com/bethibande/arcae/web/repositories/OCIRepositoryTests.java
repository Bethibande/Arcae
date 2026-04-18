package com.bethibande.arcae.web.repositories;

import com.bethibande.arcae.jpa.repository.PackageManager;
import com.bethibande.arcae.repository.mirror.StandardMirrorConfig;
import com.bethibande.arcae.repository.oci.config.OCIRepositoryConfig;
import com.bethibande.arcae.repository.oci.config.OCIRoutingConfig;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OCIRepositoryTests extends AbstractRepositoryTests {

    public static final String REPOSITORY_NAME = "oci";

    public static final String TEST_MANIFEST_CONTENT = """
            {
               "schemaVersion": 2,
               "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
               "config": {
                  "mediaType": "application/vnd.docker.container.image.v1+json",
                  "size": 8,
                  "digest": "sha256:%1$s"
               },
               "layers": [
                  {
                     "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
                     "size": 8,
                     "digest": "sha256:%1$s"
                  }
               ]
            }
            """.formatted(digest("test"));

    private static long repositoryId;
    private static String manifestDigest;
    private static int artifactId;
    private static int testVersionId;
    private static int indexVersionId;

    @Test
    @Order(0)
    public void testCreateRepository() {
        repositoryId = createRepository(
                REPOSITORY_NAME,
                PackageManager.OCI,
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
    @Order(2)
    public void testHeadUnknownBlobFails() {
        givenAdminSession()
                .head("/repositories/oci/" + REPOSITORY_NAME + "/v2/bethibande/arcae/blobs/sha256:unknown")
                .then()
                .statusCode(404);
    }

    @Test
    @Order(2)
    public void testHeadUnknownManifestFails() {
        givenAdminSession()
                .head("/repositories/oci/" + REPOSITORY_NAME + "/v2/bethibande/arcae/manifests/unknown")
                .then()
                .statusCode(404);
    }

    @Test
    @Order(3)
    public void testUploadBlob() {
        final String digest = "sha256:" + digest("test");

        givenAdminSession()
                .body("test")
                .post("/repositories/oci/" + REPOSITORY_NAME + "/v2/bethibande/arcae/blobs/uploads?digest=" + digest)
                .then()
                .statusCode(201)
                .header("Docker-Content-Digest", digest);
    }

    @Test
    @Order(3)
    public void testUploadBlobWithDigestAndComponent() {
        final String uploadDigest = "abc+sha256:" + digest("test");
        final String responseDigest = "sha256:" + digest("test");

        givenAdminSession()
                .body("test")
                .post("/repositories/oci/" + REPOSITORY_NAME + "/v2/bethibande/arcae/blobs/uploads?digest=" + uploadDigest)
                .then()
                .statusCode(201)
                .header("Docker-Content-Digest", responseDigest);
    }

    @Test
    @Order(3)
    public void testUploadBlobWithSession() {
        final String value = "test-2";
        final String digest = "sha256:" + digest(value);

        final String location = givenAdminSession()
                .post("/repositories/oci/" + REPOSITORY_NAME + "/v2/bethibande/arcae/blobs/uploads")
                .then()
                .statusCode(202)
                .extract()
                .header("Location");

        final int length = value.getBytes().length;

        final String location2 = givenAdminSession()
                .body(value)
                .header("Content-Range", "0-" + length)
                .patch(location.replace("/v2", "/repositories/oci/" + REPOSITORY_NAME + "/v2"))
                .then()
                .statusCode(202)
                .header("Range", "0-5")
                .extract()
                .header("Location");

        givenAdminSession()
                .get(location2.replace("/v2", "/repositories/oci/" + REPOSITORY_NAME + "/v2"))
                .then()
                .statusCode(204)
                .header("Range", "0-5")
                .header("Location", endsWith("&part=2"));

        givenAdminSession()
                .put(location2.replace("/v2", "/repositories/oci/" + REPOSITORY_NAME + "/v2") + "&digest=" + digest)
                .then()
                .statusCode(201)
                .header("Docker-Content-Digest", digest);
    }

    @Test
    @Order(4)
    public void testHeadBlob() {
        final String digest = "sha256:" + digest("test");

        givenAdminSession()
                .head("/repositories/oci/" + REPOSITORY_NAME + "/v2/bethibande/arcae/blobs/" + digest)
                .then()
                .statusCode(200)
                .header("Docker-Content-Digest", digest);
    }

    @Test
    @Order(5)
    public void testDeleteBlob() {
        final String digest = "sha256:" + digest("test-2");

        givenAdminSession()
                .delete("/repositories/oci/" + REPOSITORY_NAME + "/v2/bethibande/arcae/blobs/" + digest)
                .then()
                .statusCode(202);
    }

    @Test
    @Order(6)
    public void testBlobIsDeleted() {
        final String digest = "sha256:" + digest("test-2");

        givenAdminSession()
                .head("/repositories/oci/" + REPOSITORY_NAME + "/v2/bethibande/arcae/blobs/" + digest)
                .then()
                .statusCode(404);
    }

    @Test
    @Order(7)
    public void testPushManifest() {
        final String digest = "sha256:" + digest(TEST_MANIFEST_CONTENT);

        manifestDigest = givenAdminSession()
                .body(TEST_MANIFEST_CONTENT)
                .contentType("application/vnd.docker.distribution.manifest.v2+json")
                .put("/repositories/oci/" + REPOSITORY_NAME + "/v2/bethibande/arcae/manifests/test")
                .then()
                .statusCode(201)
                .header("Docker-Content-Digest", digest)
                .extract()
                .header("Docker-Content-Digest");
    }

    @Test
    @Order(8)
    public void testTestTagIsPresent() {
        artifactId = givenAdminSession()
                .get("/api/v1/artifact?r=" + repositoryId + "&p=0&s=1&o=LAST_UPDATED")
                .then()
                .statusCode(200)
                .body("page", equalTo(0))
                .body("total", equalTo(1))
                .body("pages", equalTo(1))
                .body("data.size()", equalTo(1))
                .body("data[0].artifactId", equalTo("arcae"))
                .body("data[0].groupId", equalTo("bethibande"))
                .body("data[0].latestVersion", equalTo("test"))
                .extract()
                .path("data[0].id");
    }

    @Test
    @Order(9)
    public void testPushIndexManifest() {
        final String content = """
                {
                  "schemaVersion": 2,
                  "mediaType": "application/vnd.oci.image.index.v1+json",
                  "manifests": [
                    {
                      "mediaType": "application/vnd.oci.image.manifest.v1+json",
                      "digest": "%s",
                      "size": 528,
                      "platform": {
                        "architecture": "amd64",
                        "os": "linux"
                      }
                    }
                  ]
                }
                """.formatted(manifestDigest);

        final String digest = "sha256:" + digest(content);

        givenAdminSession()
                .body(content)
                .contentType("application/vnd.oci.image.index.v1+json")
                .put("/repositories/oci/" + REPOSITORY_NAME + "/v2/bethibande/arcae/manifests/index")
                .then()
                .statusCode(201)
                .header("Docker-Content-Digest", equalTo(digest));
    }

    @Test
    @Order(10)
    public void testHeadManifest() {
        givenAdminSession()
                .head("/repositories/oci/" + REPOSITORY_NAME + "/v2/bethibande/arcae/manifests/test")
                .then()
                .statusCode(200)
                .header("Docker-Content-Digest", equalTo(manifestDigest));
    }

    @Test
    @Order(10)
    public void testIndexTagIsPresent() {
        final ExtractableResponse<Response> response = givenAdminSession()
                .get("/api/v1/artifact/" + artifactId + "/versions/search?p=0&s=2&o=LAST_UPDATED")
                .then()
                .statusCode(200)
                .body("page", equalTo(0))
                .body("total", equalTo(2))
                .body("pages", equalTo(1))
                .body("data.size()", equalTo(2))
                .body("data[0].version", equalTo("index"))
                .body("data[1].version", equalTo("test"))
                .extract();

        indexVersionId = response.path("data[0].id");
        testVersionId = response.path("data[1].id");
    }

    @Test
    @Order(10)
    public void testListTags() {
        givenAdminSession()
                .get("/repositories/oci/" + REPOSITORY_NAME + "/v2/bethibande/arcae/tags/list")
                .then()
                .statusCode(200)
                .body("name", equalTo("bethibande/arcae"))
                .body("tags.size()", equalTo(2))
                .body("tags[0]", equalTo("index"))
                .body("tags[1]", equalTo("test"));
    }

    @Test
    @Order(11)
    public void testGetBlob() {
        final String digest = "sha256:" + digest("test");
        givenAdminSession()
                .get("/repositories/oci/" + REPOSITORY_NAME + "/v2/bethibande/arcae/blobs/" + digest)
                .then()
                .statusCode(200)
                .header("Content-Type", equalTo("application/octet-stream"))
                .header("Docker-Content-Digest", equalTo(digest));
    }

    @Test
    @Order(11)
    public void testGetManifest() {
        givenAdminSession()
                .get("/repositories/oci/" + REPOSITORY_NAME + "/v2/bethibande/arcae/manifests/test")
                .then()
                .statusCode(200)
                .body(equalTo(TEST_MANIFEST_CONTENT))
                .header("Docker-Content-Digest", equalTo(manifestDigest));
    }

    @Test
    @Order(12)
    public void testDeleteManifest() {
        givenAdminSession()
                .delete("/repositories/oci/" + REPOSITORY_NAME + "/v2/bethibande/arcae/manifests/test")
                .then()
                .statusCode(202);
    }

    @Test
    @Order(13)
    public void testManifestIsDeleted() {
        givenAdminSession()
                .head("/repositories/oci/" + REPOSITORY_NAME + "/v2/bethibande/arcae/manifests/test")
                .then()
                .statusCode(404);
    }

}
