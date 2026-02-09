package com.bethibande.repository.web.api;

import com.bethibande.repository.jpa.artifact.Artifact;
import com.bethibande.repository.jpa.artifact.ArtifactVersion;
import com.bethibande.repository.jpa.repository.PublicRepositoryDTO;
import com.bethibande.repository.jpa.repository.Repository;
import com.bethibande.repository.jpa.repository.RepositoryDTO;
import com.bethibande.repository.jpa.repository.RepositoryDTOWithoutId;
import com.bethibande.repository.jpa.repository.permissions.PermissionScope;
import com.bethibande.repository.jpa.repository.permissions.UserSelectionType;
import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.jpa.user.UserRole;
import com.bethibande.repository.web.AuthenticatedUser;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.criteria.*;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.Path;
import org.apache.http.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RolesAllowed("ADMIN")
@Path("/api/v1/repository")
public class RepositoryEndpoint {

    private final AuthenticatedUser authenticatedUser;

    @Inject
    public RepositoryEndpoint(final AuthenticatedUser authenticatedUser) {
        this.authenticatedUser = authenticatedUser;
    }

    @POST
    @Transactional
    public RepositoryDTO create(final RepositoryDTOWithoutId dto) {
        final Repository repository = new Repository();
        repository.name = dto.name();
        repository.packageManager = dto.packageManager();
        repository.settings = dto.settings();
        repository.metadata = dto.metadata();
        repository.cleanupPolicies = dto.cleanupPolicies();

        repository.persist();

        return RepositoryDTO.from(repository);
    }

    @PUT
    @Transactional
    public RepositoryDTO update(final RepositoryDTO dto) {
        final Repository repository = Repository.findById(dto.id());
        if (repository == null) throw new NotFoundException("Unknown repository");

        repository.name = dto.name();
        repository.packageManager = dto.packageManager();
        repository.settings = dto.settings();
        repository.metadata = dto.metadata();
        repository.cleanupPolicies = dto.cleanupPolicies();
        repository.persist();

        return RepositoryDTO.from(repository);
    }

    @GET
    @Transactional
    public List<RepositoryDTO> list() {
        return Repository.<Repository>listAll()
                .stream()
                .map(RepositoryDTO::from)
                .toList();
    }

    public record RepositoryOverviewDTO(
            @NotNull PublicRepositoryDTO repository,
            long artifactsCount,
            Instant lastUpdated
    ) {
    }

    @GET
    @PermitAll
    @Transactional
    @Path("/overview/{id}")
    public RepositoryOverviewDTO overview(@PathParam("id") final long id) {
        final Repository repository = Repository.findById(id);
        if (repository == null) {
            throw new NotFoundException("Repository not found");
        }

        final User self = authenticatedUser.getSelf();
        if (!repository.canView(self)) throw new ForbiddenException("Unauthorized");

        return new RepositoryOverviewDTO(
                PublicRepositoryDTO.from(repository),
                Artifact.count("repository.id = ?1", repository.id),
                ArtifactVersion.findMaxUpdated(id)
        );
    }

    public enum RepositorySortOrder {
        LAST_UPDATED,
        ARTIFACT_COUNT,
        ALPHABETICAL
    }

    @GET
    @PermitAll
    @Transactional
    @Path("/overview")
    public List<RepositoryOverviewDTO> overview(final @QueryParam("o") @DefaultValue("ALPHABETICAL") RepositorySortOrder order) {
        final CriteriaBuilder builder = Repository.getEntityManager().getCriteriaBuilder();
        final CriteriaQuery<Repository> query = builder.createQuery(Repository.class);
        final Root<Repository> root = query.from(Repository.class);
        final Join<Repository, List<PermissionScope>> scopeJoin = root.join("permissions", JoinType.LEFT);

        final List<Predicate> predicates = new ArrayList<>();
        predicates.add(builder.equal(scopeJoin.get("type"), UserSelectionType.ANONYMOUS));
        predicates.add(builder.isEmpty(root.get("permissions")));

        final User self = authenticatedUser.getSelf();
        if (self != null) {
            if (self.roles.contains(UserRole.ADMIN)) {
                predicates.add(builder.conjunction());
            } else {
                predicates.add(builder.equal(scopeJoin.get("type"), UserSelectionType.AUTHENTICATED));
                predicates.add(builder.equal(scopeJoin.get("user"), self));
            }
        }

        query.where(builder.or(predicates.toArray(Predicate[]::new)));
        query.orderBy(builder.asc(root.get("name")));
        query.distinct(true);

        final List<PublicRepositoryDTO> repositories = Repository.getEntityManager()
                .createQuery(query)
                .getResultList()
                .stream()
                .map(PublicRepositoryDTO::from)
                .toList();

        final Comparator<RepositoryOverviewDTO> comparator = switch (order) {
            case ALPHABETICAL -> Comparator.comparing(dto -> dto.repository.name());
            case LAST_UPDATED ->
                    Comparator.comparing(RepositoryOverviewDTO::lastUpdated, Comparator.nullsLast(Comparator.reverseOrder()));
            case ARTIFACT_COUNT -> Comparator.comparing(RepositoryOverviewDTO::artifactsCount).reversed();
        };

        return repositories.stream()
                .map(repository -> new RepositoryOverviewDTO(
                        repository,
                        Artifact.count("repository.id = ?1", repository.id()),
                        ArtifactVersion.findMaxUpdated(repository.id())
                ))
                .sorted(comparator)
                .toList();
    }

    @GET
    @Transactional
    @Path("/{id}/can-write")
    public boolean canWrite(final @PathParam("id") long id) {
        final Repository repo = Repository.findById(id);
        if (repo == null) throw new NotFoundException("Unknown repository");

        final User self = authenticatedUser.getSelf();
        return repo.canWrite(self);
    }

    @DELETE
    @Transactional
    @Path("/{id}")
    public void delete(@PathParam("id") final Long id) {
        if (Artifact.count("repository.id = ?1", id) > 0)
            throw new ClientErrorException("Cannot delete repository with artifacts", HttpStatus.SC_CONFLICT);

        Repository.deleteById(id);
    }

}
