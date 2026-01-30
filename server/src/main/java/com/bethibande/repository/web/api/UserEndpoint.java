package com.bethibande.repository.web.api;

import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.jpa.user.UserDTOWithoutId;
import com.bethibande.repository.jpa.user.UserDTOWithoutPassword;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;

@RolesAllowed("ADMIN")
@Path("/api/v1/user")
public class UserEndpoint {

    @POST
    public UserDTOWithoutPassword create(final UserDTOWithoutId dto) {
        final User user = new User();
        user.name = dto.name();
        user.email = dto.email();
        user.roles = dto.roles();
        user.password = BcryptUtil.bcryptHash(dto.password());
        user.persist();

        return UserDTOWithoutPassword.from(user);
    }

    @PUT
    public UserDTOWithoutPassword update(final UserDTOWithoutPassword dto) {
        final User user = User.findById(dto.id());
        if (user == null) throw new NotFoundException("Unknown user");

        user.name = dto.name();
        user.email = dto.email();
        user.roles = dto.roles();
        user.persist();

        return dto;
    }

    @GET
    public UserDTOWithoutPassword get(final @QueryParam("id") long id) {
        final User user = User.findById(id);
        if (user == null) throw new NotFoundException("Unknown user");
        return UserDTOWithoutPassword.from(user);
    }

    @DELETE
    public void delete(final @QueryParam("id") long id) {
        User.deleteById(id);
    }

}
