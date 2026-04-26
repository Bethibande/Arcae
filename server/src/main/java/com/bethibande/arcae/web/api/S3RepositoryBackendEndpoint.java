package com.bethibande.arcae.web.api;

import com.bethibande.arcae.jpa.repository.Repository;
import com.bethibande.arcae.jpa.repository.S3RepositoryBackend;
import com.bethibande.arcae.jpa.repository.S3RepositoryBackendDTO;
import com.bethibande.arcae.jpa.repository.S3RepositoryBackendDTOWithoutId;
import com.bethibande.arcae.web.exception.ConflictWebException;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.*;

import java.util.stream.Collectors;

@RolesAllowed("ADMIN")
@Path("/api/v1/s3backend")
public class S3RepositoryBackendEndpoint {

    @POST
    @Transactional
    public S3RepositoryBackendDTO create(final S3RepositoryBackendDTOWithoutId dto) {
        final S3RepositoryBackend entity = new S3RepositoryBackend();
        entity.name = dto.name();
        entity.uri = dto.uri();
        entity.region = dto.region();
        entity.bucket = dto.bucket();
        entity.accessKey = dto.accessKey();
        entity.secretKey = dto.secretKey();

        entity.persist();

        return S3RepositoryBackendDTO.from(entity);
    }

    @PUT
    @Transactional
    public S3RepositoryBackendDTO update(final S3RepositoryBackendDTO dto) {
        final S3RepositoryBackend entity = S3RepositoryBackend.findById(dto.id());
        if (entity == null) throw new NotFoundException();

        entity.name = dto.name();
        entity.uri = dto.uri();
        entity.region = dto.region();
        entity.bucket = dto.bucket();
        entity.accessKey = dto.accessKey();
        entity.secretKey = dto.secretKey();

        return S3RepositoryBackendDTO.from(entity);
    }

    @GET
    @Transactional
    public PagedResponse<S3RepositoryBackendDTO> list(final @QueryParam("p") @Min(0) int page,
                                                      final @QueryParam("s") @Min(1) @Max(100) int pageSize) {
        final PanacheQuery<S3RepositoryBackend> query = S3RepositoryBackend.findAll(Sort.descending("id"));

        final long total = query.count();
        final int pages = (int) Math.ceil((double) total / pageSize);

        return new PagedResponse<>(
                query.list()
                        .stream()
                        .map(S3RepositoryBackendDTO::from)
                        .collect(Collectors.toList()),
                page,
                pages,
                (int) total
        );
    }

    @DELETE
    @Transactional
    @Path("/{id}")
    public void delete(final @PathParam("id") long id) {
        if (Repository.count("backend.id = ?1", id) > 0) throw new ConflictWebException("Backend is still in use");

        S3RepositoryBackend.deleteById(id);
    }

}
