package com.bethibande.repository.web.api;

import com.bethibande.repository.jpa.security.UserSession;
import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.jpa.user.UserDTOWithoutPassword;
import com.bethibande.repository.security.SecurityAttributes;
import com.bethibande.repository.security.UserAuthenticationMechanism;
import com.bethibande.repository.security.UserSessionService;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.wildfly.security.util.PasswordUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@ApplicationScoped
@Path("/api/v1/auth")
public class AuthenticationEndpoint {

    private static final String DUMMY_BCRYPT_HASH = BcryptUtil.bcryptHash(PasswordUtil.generateSecureRandomString(17));
    @Inject
    UserSessionService userSessionService;

    public static class Credentials {
        public String username;
        public String password;
    }

    @Inject
    protected SecurityIdentity identity;

    @ConfigProperty(name = "quarkus.profile")
    protected String profile;

    protected boolean isDevMode() {
        return Objects.equals(profile, "dev");
    }

    @POST
    @PermitAll
    @Path("/login")
    public Response login(final Credentials credentials) {
        final User user = User.find("name = ?1", credentials.username).firstResult();

        String hashToCompare;
        boolean userExists;

        if (user != null) {
            hashToCompare = user.password;
            userExists = true;
        } else {
            hashToCompare = DUMMY_BCRYPT_HASH;
            userExists = false;
        }

        boolean passwordMatches = BcryptUtil.matches(credentials.password, hashToCompare);

        if (userExists && passwordMatches) {
            return doLogin(user);
        } else {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
    }

    public Response doLogin(final User user) {
        final UserSession session = userSessionService.createSessionForUser(user);
        final Duration maxAge = Duration.between(Instant.now(), session.expiresAfter());

        return Response.ok(UserDTOWithoutPassword.from(user))
                .cookie(new NewCookie.Builder(UserAuthenticationMechanism.COOKIE_NAME)
                        .value(session.token)
                        .httpOnly(true)
                        .secure(!isDevMode())
                        .sameSite(NewCookie.SameSite.STRICT)
                        .path("/")
                        .maxAge((int) maxAge.toSeconds())
                        .build())
                .build();
    }

    @POST
    @PermitAll
    @Path("/logout")
    public Response logout() {
        final UserSession session = identity.getAttribute(SecurityAttributes.SESSION);
        if (session != null) userSessionService.invalidateSession(session.token);

        return Response.ok()
                .cookie(new NewCookie.Builder(UserAuthenticationMechanism.COOKIE_NAME)
                        .httpOnly(true)
                        .secure(!isDevMode())
                        .path("/")
                        .maxAge(0)
                        .value("")
                        .build())
                .build();
    }

    @GET
    @PermitAll
    @Path("/me")
    public UserDTOWithoutPassword me(final @HeaderParam("User-Agent") String userAgent) {
        if (identity.isAnonymous()) throw new NotFoundException();

        final User user = identity.getPrincipal(User.class);
        return UserDTOWithoutPassword.from(user);
    }

}
