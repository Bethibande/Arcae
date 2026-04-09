package com.bethibande.arcae.web.api;

import com.bethibande.arcae.jpa.repository.PackageManager;
import com.bethibande.arcae.jpa.repository.RepositoryDTO;
import com.bethibande.arcae.jpa.repository.RepositoryDTOWithoutId;
import com.bethibande.arcae.repository.cleanup.CleanupPolicies;
import com.bethibande.arcae.repository.maven.MavenRepositoryConfig;
import com.bethibande.arcae.repository.mirror.StandardMirrorConfig;
import com.bethibande.arcae.web.AbstractWebTests;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestHTTPEndpoint(RepositoryEndpoint.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RepositoryEndpointTests extends AbstractWebTests {

    private static long id;

    @Test
    @Order(1)
    public void testCreate() {
        id  = givenAdminSessionWithJsonBody(new RepositoryDTOWithoutId(
                "maven-test",
                PackageManager.MAVEN,
                toJson(new MavenRepositoryConfig(
                        false,
                        getS3Config(),
                        new StandardMirrorConfig(
                                List.of(),
                                false,
                                false,
                                false
                        )
                )),
                null,
                CleanupPolicies.standard()
        ))
                .post()
                .then()
                .statusCode(200)
                .body("id", notNullValue())
                .extract()
                .body()
                .jsonPath()
                .getLong("id");
    }

    @Test
    @Order(2)
    public void testUpdate() {
        givenAdminSessionWithJsonBody(new RepositoryDTO(
                id,
                "maven-test-2",
                PackageManager.MAVEN,
                toJson(new MavenRepositoryConfig(
                        false,
                        getS3Config(),
                        new StandardMirrorConfig(
                                List.of(),
                                false,
                                false,
                                false
                        )
                )),
                null,
                CleanupPolicies.standard()
        ))
                .put()
                .then()
                .statusCode(200)
                .body("id", Matchers.equalTo((int) id))
                .body("name", Matchers.equalTo("maven-test-2"));
    }

    @Test
    @Order(2)
    public void testUpdateUnknownShouldYield404() {
        givenAdminSessionWithJsonBody(new RepositoryDTO(
                238548234534L,
                null,
                null,
                null,
                null,
                null
        ))
                .put()
                .then()
                .statusCode(404);
    }

    @Test
    @Order(3)
    public void testGet() {
        givenAdminSession()
                .get("/" + id)
                .then()
                .statusCode(200)
                .body("id", Matchers.equalTo((int) id))
                .body("name", Matchers.equalTo("maven-test-2"));
    }

    @Test
    @Order(3)
    public void testAnonymousGetShouldFail() {
        given()
                .get("/" + id)
                .then()
                .statusCode(401);
    }

    @Test
    @Order(3)
    public void testGetOverview() {
        given()
                .get("/overview/" + id)
                .then()
                .statusCode(200)
                .body("artifactsCount", equalTo(0))
                .body("lastUpdated", nullValue())
                .body("repository.name", equalTo("maven-test-2"));
    }

    @Test
    @Order(3)
    public void testListOverview() {
        given()
                .get("/overview?o=LAST_UPDATED")
                .then()
                .statusCode(200)
                .body("[0].artifactsCount", equalTo(0))
                .body("[0].lastUpdated", nullValue())
                .body("[0].repository.name", equalTo("maven-test-2"));
    }

    @Test
    @Order(4)
    public void testCanWriteAnonymous() {
        given()
                .get("/" + id + "/can-write")
                .then()
                .statusCode(200)
                .body(equalTo("false"));
    }

    @Test
    @Order(4)
    public void testCanWriteAdmin() {
        givenAdminSession()
                .get("/" + id + "/can-write")
                .then()
                .statusCode(200)
                .body(equalTo("true"));
    }

    @Test
    @Order(5)
    public void testDelete() {
        givenAdminSession()
                .delete("/" + id)
                .then()
                .statusCode(204);
    }

}
