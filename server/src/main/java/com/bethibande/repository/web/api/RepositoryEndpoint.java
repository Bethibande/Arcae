package com.bethibande.repository.web.api;

import com.bethibande.repository.jpa.artifact.Artifact;
import com.bethibande.repository.jpa.artifact.ArtifactVersion;
import com.bethibande.repository.jpa.repository.PublicRepositoryDTO;
import com.bethibande.repository.jpa.repository.Repository;
import com.bethibande.repository.jpa.repository.RepositoryDTO;
import com.bethibande.repository.jpa.repository.RepositoryDTOWithoutId;
import com.bethibande.repository.web.CRUDResponse;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;

import java.time.Instant;
import java.util.List;

@RolesAllowed("ADMIN")
@Path("/api/v1/repository")
public class RepositoryEndpoint {

    @POST
    @Transactional
    public RepositoryDTO create(final RepositoryDTOWithoutId dto) {
        final Repository repository = new Repository();
        repository.name = dto.name();
        repository.packageManager = dto.packageManager();
        repository.settings = dto.settings();

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

        return new RepositoryOverviewDTO(
                PublicRepositoryDTO.from(repository),
                Artifact.count("repository.id = ?1", repository.id),
                ArtifactVersion.findMaxUpdated(id)
        );
    }

    @GET
    @PermitAll
    @Transactional
    @Path("/overview")
    public List<RepositoryOverviewDTO> overview() {

        final List<PublicRepositoryDTO> repositories = Repository.<Repository>listAll()
                .stream()
                .map(PublicRepositoryDTO::from)
                .toList();

        return repositories.stream()
                .map(repository -> new RepositoryOverviewDTO(
                        repository,
                        Artifact.count("repository.id = ?1", repository.id()),
                        ArtifactVersion.findMaxUpdated(repository.id())
                ))
                .toList();
    }

    @DELETE
    @Transactional
    @Path("/{id}")
    public CRUDResponse<Void> delete(@PathParam("id") final Long id) {
        if (Artifact.count("repository.id = ?", id) > 0) return CRUDResponse.failure("Cannot delete repository with artifacts", "repository.delete.artifacts");

        return Repository.deleteById(id)
                ? CRUDResponse.success(null)
                : CRUDResponse.failure("Unknown repository", "repository.delete.unknown");
    }

}
