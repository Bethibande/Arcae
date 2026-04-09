package com.bethibande.arcae.web.api;

import com.bethibande.arcae.jpa.security.AccessTokenDTOWithoutId;
import com.bethibande.arcae.jpa.security.AccessTokenDTOWithoutToken;
import com.bethibande.arcae.web.AbstractWebTests;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestHTTPEndpoint(AccessTokenEndpoint.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AccessTokenEndpointTests extends AbstractWebTests {

    private static long id;
    private static String token;

    @Test
    @Order(1)
    public void testCreateAccessToken() {
        final ExtractableResponse<Response> response = givenAdminSession()
                .body(new AccessTokenDTOWithoutId(
                        "test",
                        Instant.now().plus(Duration.ofMinutes(5))
                ))
                .header("Content-Type", "application/json")
                .post()
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .extract();

        id = response.body().jsonPath().getLong("id");
        token = response.body().jsonPath().getString("token");
    }

    @Test
    @Order(2)
    public void testUpdateAccessToken() {
        givenAdminSession()
                .body(new AccessTokenDTOWithoutToken(
                        "test",
                        Instant.now().plus(Duration.ofMinutes(5)),
                        id
                ))
                .header("Content-Type", "application/json")
                .put()
                .then()
                .statusCode(200)
                .body("token", nullValue())
                .body("name", notNullValue());
    }

    @Test
    @Order(2)
    public void updateTokenShouldFailForNonExistingToken() {
        givenAdminSession()
                .body(new AccessTokenDTOWithoutToken(
                        "test",
                        Instant.now().plus(Duration.ofMinutes(5)),
                        999999999999999999L
                ))
                .header("Content-Type", "application/json")
                .put()
                .then()
                .statusCode(404);
    }

    @Test
    @Order(3)
    public void testListAccessTokens() {
        givenAdminSession()
                .get()
                .then()
                .statusCode(200)
                .body("size()", is(1));
    }

    @Test
    @Order(3)
    public void testListAccessTokensUsingBearerToken() {
        given()
                .header("Authorization", "Bearer " + token)
                .get()
                .then()
                .statusCode(200)
                .body("size()", is(1));
    }

    @Test
    @Order(3)
    public void testListAccessTokensUsingBasicToken() {
        final String credentials = Base64.getEncoder().encodeToString(("admin:" + token).getBytes());
        given()
                .header("Authorization", "Basic " + credentials)
                .get()
                .then()
                .statusCode(200)
                .body("size()", is(1));
    }

    @Test
    @Order(4)
    public void testDeleteAccessToken() {
        givenAdminSession()
                .delete("/" + id)
                .then()
                .statusCode(204);
    }

}
