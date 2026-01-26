package com.bethibande.repository.web.api;

import com.bethibande.repository.jpa.repository.*;
import com.bethibande.repository.web.CRUDResponse;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;

import java.util.List;

@Path("/api/v1/")
public class RepositoryEndpoint {

    @POST
    @Transactional
    public RepositoryDTO create(final RepositoryDTOWithoutId dto) {
        final Repository repository = new Repository();
        repository.name = dto.name();
        repository.type = dto.type();
        repository.settings = dto.settings();
        repository.backend = RepositoryBackend.findById(dto.backendId());

        if (repository.backend == null) throw new BadRequestException("Backend id unknown");

        repository.persist();
        return RepositoryDTO.from(repository);
    }

    @PUT
    @Transactional
    public RepositoryDTO update(final RepositoryDTO dto) {
        final Repository repository = Repository.findById(dto.id());
        if (repository == null) throw new NotFoundException();

        repository.name = dto.name();
        repository.type = dto.type();
        repository.settings = dto.settings();
        repository.backend = RepositoryBackend.findById(dto.backendId());

        if (repository.backend == null) throw new BadRequestException("Backend id unknown");
        return RepositoryDTO.from(repository);
    }

    @GET
    @Transactional
    public List<RepositoryDTOWithoutBackend> findAll() {
        return Repository.<Repository>listAll()
                .stream()
                .map(RepositoryDTOWithoutBackend::from)
                .toList();
    }

    @DELETE
    public CRUDResponse<Void> delete(final long id) {
        return Repository.deleteById(id)
                ? CRUDResponse.success(null)
                : CRUDResponse.failure("Not found", "error.notFound");
    }

}
