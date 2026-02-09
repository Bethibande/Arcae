package com.bethibande.repository.web.api;

import com.bethibande.repository.jpa.artifact.Artifact;
import com.bethibande.repository.jpa.artifact.ArtifactDTO;
import com.bethibande.repository.jpa.artifact.ArtifactVersion;
import com.bethibande.repository.jpa.artifact.ArtifactVersionDTO;
import com.bethibande.repository.jpa.repository.Repository;
import com.bethibande.repository.jpa.repository.RepositoryManager;
import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.repository.ManagedRepository;
import com.bethibande.repository.repository.maven.MavenRepository;
import com.bethibande.repository.web.AuthenticatedUser;
import io.quarkus.panache.common.Sort;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.*;
import org.hibernate.search.engine.search.predicate.dsl.BooleanPredicateClausesStep;
import org.hibernate.search.engine.search.predicate.dsl.PredicateFinalStep;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.engine.search.sort.dsl.TypedSearchSortFactory;
import org.hibernate.search.mapper.orm.session.SearchSession;

import java.util.ArrayList;
import java.util.List;

@Path("/api/v1/artifact")
public class ArtifactEndpoint {

    @Inject
    RepositoryManager repositoryManager;
    @Inject
    AuthenticatedUser authenticatedUser;

    public enum ArtifactSortOrder {
        BEST_MATCH,
        LAST_UPDATED
    }

    public record ArtifactQuery(
            @QueryParam("r") long repositoryId,
            @QueryParam("q") String text,
            @QueryParam("p") @Min(0) int page,
            @QueryParam("s") @Max(100) @Min(1) int pageSize,
            @QueryParam("o") ArtifactSortOrder sortOrder
    ) {
    }

    @Inject
    public SearchSession searchSession;

    @GET
    @Transactional
    public PagedResponse<ArtifactDTO> searchByGroupAndName(final @BeanParam ArtifactQuery query) {
        final User self = authenticatedUser.getSelf();
        final Repository repository = Repository.findById(query.repositoryId());
        if (repository == null) throw new NotFoundException("Unknown repository");
        if (!repository.canView(self)) throw new ForbiddenException("Unauthorized");

        final SearchResult<Artifact> result = searchSession.search(Artifact.class)
                .where(q -> {
                    final List<PredicateFinalStep> predicates = new ArrayList<>();
                    predicates.add(q.match().field("repository.id").matching(query.repositoryId()));
                    if (query.text() != null) {
                        predicates.add(q.match()
                                .field("groupId")
                                .field("artifactId")
                                .matching(query.text())
                                .fuzzy(2));
                    }

                    final BooleanPredicateClausesStep<?, ?> step = q.bool();
                    predicates.forEach(step::must);

                    return step;
                })
                .sort(query.sortOrder == ArtifactSortOrder.LAST_UPDATED ? (b -> b.field("lastUpdated").desc()) : TypedSearchSortFactory::score)
                .fetch(query.page() * query.pageSize(), query.pageSize());

        final long total = result.total().hitCount();
        final int totalPages = (int) Math.ceil(total / (double) query.pageSize());

        return new PagedResponse<>(
                result.hits()
                        .stream()
                        .map(ArtifactDTO::from)
                        .toList(),
                query.page(),
                totalPages,
                (int) total
        );
    }

    @GET
    @Transactional
    @Path("/{id}")
    public ArtifactDTO getArtifact(final @PathParam("id") long id) {
        final Artifact artifact = Artifact.findById(id);
        if (artifact == null) throw new NotFoundException("Unknown artifact");

        final User self = authenticatedUser.getSelf();
        if (!artifact.repository.canView(self)) throw new ForbiddenException("Unauthorized");

        return ArtifactDTO.from(artifact);
    }

    @GET
    @Transactional
    @Path("/{id}/versions")
    public PagedResponse<ArtifactVersionDTO> getArtifactVersions(final @PathParam("id") long id,
                                                                 final @QueryParam("p") @Min(0) int page,
                                                                 final @QueryParam("s") @Max(100) @DefaultValue("20") int pageSize) {
        final Artifact artifact = Artifact.<Artifact>findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Artifact not found"));

        final User self = authenticatedUser.getSelf();
        if (!artifact.repository.canView(self)) throw new ForbiddenException("Unauthorized");

        final var query = ArtifactVersion.<ArtifactVersion>find("artifact.id = ?1", Sort.descending("updated"), artifact.id)
                .page(page, pageSize);

        final List<ArtifactVersion> versions = query.list();
        final long total = query.count();
        final int totalPages = (int) Math.ceil(total / (double) pageSize);

        return new PagedResponse<>(
                versions.stream()
                        .map(ArtifactVersionDTO::from)
                        .toList(),
                page,
                totalPages,
                (int) total
        );
    }

    @DELETE
    @Transactional
    @Path("/{id}")
    public void delete(final @PathParam("id") long id) {
        final Artifact artifact = Artifact.findById(id);
        if (artifact == null) throw new NotFoundException("Unknown artifact");

        final Repository repositoryEntity = artifact.repository;
        final ManagedRepository repository = repositoryManager.findRepository(repositoryEntity.name, repositoryEntity.packageManager);

        final User self = authenticatedUser.getSelf();

        repository.delete(self, artifact, false);
    }

    @DELETE
    @Transactional
    @Path("/version/{id}")
    public void deleteVersion(final @PathParam("id") long id) {
        final ArtifactVersion version = ArtifactVersion.findById(id);
        if (version == null) throw new NotFoundException("Unknown version");

        final Repository repositoryEntity = version.artifact.repository;
        final ManagedRepository repository = repositoryManager.findRepository(repositoryEntity.name, repositoryEntity.packageManager);

        final User self = authenticatedUser.getSelf();

        repository.delete(self, version, true);
    }

}
