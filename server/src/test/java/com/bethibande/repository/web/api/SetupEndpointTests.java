package com.bethibande.repository.web.api;

import com.bethibande.repository.jpa.security.AccessToken;
import com.bethibande.repository.jpa.security.RefreshToken;
import com.bethibande.repository.jpa.security.UserSession;
import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.jpa.user.UserDTOWithoutId;
import com.bethibande.repository.jpa.user.UserRole;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.specification.RequestSpecification;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@TestHTTPEndpoint(SetupEndpoint.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SetupEndpointTests {

    @BeforeAll
    public static void setup() {
        QuarkusTransaction.requiringNew().run(() -> {
            AccessToken.deleteAll();
            UserSession.deleteAll();
            RefreshToken.deleteAll();
            User.deleteAll();
        });
    }

    protected RequestSpecification basicRequest() {
        return given()
                .body(new UserDTOWithoutId(
                        "admin",
                        "",
                        "password",
                        List.of(UserRole.ADMIN)
                ))
                .header("Content-Type", "application/json");
    }

    @Test
    @Order(1)
    public void shouldNotBeComplete() {
        given()
                .get("/complete")
                .then()
                .statusCode(200)
                .body(is("false"));
    }

    @Test
    @Order(2)
    public void testCreateAdminUser() {
        basicRequest()
                .when()
                .post("/user")
                .then()
                .statusCode(204);
    }

    @Test
    @Order(3)
    public void creationShouldFailForExistingUser() {
        basicRequest()
                .when()
                .post("/user")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(3)
    public void shouldBeComplete() {
        given()
                .get("/complete")
                .then()
                .statusCode(200)
                .body(is("true"));
    }

}
