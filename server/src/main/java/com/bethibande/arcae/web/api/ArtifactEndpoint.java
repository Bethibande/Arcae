package com.bethibande.arcae.web.api;

import com.bethibande.arcae.jpa.artifact.Artifact;
import com.bethibande.arcae.jpa.artifact.ArtifactDTO;
import com.bethibande.arcae.jpa.artifact.ArtifactVersion;
import com.bethibande.arcae.jpa.artifact.ArtifactVersionDTO;
import com.bethibande.arcae.jpa.repository.Repository;
import com.bethibande.arcae.jpa.repository.RepositoryManager;
import com.bethibande.arcae.jpa.user.User;
import com.bethibande.arcae.repository.ManagedRepository;
import com.bethibande.arcae.repository.security.AuthContext;
import com.bethibande.arcae.web.AuthenticatedUser;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.search.engine.search.predicate.dsl.BooleanPredicateClausesStep;
import org.hibernate.search.engine.search.predicate.dsl.PredicateFinalStep;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.engine.search.sort.dsl.TypedSearchSortFactory;
import org.hibernate.search.mapper.orm.session.SearchSession;

import java.util.ArrayList;
import java.util.List;

@Path("/api/v1/artifact")
public class ArtifactEndpoint {

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

    public record VersionQuery(
            @QueryParam("q") String text,
            @QueryParam("p") @Min(0) int page,
            @QueryParam("s") @Max(100) @Min(1) int pageSize,
            @QueryParam("o") ArtifactSortOrder sortOrder
    ) {
    }

    @Inject
    protected RepositoryManager repositoryManager;

    @Inject
    protected AuthenticatedUser authenticatedUser;

    @Inject
    protected Instance<SearchSession> searchSession;

    @ConfigProperty(name = "arcae.search.enabled")
    protected boolean useSearchORM;

    protected PagedResponse<ArtifactDTO> searchByGroupAndNameUsingSearchORM(final ArtifactQuery query) {
        final SearchResult<Artifact> result = searchSession.get()
                .search(Artifact.class)
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

    protected PagedResponse<ArtifactDTO> searchByGroupAndNameUsingDatabase(final ArtifactQuery query) {
        final Sort sort = switch (query.sortOrder()) {
            case LAST_UPDATED -> Sort.descending("lastUpdated");
            case BEST_MATCH -> Sort.ascending("groupId", "artifactId");
        };

        final PanacheQuery<Artifact> q = query.text() != null
                ? Artifact.find("repository.id = ?1 AND (groupId LIKE ?2 OR artifactId LIKE ?2)", sort, query.repositoryId(), "%" + query.text + "%")
                : Artifact.find("repository.id = ?1", sort, query.repositoryId());

        final long total = q.count();
        final int totalPages = (int) Math.ceil((double) total / query.pageSize());

        return new PagedResponse<>(
                q.page(query.page(), query.pageSize())
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
    public PagedResponse<ArtifactDTO> searchByGroupAndName(final @BeanParam ArtifactQuery query) {
        final User self = authenticatedUser.getSelf();
        final Repository repository = Repository.findById(query.repositoryId());
        if (repository == null) throw new NotFoundException("Unknown repository");
        if (!repository.canView(AuthContext.ofUser(self))) throw new ForbiddenException("Unauthorized");

        if (this.useSearchORM) {
            return searchByGroupAndNameUsingSearchORM(query);
        }
        return searchByGroupAndNameUsingDatabase(query);
    }

    protected PagedResponse<ArtifactVersionDTO> searchVersionsUsingSearchORM(final long id,
                                                                             final VersionQuery query) {
        final SearchResult<ArtifactVersion> result = this.searchSession.get().search(ArtifactVersion.class)
                .where(q -> {
                    final List<PredicateFinalStep> predicates = new ArrayList<>();
                    predicates.add(q.match().field("artifact.id").matching(id));
                    if (query.text() != null) {
                        predicates.add(q.match().field("version").matching(query.text()));
                    }

                    final BooleanPredicateClausesStep<?, ?> step = q.bool();
                    predicates.forEach(step::must);

                    return step;
                })
                .sort(query.sortOrder == ArtifactSortOrder.LAST_UPDATED ? (b -> b.field("updated").desc()) : TypedSearchSortFactory::score)
                .fetch(query.page() * query.pageSize(), query.pageSize());

        final long total = result.total().hitCount();
        final int totalPages = (int) Math.ceil(total / (double) query.pageSize());

        return new PagedResponse<>(
                result.hits()
                        .stream()
                        .map(ArtifactVersionDTO::from)
                        .toList(),
                query.page(),
                totalPages,
                (int) total
        );
    }

    protected PagedResponse<ArtifactVersionDTO> searchVersionsUsingDatabase(final long id,
                                                                            final VersionQuery query) {
        final Sort sort = Sort.descending("updated");
        final PanacheQuery<ArtifactVersion> q = query.text() != null
                ? ArtifactVersion.find("artifact.id = ?1 AND version LIKE ?2", sort, id, "%" + query.text() + "%")
                : ArtifactVersion.find("artifact.id = ?1", sort, id);

        final long total = q.count();
        final int totalPages = (int) Math.ceil(total / (double) query.pageSize());

        return new PagedResponse<>(
                q.page(query.page(), query.pageSize())
                        .list()
                        .stream()
                        .map(ArtifactVersionDTO::from)
                        .toList(),
                query.page(),
                totalPages,
                (int) total
        );
    }

    @GET
    @Transactional
    @Path("/{id}/versions/search")
    public PagedResponse<ArtifactVersionDTO> searchVersions(final @PathParam("id") long id, final @BeanParam VersionQuery query) {
        final Artifact artifact = Artifact.findById(id);
        if (artifact == null) throw new NotFoundException();

        final User self = authenticatedUser.getSelf();
        final Repository repository = artifact.repository;
        if (!repository.canView(AuthContext.ofUser(self))) throw new ForbiddenException("Unauthorized");

        if (this.useSearchORM) {
            return searchVersionsUsingSearchORM(id, query);
        }
        return searchVersionsUsingDatabase(id, query);
    }

    @GET
    @Transactional
    @Path("/{id}")
    public ArtifactDTO getArtifact(final @PathParam("id") long id) {
        final Artifact artifact = Artifact.findById(id);
        if (artifact == null) throw new NotFoundException("Unknown artifact");

        final User self = authenticatedUser.getSelf();
        if (!artifact.repository.canView(AuthContext.ofUser(self))) throw new ForbiddenException("Unauthorized");

        return ArtifactDTO.from(artifact);
    }

    @GET
    @Transactional
    @Path("/version/{id}")
    public ArtifactVersionDTO getArtifactVersion(final @PathParam("id") long versionId) {
        final ArtifactVersion version = ArtifactVersion.findById(versionId);
        if (version == null) throw new NotFoundException("Unknown version");

        final User self = authenticatedUser.getSelf();
        final Repository repository = version.artifact.repository;
        if (!repository.canView(AuthContext.ofUser(self))) throw new ForbiddenException("Unauthorized");

        return ArtifactVersionDTO.from(version);
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
        if (!artifact.repository.canView(AuthContext.ofUser(self))) throw new ForbiddenException("Unauthorized");

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

        repository.delete(AuthContext.ofUser(self), artifact);
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

        repository.delete(AuthContext.ofUser(self), version);
    }

}
