package com.bethibande.repository.web.api;

import com.bethibande.repository.jpa.repository.Repository;
import com.bethibande.repository.jpa.repository.RepositoryBackend;
import com.bethibande.repository.jpa.repository.RepositoryBackendDTO;
import com.bethibande.repository.jpa.repository.RepositoryBackendDTOWithoutId;
import com.bethibande.repository.web.CRUDResponse;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;

import java.util.List;

@Path("/api/v1/repository/backend")
public class RepositoryBackendEndpoint {

    @POST
    @Transactional
    public RepositoryBackendDTO create(final RepositoryBackendDTOWithoutId dto) {
        final RepositoryBackend backend = new RepositoryBackend();
        backend.name = dto.name();
        backend.type = dto.type();
        backend.settings = dto.settings();
        backend.persist();

        return RepositoryBackendDTO.from(backend);
    }

    @PUT
    @Transactional
    public RepositoryBackendDTO update(final RepositoryBackendDTO dto) {
        final RepositoryBackend backend = RepositoryBackend.findById(dto.id());
        if (backend == null) throw new NotFoundException();

        backend.name = dto.name();
        backend.type = dto.type();
        backend.settings = dto.settings();

        return RepositoryBackendDTO.from(backend);
    }

    @GET
    @Transactional
    public List<RepositoryBackendDTO> findAll() {
        return RepositoryBackend.<RepositoryBackend>listAll()
                .stream()
                .map(RepositoryBackendDTO::from)
                .toList();
    }

    @DELETE
    public CRUDResponse<Void> delete(final long id) {
        if (Repository.find("backend.id = ?1", id).count() > 0) return CRUDResponse.failure("Entity is used", "error.dependencies");

        return RepositoryBackend.deleteById(id)
                ? CRUDResponse.success(null)
                : CRUDResponse.failure("not found", "error.notFound");
    }

}
