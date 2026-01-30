package com.bethibande.repository.web.api;

import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.jpa.user.UserDTOWithoutId;
import com.bethibande.repository.jpa.user.UserRole;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import java.util.List;
import java.util.Set;

@Path("/api/v1/setup")
public class SetupEndpoint {

    @POST
    @Transactional
    @Path("/user")
    public void createAdminUser(final UserDTOWithoutId dto) {
        if (User.count() > 0) throw new BadRequestException();

        final User user = new User();
        user.name = dto.name();
        user.email = dto.email();
        user.password = BcryptUtil.bcryptHash(dto.password());
        user.roles = List.of(UserRole.ADMIN, UserRole.DEFAULT);
        user.persist();
    }

}
