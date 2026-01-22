package com.bethibande.repository.web.repositories;

import com.bethibande.repository.repository.ArtifactDescriptor;
import com.bethibande.repository.repository.IRepository;
import com.bethibande.repository.repository.RepositoryManager;
import com.bethibande.repository.repository.impl.Maven3Repository;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;

import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Path("/repositories/maven")
public class MavenRepositoryEndpoint {

    private final RepositoryManager repositoryManager;

    @Inject
    public MavenRepositoryEndpoint(final RepositoryManager repositoryManager) {
        this.repositoryManager = repositoryManager;
    }

    protected Maven3Repository resolveRepositoryOrThrowNotFound(final String name) {
        final IRepository repository = repositoryManager.getRepositoryByName(name);
        if (repository == null) {
            throw new NotFoundException(name);
        }
        if (!(repository instanceof Maven3Repository mavenRepository)) {
            throw new NotFoundException();
        }

        return mavenRepository;
    }

    @GET
    @Transactional
    @Path("/{repository}/{path:.*}")
    public Response get(final @PathParam("repository") String repositoryName,
                        final @PathParam("path") String path) {
        final Maven3Repository repository = resolveRepositoryOrThrowNotFound(repositoryName);
        final Optional<ArtifactDescriptor> descriptor = repository.getBackend()
                .get(path);

        if (descriptor.isPresent()) {
            final ArtifactDescriptor artifact = descriptor.get();
            return Response.ok()
                    .entity(artifact.stream())
                    .header(HttpHeaders.CONTENT_LENGTH, artifact.contentLength())
                    .header(HttpHeaders.CONTENT_TYPE, artifact.contentType())
                    .build();
        }

        return Response.status(HttpStatus.SC_NOT_FOUND)
                .build();
    }

    @PUT
    @Transactional
    @Path("/{repository}/{path:.*}")
    public Uni<Response> put(final @PathParam("repository") String repositoryName,
                             final @PathParam("path") String path,
                             final @HeaderParam(HttpHeaders.CONTENT_LENGTH) long contentLength,
                             final @HeaderParam(HttpHeaders.CONTENT_TYPE) String contentType,
                             final @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
                             final InputStream data) {
        final Maven3Repository repository = resolveRepositoryOrThrowNotFound(repositoryName);
        final CompletableFuture<Void> future = repository.uploadFile(path, data, contentLength, contentType);

        return Uni.createFrom()
                .completionStage(future)
                .map(_ -> Response.status(HttpStatus.SC_CREATED).build());
    }

}
