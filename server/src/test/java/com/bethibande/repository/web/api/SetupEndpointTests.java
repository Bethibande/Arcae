package com.bethibande.repository.web.api;

import com.bethibande.repository.jpa.security.RefreshToken;
import com.bethibande.repository.jpa.security.UserSession;
import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.jpa.user.UserDTOWithoutId;
import com.bethibande.repository.jpa.user.UserRole;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;

import java.util.List;

import static io.restassured.RestAssured.given;

@QuarkusTest
@TestHTTPEndpoint(SetupEndpoint.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SetupEndpointTests {

    @BeforeAll
    public static void setup() {
        QuarkusTransaction.requiringNew().run(() -> {
            UserSession.deleteAll();
            RefreshToken.deleteAll();
            User.deleteAll();
        });
    }

    protected RequestSpecification basicRequest() {
        return given()
                .body(new UserDTOWithoutId(
                        "admin",
                        "test@example.org",
                        "password",
                        List.of(UserRole.ADMIN)
                ))
                .header("Content-Type", "application/json");
    }

    @Test
    @Order(1)
    public void testCreateAdminUser() {
        basicRequest()
                .when()
                .post("/user")
                .then()
                .statusCode(204);
    }

    @Test
    @Order(2)
    public void creationShouldFailForExistingUser() {
        basicRequest()
                .when()
                .post("/user")
                .then()
                .statusCode(400);
    }

}
