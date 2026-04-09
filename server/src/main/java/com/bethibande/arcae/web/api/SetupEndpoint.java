package com.bethibande.arcae.web.api;

import com.bethibande.arcae.jpa.user.User;
import com.bethibande.arcae.jpa.user.UserDTOWithoutIdAndRoles;
import com.bethibande.arcae.jpa.user.UserRole;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;

import java.util.List;

@Path("/api/v1/setup")
public class SetupEndpoint {

    private volatile boolean complete = false;

    @POST
    @Transactional
    @Path("/user")
    public void createAdminUser(final UserDTOWithoutIdAndRoles dto) {
        if (this.complete || User.count() > 0) throw new BadRequestException();

        final User user = new User();
        user.name = dto.name();
        user.email = dto.email();
        user.password = BcryptUtil.bcryptHash(dto.password());
        user.roles = List.of(UserRole.ADMIN, UserRole.DEFAULT);
        user.persist();

        this.complete = true;
    }

    @GET
    @Transactional
    @Path("/complete")
    @Produces("application/json")
    public boolean isComplete() {
        return this.complete || User.count() > 0;
    }

}
