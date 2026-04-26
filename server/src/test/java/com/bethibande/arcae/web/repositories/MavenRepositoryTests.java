package com.bethibande.arcae.web.repositories;

import com.bethibande.arcae.jpa.repository.PackageManager;
import com.bethibande.arcae.repository.maven.MavenRepositoryConfig;
import com.bethibande.arcae.repository.mirror.StandardMirrorConfig;
import io.quarkus.test.junit.QuarkusTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MavenRepositoryTests extends AbstractRepositoryTests {

    public static final String REPOSITORY_NAME = "maven";

    protected static long repositoryId = 0;
    protected static int artifactId = 0;
    protected static int versionId = 0;

    @Test
    @Order(0)
    public void testCreateRepository() {
        repositoryId = createRepository(
                REPOSITORY_NAME,
                PackageManager.MAVEN,
                new MavenRepositoryConfig(
                        false,
                        new StandardMirrorConfig(
                                List.of(),
                                false,
                                false,
                                false
                        )
                ),
                List.of()
        ).id;
    }

    @Test
    @Order(1)
    public void testUploadFile() {
        givenAdminSession()
                .body(streamOf("test"))
                .put("/repositories/maven/" + REPOSITORY_NAME + "/com/bethibande/test/1.0.0/test-1.0.0.jar")
                .then()
                .statusCode(204);
    }

    @Test
    @Order(2)
    public void testBlockRedeployment() {
        givenAdminSession()
                .body(streamOf("test-2"))
                .put("/repositories/maven/" + REPOSITORY_NAME + "/com/bethibande/test/1.0.0/test-1.0.0.jar")
                .then()
                .statusCode(409);
    }

    @Test
    @Order(2)
    public void testUploadDigest() {
        // Digests receive special treatment, they are stored in a database column instead of being stored in S3
        givenAdminSession()
                .body(digest("test"))
                .put("/repositories/maven/" + REPOSITORY_NAME + "/com/bethibande/test/1.0.0/test-1.0.0.jar.sha256")
                .then()
                .statusCode(204);
    }

    @Test
    @Order(2)
    public void testUploadPomA() {
        givenAdminSession()
                .body("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                          <!-- This module was also published with a richer model, Gradle metadata,  -->
                          <!-- which should be used instead. Do not delete the following line which  -->
                          <!-- is to indicate to Gradle or any Gradle module metadata file consumer  -->
                          <!-- that they should prefer consuming it instead. -->
                          <!-- do_not_remove: published-with-gradle-metadata -->
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.bethibande</groupId>
                          <artifactId>test</artifactId>
                          <version>1.0.0</version>
                          <name>test</name>
                          <url>https://github.com/Bethibande/Arcae</url>
                          <licenses>
                            <license>
                              <name>FSL Apache 2</name>
                              <url>https://raw.githubusercontent.com/Bethibande/arcae/refs/heads/master/LICENSE</url>
                            </license>
                          </licenses>
                          <developers>
                            <developer>
                              <id>bethibande</id>
                              <name>Max Bethmann</name>
                              <email>contact@bethibande.com</email>
                            </developer>
                          </developers>
                          <scm>
                            <connection>scm:git:git://github.com/Bethibande/arcae.git</connection>
                            <developerConnection>scm:git:ssh://github.com/Bethibande/arcae.git</developerConnection>
                            <url>https://github.com/Bethibande/arcae</url>
                          </scm>
                        </project>
                        
                        """)
                .header("Content-Type", "application/xml")
                .put("/repositories/maven/" + REPOSITORY_NAME + "/com/bethibande/test/1.0.0/test-1.0.0.pom")
                .then()
                .statusCode(204);
    }

    @Test
    @Order(2)
    public void testUploadPomB() {
        givenAdminSession()
                .body("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                          <!-- This module was also published with a richer model, Gradle metadata,  -->
                          <!-- which should be used instead. Do not delete the following line which  -->
                          <!-- is to indicate to Gradle or any Gradle module metadata file consumer  -->
                          <!-- that they should prefer consuming it instead. -->
                          <!-- do_not_remove: published-with-gradle-metadata -->
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.bethibande</groupId>
                          <artifactId>test</artifactId>
                          <version>1.0.1</version>
                          <name>test</name>
                          <url>https://github.com/Bethibande/Arcae</url>
                          <licenses>
                            <license>
                              <name>FSL Apache 2</name>
                              <url>https://raw.githubusercontent.com/Bethibande/arcae/refs/heads/master/LICENSE</url>
                            </license>
                          </licenses>
                          <developers>
                            <developer>
                              <id>bethibande</id>
                              <name>Max Bethmann</name>
                              <email>contact@bethibande.com</email>
                            </developer>
                          </developers>
                          <scm>
                            <connection>scm:git:git://github.com/Bethibande/arcae.git</connection>
                            <developerConnection>scm:git:ssh://github.com/Bethibande/arcae.git</developerConnection>
                            <url>https://github.com/Bethibande/arcae</url>
                          </scm>
                        </project>
                        
                        """)
                .header("Content-Type", "application/xml")
                .put("/repositories/maven/" + REPOSITORY_NAME + "/com/bethibande/test/1.0.1/test-1.0.1.pom")
                .then()
                .statusCode(204);
    }

    @Test
    @Order(3)
    public void testUploadGAMetadata() {
        givenAdminSession()
                .body("""
                        <?xml version="1.0" encoding="UTF-8"?>
                         <metadata>
                          <groupId>de.bethibande</groupId>
                          <artifactId>test</artifactId>
                          <versioning>
                            <latest>1.0.1</latest>
                            <versions>
                              <version>1.0.1</version>
                              <version>1.0.0</version>
                            </versions>
                            <lastUpdated>20260409184603</lastUpdated>
                          </versioning>
                        </metadata>""")
                .header("Content-Type", "application/xml")
                .put("/repositories/maven/" + REPOSITORY_NAME + "/com/bethibande/test/maven-metadata.xml")
                .then()
                .statusCode(204);
    }

    @Test
    @Order(4)
    public void testListArtifacts() {
        artifactId = givenAdminSession()
                .get("/api/v1/artifact?r=" + repositoryId + "&p=0&s=1&o=LAST_UPDATED")
                .then()
                .statusCode(200)
                .body("page", equalTo(0))
                .body("pages", equalTo(1))
                .body("total", equalTo(1))
                .body("data.size()", equalTo(1))
                .body("data[0].artifactId", Matchers.equalTo("test"))
                .body("data[0].groupId", Matchers.equalTo("com.bethibande"))
                .extract()
                .path("data[0].id");
    }

    @Test
    @Order(5)
    public void testListArtifactVersions() {
        versionId = givenAdminSession()
                .get("/api/v1/artifact/" + artifactId + "/versions/search?p=0&s=2&o=LAST_UPDATED")
                .then()
                .statusCode(200)
                .body("page", equalTo(0))
                .body("pages", equalTo(1))
                .body("total", equalTo(2))
                .body("data.size()", equalTo(2))
                .body("data[0].version", equalTo("1.0.1"))
                .body("data[0].details.url", equalTo("https://github.com/Bethibande/Arcae"))
                .body("data[0].details.authors.size()", equalTo(1))
                .body("data[0].details.authors[0].name", equalTo("Max Bethmann"))
                .body("data[0].details.authors[0].email", equalTo("contact@bethibande.com"))
                .body("data[0].details.licenses.size()", equalTo(1))
                .body("data[0].details.licenses[0].name", equalTo("FSL Apache 2"))
                .body("data[0].details.licenses[0].url", equalTo("https://raw.githubusercontent.com/Bethibande/arcae/refs/heads/master/LICENSE"))
                .body("data[1].version", equalTo("1.0.0"))
                .extract()
                .path("data[0].id");
    }

    @Test
    @Order(6)
    public void testDownloadArtifact() {
        givenAdminSession()
                .get("/repositories/maven/" + REPOSITORY_NAME + "/com/bethibande/test/1.0.0/test-1.0.0.jar")
                .then()
                .statusCode(200)
                .body(equalTo("test"));
    }

    @Test
    @Order(6)
    public void testDownloadArtifactDigest() {
        givenAdminSession()
                .get("/repositories/maven/" + REPOSITORY_NAME + "/com/bethibande/test/1.0.0/test-1.0.0.jar.sha256")
                .then()
                .statusCode(200)
                .body(equalTo(digest("test")));
    }

    @Test
    @Order(6)
    public void testPomIsPresent() {
        givenAdminSession()
                .get("/repositories/maven/" + REPOSITORY_NAME + "/com/bethibande/test/1.0.1/test-1.0.1.pom")
                .then()
                .statusCode(200);
    }

    @Test
    @Order(7)
    public void testDeleteVersion() {
        givenAdminSession()
                .delete("/api/v1/artifact/version/" + versionId)
                .then()
                .statusCode(204);
    }

    @Test
    @Order(8)
    public void testGAMetadataShouldBeUpdated() {
        givenAdminSession()
                .get("/repositories/maven/" + REPOSITORY_NAME + "/com/bethibande/test/maven-metadata.xml")
                .then()
                .statusCode(200)
                .body("metadata.versioning.versions.size()", equalTo(1))
                .body("metadata.versioning.latest", equalTo("1.0.0"));
    }

    @Test
    @Order(8)
    public void testFilesAreDeleted() {
        givenAdminSession()
                .get("/repositories/maven/" + REPOSITORY_NAME + "/com/bethibande/test/1.0.1/test-1.0.1.pom")
                .then()
                .statusCode(404);
    }

    @Test
    @Order(8)
    public void testVersionIsDeletedFromIndex() {
        givenAdminSession()
                .get("/api/v1/artifact/version/" + versionId)
                .then()
                .statusCode(404);
    }

    @Test
    @Order(9)
    public void testDeleteArtifact() {
        givenAdminSession()
                .delete("/api/v1/artifact/" + artifactId)
                .then()
                .statusCode(204);
    }

    @Test
    @Order(10)
    public void testArtifactIsDeletedFromIndex() {
        givenAdminSession()
                .get("/api/v1/artifact/" + artifactId)
                .then()
                .statusCode(404);
    }

    @Test
    @Order(10)
    public void testGAMetadataIsDeleted() {
        givenAdminSession()
                .get("/repositories/maven/" + REPOSITORY_NAME + "/com/bethibande/test/maven-metadata.xml")
                .then()
                .statusCode(404);
    }

}
