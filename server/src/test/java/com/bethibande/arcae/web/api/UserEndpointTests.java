package com.bethibande.arcae.web.api;

import com.bethibande.arcae.jpa.user.User;
import com.bethibande.arcae.jpa.user.UserRole;
import com.bethibande.arcae.jpa.user.*;
import com.bethibande.arcae.web.AbstractWebTests;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestHTTPEndpoint(UserEndpoint.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserEndpointTests extends AbstractWebTests {

    public static final String USERNAME = "test";
    public static final String PASSWORD = "test";

    @BeforeAll
    public static void beforeAll() {
        QuarkusTransaction.requiringNew().run(() -> {
            final SearchSession searchSession = Search.session(User.getEntityManager());
            final List<User> users = User.listAll();

            // Clear index to ensure we don't end up with unexpected search results
            for (int i = 0; i < users.size(); i++) {
                searchSession.indexingPlan().delete(users.get(i));
            }
        });
    }

    private static long id;

    @Test
    @Order(1)
    public void testCreateUser() {
        id = givenAdminSessionWithJsonBody(new UserDTOWithoutId(
                USERNAME,
                "abc@bethibande.com",
                PASSWORD,
                List.of(UserRole.DEFAULT)
        ))
                .post()
                .then()
                .statusCode(200)
                .body("id", notNullValue())
                .body("name", equalTo(USERNAME))
                .body("password", nullValue())
                .body("roles", hasItem(UserRole.DEFAULT.name()))
                .extract()
                .jsonPath()
                .getLong("id");
    }

    @Test
    @Order(2)
    public void testUpdateUser() {
        givenAdminSessionWithJsonBody(new UserDTOWithoutPassword(
                USERNAME,
                "repository-test@bethibande.com",
                List.of(UserRole.DEFAULT),
                id
        ))
                .put()
                .then()
                .statusCode(200)
                .body("id", equalTo((int) id))
                .body("name", equalTo(USERNAME))
                .body("password", nullValue())
                .body("email", equalTo("repository-test@bethibande.com"))
                .body("roles", hasItem(UserRole.DEFAULT.name()));
    }

    @Test
    @Order(2)
    public void failUpdateOnUnknownUser() {
        givenAdminSessionWithJsonBody(new UserDTOWithoutPassword(
                "test",
                "",
                List.of(UserRole.DEFAULT),
                348573856734345L
        ))
                .put()
                .then()
                .statusCode(404);
    }

    @Test
    @Order(3)
    public void testGetUserById() {
        givenAdminSession()
                .get("/" + id)
                .then()
                .statusCode(200)
                .body("id", equalTo((int) id))
                .body("name", equalTo(USERNAME))
                .body("email", equalTo("repository-test@bethibande.com"))
                .body("password", nullValue())
                .body("roles", hasItem(UserRole.DEFAULT.name()));
    }

    @Test
    @Order(4)
    public void testSearchByUsername() {
        givenAdminSession()
                .get("/search?q=test&p=0")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("data[0].name", equalTo(USERNAME));
    }

    @Test
    @Order(4)
    public void testSearchByEmail() {
        givenAdminSession()
                .get("/search?q=repository-test&p=0")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("data[0].name", equalTo(USERNAME));
    }

    @Test
    @Order(4)
    public void testList() {
        givenAdminSession()
                .get("/?p=0")
                .then()
                .statusCode(200)
                .body("total", equalTo(2));
    }

    @Test
    @Order(5)
    public void testUpdateSelf() {
        givenSessionWithJsonBody(USERNAME, new UserDTOWithoutIdAndRoles(
                USERNAME,
                "repository-test2@bethibande.com",
                PASSWORD
        ))
                .put("/self")
                .then()
                .statusCode(204);
    }

    @Test
    @Order(5)
    public void testUpdateSelfFailOnWrongPassword() {
        givenSessionWithJsonBody(USERNAME, new UserDTOWithoutIdAndRoles(
                USERNAME,
                "",
                PASSWORD + "1"
        ))
                .put("/self")
                .then()
                .statusCode(403);
    }

    @Test
    @Order(6)
    public void testUpdatePassword() {
        givenSessionWithJsonBody(USERNAME, new UserEndpoint.PasswordResetForm(
                PASSWORD,
                "123"
        ))
                .put("/self/password")
                .then()
                .statusCode(204);
    }

    @Test
    @Order(7)
    public void testUpdatePasswordFailOnWrongPassword() {
        givenSessionWithJsonBody(USERNAME, new UserEndpoint.PasswordResetForm(
                PASSWORD + "1",
                "123"
        ))
                .put("/self/password")
                .then()
                .statusCode(403);
    }

    @Test
    @Order(8)
    public void testDeleteUser() {
        givenAdminSession()
                .delete("/" + id)
                .then()
                .statusCode(204);
    }

}
