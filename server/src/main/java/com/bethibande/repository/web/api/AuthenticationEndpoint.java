package com.bethibande.repository.web.api;

import com.bethibande.repository.jpa.security.RefreshToken;
import com.bethibande.repository.jpa.security.UserSession;
import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.jpa.user.UserDTOWithoutPassword;
import com.bethibande.repository.jpa.user.UserRole;
import com.bethibande.repository.security.SecurityAttributes;
import com.bethibande.repository.security.UserAuthenticationMechanism;
import com.bethibande.repository.security.UserSessionService;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.security.util.PasswordUtil;

import java.security.spec.InvalidKeySpecException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@ApplicationScoped
@Path("/api/v1/auth")
public class AuthenticationEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationEndpoint.class);

    private static final String DUMMY_BCRYPT_HASH = BcryptUtil.bcryptHash(PasswordUtil.generateSecureRandomString(17));
    private static final String REFRESH_TOKEN_COOKIE_NAME = "RefreshToken";

    @Inject
    protected UserSessionService userSessionService;

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

        boolean passwordMatches = false;
        try {
            passwordMatches = BcryptUtil.matches(credentials.password, hashToCompare);
        } catch (Exception e) {
            if (e.getCause() == null || !(e.getCause() instanceof InvalidKeySpecException)) {
                LOGGER.error("Error comparing password hash", e);
            }
        }

        if (userExists
                && passwordMatches
                && !hashToCompare.isBlank()
                && !user.roles.contains(UserRole.SYSTEM)) {
            return doLogin(user);
        } else {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
    }

    @GET
    @PermitAll
    @Transactional
    @Path("/refresh")
    public Response refresh(final @CookieParam(REFRESH_TOKEN_COOKIE_NAME) String refreshToken) {
        final RefreshToken token = RefreshToken.find("token = ?1", refreshToken).firstResult();
        final Instant now = Instant.now();

        if (token == null || token.isExpired(now)) throw new BadRequestException("Invalid refresh token");

        token.delete();
        return doLogin(token.user);
    }

    public Response doLogin(final User user) {
        final UserSession session = userSessionService.createSessionForUser(user);
        final Duration maxSessionAge = Duration.between(Instant.now(), session.expiresAfter());

        final RefreshToken refreshToken = userSessionService.createRefreshTokenForUser(user);
        final Duration maxRefreshTokenAge = Duration.between(Instant.now(), refreshToken.expiresAfter());

        return Response.ok(UserDTOWithoutPassword.from(user))
                .cookie(
                        new NewCookie.Builder(UserAuthenticationMechanism.COOKIE_NAME)
                                .value(session.token)
                                .httpOnly(true)
                                .secure(!isDevMode())
                                .sameSite(NewCookie.SameSite.STRICT)
                                .path("/")
                                .maxAge((int) maxSessionAge.toSeconds())
                                .build(),
                        new NewCookie.Builder(REFRESH_TOKEN_COOKIE_NAME)
                                .value(refreshToken.token)
                                .httpOnly(true)
                                .secure(!isDevMode())
                                .sameSite(NewCookie.SameSite.STRICT)
                                .path("/api/v1/auth/refresh")
                                .maxAge((int) maxRefreshTokenAge.toSeconds())
                                .build()
                )
                .build();
    }

    @POST
    @PermitAll
    @Path("/logout")
    public Response logout() {
        final UserSession session = identity.getAttribute(SecurityAttributes.SESSION);
        if (session != null) userSessionService.invalidateSession(session.token);

        return Response.ok()
                .cookie(
                        new NewCookie.Builder(UserAuthenticationMechanism.COOKIE_NAME)
                                .httpOnly(true)
                                .secure(!isDevMode())
                                .path("/")
                                .maxAge(0)
                                .value("")
                                .build(),
                        new NewCookie.Builder(REFRESH_TOKEN_COOKIE_NAME)
                                .httpOnly(true)
                                .secure(!isDevMode())
                                .path("/api/v1/auth/refresh")
                                .maxAge(0)
                                .value("")
                                .build()
                )
                .build();
    }

    @GET
    @PermitAll
    @Path("/me")
    public UserDTOWithoutPassword me() {
        if (identity.isAnonymous()) throw new NotFoundException();

        final User user = identity.getPrincipal(User.class);
        return UserDTOWithoutPassword.from(user);
    }

}
