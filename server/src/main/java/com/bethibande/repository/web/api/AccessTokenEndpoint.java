package com.bethibande.repository.web.api;

import com.bethibande.repository.jpa.user.*;
import com.bethibande.repository.web.AuthenticatedUser;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import org.wildfly.security.util.PasswordUtil;

import java.util.List;
import java.util.Objects;

@Path("/api/v1/tokens")
public class AccessTokenEndpoint {

    private final AuthenticatedUser authenticatedUser;

    @Inject
    public AccessTokenEndpoint(final AuthenticatedUser authenticatedUser) {
        this.authenticatedUser = authenticatedUser;
    }

    @POST
    @Authenticated
    @Transactional
    public AccessTokenDTO create(final AccessTokenDTOWithoutId dto) {
        final User self = authenticatedUser.getSelf();
        if (self == null) throw new ForbiddenException("User must be authenticated to create access tokens");

        final AccessToken token = new AccessToken();
        token.name = dto.name();
        token.expiresAfter = dto.expiresAfter();
        token.owner = self;
        token.token = PasswordUtil.generateSecureRandomString(128);
        token.persist();

        return AccessTokenDTO.from(token);
    }

    @PUT
    @Transactional
    public AccessTokenDTOWithoutToken update(final AccessTokenDTOWithoutToken dto) {
        final AccessToken token = AccessToken.findById(dto.id());
        token.name = dto.name();
        token.expiresAfter = dto.expiresAfter();

        return AccessTokenDTOWithoutToken.from(token);
    }

    @GET
    @Authenticated
    @Transactional
    public List<AccessTokenDTOWithoutToken> list() {
        final User self = authenticatedUser.getSelf();
        return AccessToken.<AccessToken>list("owner = ?1", Sort.ascending("name"), self)
                .stream()
                .map(AccessTokenDTOWithoutToken::from)
                .toList();
    }

    @DELETE
    @Authenticated
    @Transactional
    @Path("/{id}")
    public void delete(@PathParam("id") final long id) {
        final User self = authenticatedUser.getSelf();
        final AccessToken token = AccessToken.findById(id);

        if (token == null || !Objects.equals(token.owner.id, self.id)) throw new NotFoundException("Unknown token");
        token.delete();
    }

}
