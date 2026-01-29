package com.bethibande.repository.web.repositories;

import com.bethibande.repository.jpa.repository.PackageManager;
import com.bethibande.repository.jpa.repository.RepositoryManager;
import com.bethibande.repository.repository.StreamHandle;
import com.bethibande.repository.repository.maven.MavenRepository;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.apache.http.HttpHeaders;

import java.io.InputStream;

@Path("/repositories/maven/{repository}/{path:.*}")
public class MavenRepositoryEndpoint {

    private final RepositoryManager repositoryManager;

    @Inject
    public MavenRepositoryEndpoint(final RepositoryManager repositoryManager) {
        this.repositoryManager = repositoryManager;
    }

    @PUT
    @Transactional
    public void deployArtifact(final @PathParam("repository") String repository,
                               final @PathParam("path") String path,
                               final @HeaderParam(HttpHeaders.CONTENT_TYPE) String contentType,
                               final @HeaderParam(HttpHeaders.CONTENT_LENGTH) long contentLength,
                               final InputStream data) {
        final MavenRepository repo = repositoryManager.findRepository(repository, PackageManager.MAVEN_3);
        if (repo == null) throw new NotFoundException("Unknown repository");

        repo.put(path, new StreamHandle(data, contentType, contentLength));
    }

    @GET
    public Response getArtifact(final @PathParam("repository") String repository,
                                final @PathParam("path") String path) {
        final MavenRepository repo = repositoryManager.findRepository(repository, PackageManager.MAVEN_3);
        if (repo == null) throw new NotFoundException("Unknown repository");

        final StreamHandle handle = repo.get(path);
        if (handle == null) throw new NotFoundException("Artifact not found");

        return Response.ok()
                .header(HttpHeaders.CONTENT_TYPE, handle.contentType())
                .header(HttpHeaders.CONTENT_LENGTH, handle.contentLength())
                .entity(handle.stream())
                .build();
    }

}
