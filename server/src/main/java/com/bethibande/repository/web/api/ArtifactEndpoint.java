package com.bethibande.repository.web.api;

import com.bethibande.repository.jpa.artifact.Artifact;
import com.bethibande.repository.jpa.artifact.ArtifactDTO;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
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

    @Inject
    public SearchSession searchSession;

    @GET
    @Transactional
    public PagedResponse<ArtifactDTO> searchByGroupAndName(final @BeanParam ArtifactQuery query) {
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

}
