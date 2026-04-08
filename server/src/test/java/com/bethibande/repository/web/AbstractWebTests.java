package com.bethibande.repository.web;

import com.bethibande.repository.jpa.security.UserSession;
import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.jpa.user.UserRole;
import com.bethibande.repository.repository.S3Config;
import com.bethibande.repository.security.UserAuthenticationMechanism;
import com.bethibande.repository.security.UserSessionService;
import com.bethibande.repository.test.MinioResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@QuarkusTest
@QuarkusTestResource(MinioResource.class)
public abstract class AbstractWebTests {

    protected static final Map<String, UserSession> sessions = new HashMap<>();

    public static S3Config getS3Config() {
        return MinioResource.getConfig();
    }

    @BeforeAll
    static void init() {
        QuarkusTransaction.requiringNew().run(() -> {
            if (User.count("name = ?1", "admin") > 0) return;

            final User user = new User();
            user.name = "admin";
            user.email = "";
            user.password = BcryptUtil.bcryptHash("12345678");
            user.roles = List.of(UserRole.ADMIN);
            user.persist();
        });

        sessions.clear();
    }

    @Inject
    protected UserSessionService userSessionService;

    @Inject
    protected ObjectMapper objectMapper;

    public String toJson(final Object object) {
        return this.objectMapper.valueToTree(object).toString();
    }

    public <T> T fromJson(final String json, final Class<T> clazz) {
        return this.objectMapper.convertValue(json, clazz);
    }

    public RequestSpecification givenSession(final String username) {
        if (sessions.get(username) == null) {
            QuarkusTransaction.requiringNew().run(() -> {
                final User admin = User.find("name = ?1", username).firstResult();
                sessions.put(username, userSessionService.createSessionForUser(admin, "127.0.0.1"));
            });
        }

        return RestAssured.given()
                .cookie(UserAuthenticationMechanism.COOKIE_NAME, sessions.get(username).token);
    }

    public RequestSpecification givenSessionWithJsonBody(final String username, final Object body) {
        return givenSession(username)
                .header("Content-Type", "application/json")
                .body(body);
    }

    public RequestSpecification givenAdminSession() {
        return givenSession("admin");
    }

    public RequestSpecification givenAdminSessionWithJsonBody(final Object body) {
        return givenSessionWithJsonBody("admin", body);
    }

}
