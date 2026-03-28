package com.bethibande.repository.web.api;

import com.bethibande.repository.jobs.BuiltinJobScheduler;
import com.bethibande.repository.jobs.impl.PasswordResetTask;
import com.bethibande.repository.jpa.security.RefreshToken;
import com.bethibande.repository.jpa.security.UserSession;
import com.bethibande.repository.jpa.user.PasswordResetToken;
import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.jpa.user.UserDTOWithoutPassword;
import com.bethibande.repository.jpa.user.UserRole;
import com.bethibande.repository.security.SecurityAttributes;
import com.bethibande.repository.security.UserAuthenticationMechanism;
import com.bethibande.repository.security.UserSessionService;
import io.quarkiverse.bucket4j.runtime.RateLimited;
import io.quarkiverse.bucket4j.runtime.resolver.IpResolver;
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
import java.util.concurrent.CompletionStage;

@ApplicationScoped
@Path("/api/v1/auth")
public class AuthenticationEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationEndpoint.class);

    private static final String DUMMY_BCRYPT_HASH = BcryptUtil.bcryptHash(PasswordUtil.generateSecureRandomString(17));
    public static final String REFRESH_TOKEN_COOKIE_NAME = "RefreshToken";

    @Inject
    protected UserSessionService userSessionService;

    @Inject
    protected BuiltinJobScheduler builtinJobScheduler;

    @Inject
    protected PasswordResetTask passwordResetTask;

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
    @Path("/reset-request")
    @RateLimited(bucket = "password-reset", identityResolver = IpResolver.class)
    public CompletionStage<Response> resetPassword(final @QueryParam("email") String email) {
        return this.builtinJobScheduler.runOnce(
                        this.passwordResetTask,
                        new PasswordResetTask.Config(email)
                ).thenApply((_) -> Response.ok().build())
                .exceptionally((ex) -> {
                    LOGGER.error("Failed to schedule password reset task for email: {}", email, ex);
                    return Response.serverError().build();
                });
    }

    public record PasswordResetCredentials(
            String email,
            String token,
            String newPassword
    ) {
    }

    @POST
    @PermitAll
    @Transactional
    @Path("/reset")
    @RateLimited(bucket = "password-reset", identityResolver = IpResolver.class)
    public Response resetPassword(final PasswordResetCredentials credentials) {
        final PasswordResetToken token = PasswordResetToken.find("token = ?1", credentials.token).firstResult();
        if (token == null || token.isExpired(Instant.now()))
            return Response.status(Response.Status.BAD_REQUEST).build();
        if (!token.user.email.equalsIgnoreCase(credentials.email))
            return Response.status(Response.Status.BAD_REQUEST).build();

        final User user = token.user;
        user.password = BcryptUtil.bcryptHash(credentials.newPassword);

        PasswordResetToken.delete("user = ?1", user);

        return Response.ok().build();
    }

    @POST
    @PermitAll
    @Path("/login")
    @RateLimited(bucket = "auth", identityResolver = IpResolver.class)
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
    @RateLimited(bucket = "auth", identityResolver = IpResolver.class)
    public Response refresh(final @CookieParam(REFRESH_TOKEN_COOKIE_NAME) String refreshToken) {
        final RefreshToken token = RefreshToken.find("token = ?1", refreshToken).firstResult();
        final Instant now = Instant.now();

        if (token == null || token.isExpired(now)) throw new BadRequestException("Invalid refresh token");
        token.delete();

        final UserSession session = identity.getAttribute(SecurityAttributes.SESSION);
        if (session != null) userSessionService.invalidateSession(session.token); // Invalidate current session

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
        if (!(identity.getPrincipal() instanceof User)) throw new BadRequestException("You are not a user");

        final User user = identity.getPrincipal(User.class);
        return UserDTOWithoutPassword.from(user);
    }

}
