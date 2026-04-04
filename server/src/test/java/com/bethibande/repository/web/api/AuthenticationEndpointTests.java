package com.bethibande.repository.web.api;

import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.jpa.user.UserRole;
import com.bethibande.repository.security.UserAuthenticationMechanism;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;

import java.util.List;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static io.restassured.matcher.RestAssuredMatchers.detailedCookie;
import static org.hamcrest.Matchers.not;

@QuarkusTest
@TestHTTPEndpoint(AuthenticationEndpoint.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuthenticationEndpointTests {

    @BeforeAll
    public static void setup() {
        QuarkusTransaction.requiringNew().run(() -> {
            final User user = new User();
            user.name = "admin";
            user.email = "";
            user.password = BcryptUtil.bcryptHash("password");
            user.roles = List.of(UserRole.ADMIN);
            user.persist();
        });
    }

    private static String sessionToken;
    private static String refreshToken;

    @Test
    @Order(1)
    public void testLogin() {
        final ExtractableResponse<Response> result = given()
                .body("{\"username\": \"admin\", \"password\": \"password\"}")
                .header("Content-Type", "application/json")
                .when()
                .post("/login")
                .then()
                .cookie(UserAuthenticationMechanism.COOKIE_NAME, detailedCookie()
                        .httpOnly(true)
                        .secured(true)
                        .sameSite("Strict"))
                .cookie(AuthenticationEndpoint.REFRESH_TOKEN_COOKIE_NAME, detailedCookie()
                        .httpOnly(true)
                        .secured(true)
                        .sameSite("Strict")
                        .path("/api/v1/auth/refresh"))
                .statusCode(200)
                .extract();

        sessionToken = result.cookie(UserAuthenticationMechanism.COOKIE_NAME);
        refreshToken = result.cookie(AuthenticationEndpoint.REFRESH_TOKEN_COOKIE_NAME);
    }

    @Test
    @Order(2)
    public void testRefresh() {
        final ExtractableResponse<Response> result = given()
                .cookie(UserAuthenticationMechanism.COOKIE_NAME, this.sessionToken)
                .cookie(AuthenticationEndpoint.REFRESH_TOKEN_COOKIE_NAME, this.refreshToken)
                .when()
                .get("/refresh")
                .then()
                .cookie(UserAuthenticationMechanism.COOKIE_NAME, detailedCookie()
                        .httpOnly(true)
                        .secured(true)
                        .sameSite("Strict")
                        .value(not(this.sessionToken)))
                .cookie(AuthenticationEndpoint.REFRESH_TOKEN_COOKIE_NAME, detailedCookie()
                        .httpOnly(true)
                        .secured(true)
                        .sameSite("Strict")
                        .path("/api/v1/auth/refresh")
                        .value(not(this.refreshToken)))
                .statusCode(200)
                .extract();

        sessionToken = result.cookie(UserAuthenticationMechanism.COOKIE_NAME);
        refreshToken = result.cookie(AuthenticationEndpoint.REFRESH_TOKEN_COOKIE_NAME);
    }

    @Test
    @Order(3)
    public void testMeShouldReturnAdmin() {
        given()
                .cookie(UserAuthenticationMechanism.COOKIE_NAME, this.sessionToken)
                .when()
                .get("/me")
                .then()
                .body("name", Matchers.equalTo("admin"))
                .statusCode(200);
    }

    @Test
    @Order(4)
    public void testLogout() {
        given()
                .cookie(UserAuthenticationMechanism.COOKIE_NAME, this.sessionToken)
                .when()
                .post("/logout")
                .then()
                .cookie(UserAuthenticationMechanism.COOKIE_NAME, detailedCookie()
                        .value("")
                        .maxAge(0))
                .cookie(AuthenticationEndpoint.REFRESH_TOKEN_COOKIE_NAME, detailedCookie()
                        .value("")
                        .maxAge(0))
                .statusCode(200);
    }


    @Test
    @Order(5)
    public void testMeShouldReturn404() {
        when()
                .get("/me")
                .then()
                .statusCode(404);
    }

}
