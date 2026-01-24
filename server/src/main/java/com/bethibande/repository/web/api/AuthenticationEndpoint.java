package com.bethibande.repository.web.api;

import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.jpa.user.UserDTOWithoutPassword;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.jwt.build.Jwt;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.Claims;
import org.wildfly.security.util.PasswordUtil;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Path("/api/v1/auth")
public class AuthenticationEndpoint {

    private static final String DUMMY_BCRYPT_HASH = BcryptUtil.bcryptHash(PasswordUtil.generateSecureRandomString(17));

    public static class Credentials {
        public String username;
        public String password;
    }

    @Inject
    protected SecurityIdentity identity;

    @ConfigProperty(name = "quarkus.profile")
    protected String profile;

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    protected String issuer;

    @ConfigProperty(name = "mp.jwt.token.cookie")
    protected String cookieName;

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

    private Response doLogin(final User user) {
        final Set<String> roles = user.roles.stream()
                .map(Object::toString)
                .collect(Collectors.toSet());

        final Duration duration = Duration.ofHours(24);
        final String token = Jwt.issuer(issuer)
                .upn(user.name)
                .groups(roles)
                .expiresIn(duration)
                .sign();

        return Response.ok(UserDTOWithoutPassword.from(user))
                .cookie(new NewCookie.Builder(cookieName)
                        .value(token)
                        .httpOnly(true)
                        .secure(!isDevMode())
                        .sameSite(NewCookie.SameSite.STRICT)
                        .path("/")
                        .maxAge((int) duration.toSeconds())
                        .build())
                .build();
    }

    @POST
    @PermitAll
    @Path("/logout")
    public Response logout() {
        return Response.ok()
                .cookie(new NewCookie.Builder(cookieName)
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
    public UserDTOWithoutPassword me() {
        if (identity.isAnonymous()) throw new NotFoundException();
        final User user = User.find("name = ?1", identity.getPrincipal().getName()).firstResult();
        return UserDTOWithoutPassword.from(user);
    }

}
