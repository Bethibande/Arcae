package com.bethibande.arcae.web.api;

import com.bethibande.arcae.jpa.repository.Repository;
import com.bethibande.arcae.jpa.repository.permissions.PermissionScope;
import com.bethibande.arcae.jpa.repository.permissions.PermissionScopeDTO;
import com.bethibande.arcae.jpa.repository.permissions.PermissionScopeDTOWithoutId;
import com.bethibande.arcae.jpa.user.User;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;

import java.util.Comparator;
import java.util.List;

@RolesAllowed("ADMIN")
@Path("/api/v1/repository")
public class RepositoryPermissionEndpoint {

    @POST
    @Transactional
    @Path("/permission")
    public PermissionScopeDTO create(final PermissionScopeDTOWithoutId dto) {
        final PermissionScope scope = new PermissionScope();
        scope.repository = Repository.findById(dto.repositoryId());
        scope.level = dto.level();
        scope.type = dto.type();
        scope.user = dto.userId() != null ? User.findById(dto.userId()) : null;

        if (scope.repository == null) throw new NotFoundException("Unknown repository");

        scope.persist();

        return PermissionScopeDTO.from(scope);
    }

    @PUT
    @Transactional
    @Path("/permission")
    public PermissionScopeDTO update(final PermissionScopeDTO dto) {
        final PermissionScope scope = PermissionScope.findById(dto.id());
        if (scope == null) throw new NotFoundException("Unknown permission scope");

        scope.level = dto.level();
        scope.type = dto.type();
        scope.user = dto.userId() != null ? User.findById(dto.userId()) : null;

        scope.persist();

        return PermissionScopeDTO.from(scope);
    }

    @GET
    @Transactional
    @Path("/{id}/permissions")
    public List<PermissionScopeDTO> list(@PathParam("id") final long id) {
        final Repository repository = Repository.findById(id);
        if (repository == null) throw new NotFoundException("Unknown repository");

        return repository.permissions.stream()
                .sorted(Comparator.comparing(p -> p.id))
                .map(PermissionScopeDTO::from)
                .toList();
    }

    @DELETE
    @Transactional
    @Path("/permission/{id}")
    public void delete(@PathParam("id") final long id) {
        PermissionScope.deleteById(id);
    }


}
