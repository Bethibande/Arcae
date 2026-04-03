package com.bethibande.repository.web.api;

import com.bethibande.repository.jpa.security.AccessToken;
import com.bethibande.repository.jpa.security.OpenIDConnection;
import com.bethibande.repository.jpa.security.RefreshToken;
import com.bethibande.repository.jpa.security.UserSession;
import com.bethibande.repository.jpa.user.*;
import com.bethibande.repository.web.AuthenticatedUser;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.apache.http.HttpStatus;
import org.hibernate.search.engine.search.predicate.dsl.BooleanPredicateClausesStep;
import org.hibernate.search.engine.search.predicate.dsl.PredicateFinalStep;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.engine.search.sort.dsl.TypedSearchSortFactory;
import org.hibernate.search.mapper.orm.session.SearchSession;

import java.util.ArrayList;
import java.util.List;

@RolesAllowed("ADMIN")
@Path("/api/v1/user")
public class UserEndpoint {

    @Inject
    public SearchSession searchSession;
    @Inject
    AuthenticatedUser authenticatedUser;
    @Inject
    AuthenticationEndpoint authenticationEndpoint;

    @POST
    @Transactional
    public UserDTOWithoutPassword create(final UserDTOWithoutId dto) {
        if (dto.roles().contains(UserRole.SYSTEM)) throw new NotAuthorizedException("System users cannot be created");

        final User user = new User();
        user.name = dto.name();
        user.email = dto.email();
        user.roles = dto.roles();
        user.password = BcryptUtil.bcryptHash(dto.password());
        user.persist();

        return UserDTOWithoutPassword.from(user);
    }

    @PUT
    @Transactional
    public UserDTOWithoutPassword update(final UserDTOWithoutPassword dto) {
        final User user = User.findById(dto.id());
        if (user == null) throw new NotFoundException("Unknown user");
        if (user.roles.contains(UserRole.SYSTEM)) throw new NotAuthorizedException("System users cannot be updated");

        user.name = dto.name();
        user.email = dto.email();
        user.roles = dto.roles();
        user.persist();

        return dto;
    }

    @GET
    @Transactional
    @Path("/{id}")
    public UserDTOWithoutPassword get(final @PathParam("id") long id) {
        final User user = User.findById(id);
        if (user == null) throw new NotFoundException("Unknown user");
        return UserDTOWithoutPassword.from(user);
    }

    @GET
    @Transactional
    @Path("/search")
    public PagedResponse<UserDTOWithoutPassword> search(final @QueryParam("q") String query,
                                                        final @QueryParam("p") @Min(0) int page,
                                                        final @QueryParam("s") @Max(100) @DefaultValue("20") int pageSize) {
        final SearchResult<User> result = searchSession.search(User.class)
                .where(q -> {
                    final List<PredicateFinalStep> predicates = new ArrayList<>();
                    predicates.add(q.match()
                            .field("name")
                            .matching(query)
                            .fuzzy(2));
                    predicates.add(q.match()
                            .field("email")
                            .matching(query)
                            .fuzzy(2));

                    final BooleanPredicateClausesStep<?, ?> step = q.bool();
                    predicates.forEach(step::should);

                    return step;
                })
                .sort(TypedSearchSortFactory::score)
                .fetch(page * pageSize, pageSize);

        final long hits = result.total().hitCount();
        final int totalPages = (int) Math.ceil(hits / (double) pageSize);

        return new PagedResponse<>(
                result.hits()
                        .stream()
                        .map(UserDTOWithoutPassword::from)
                        .toList(),
                page,
                totalPages,
                (int) hits
        );
    }

    @GET
    @Transactional
    public PagedResponse<UserDTOWithoutPassword> list(final @QueryParam("p") @Min(0) int page,
                                                      final @QueryParam("s") @Max(100) @DefaultValue("20") int pageSize) {
        final PanacheQuery<User> query = User.findAll(Sort.ascending("name")).page(page, pageSize);

        final long total = query.count();
        final int totalPages = (int) Math.ceil(total / (double) pageSize);

        return new PagedResponse<>(
                query.list()
                        .stream()
                        .map(UserDTOWithoutPassword::from)
                        .toList(),
                page,
                totalPages,
                (int) total
        );
    }

    @DELETE
    @Transactional
    public void delete(final @QueryParam("id") long id) {
        if (authenticatedUser.getSelf().id == id) throw new NotAuthorizedException("Cannot delete self");

        UserSession.delete("user.id = ?1", id);
        RefreshToken.delete("user.id = ?1", id);
        OpenIDConnection.delete("user.id = ?1", id);
        AccessToken.delete("owner.id = ?1", id);

        User.deleteById(id);
    }

    @PUT
    @Transactional
    @Authenticated
    @Path("/self")
    public Response updateSelf(final UserDTOWithoutRoles dto) {
        if (User.count("name = ?1", dto.name()) > 1)
            throw new WebApplicationException("Duplicate username", HttpStatus.SC_CONFLICT);
        if (User.count("email = ?1", dto.email()) > 1)
            throw new WebApplicationException("Duplicate email", HttpStatus.SC_CONFLICT);

        final User self = authenticatedUser.getSelf();
        if (!BcryptUtil.matches(dto.password(), self.password)) throw new ForbiddenException("Invalid password");

        final User user = User.findById(self.id); // Load the user to ensure it is attached to our transaction
        user.name = dto.name();
        user.email = dto.email();

        user.persist();

        return authenticationEndpoint.doLogin(self);
    }

    public record PasswordResetForm(
            String current,
            String newPassword
    ) {
    }

    @PUT
    @Transactional
    @Authenticated
    @Path("/self/password")
    public void resetPassword(final PasswordResetForm form) {
        final User self = authenticatedUser.getSelf();
        if (BcryptUtil.matches(form.current, self.password)) {
            self.password = BcryptUtil.bcryptHash(form.newPassword);
        } else {
            throw new ForbiddenException("Invalid current password");
        }
    }

}
