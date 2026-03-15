package com.bethibande.repository.web.repositories;

import com.bethibande.repository.jpa.repository.PackageManager;
import com.bethibande.repository.jpa.repository.RepositoryManager;
import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.repository.StreamHandle;
import com.bethibande.repository.repository.maven.MavenRepository;
import com.bethibande.repository.repository.security.AuthContext;
import com.bethibande.repository.web.AuthenticatedUser;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.TransactionSemantics;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.apache.http.HttpHeaders;

import java.io.InputStream;

@Path("/repositories/maven/{repository}/{path:.*}")
public class MavenRepositoryEndpoint {

    private final RepositoryManager repositoryManager;
    private final AuthenticatedUser authenticatedUser;

    @Inject
    public MavenRepositoryEndpoint(final RepositoryManager repositoryManager,
                                   final AuthenticatedUser authenticatedUser) {
        this.repositoryManager = repositoryManager;
        this.authenticatedUser = authenticatedUser;
    }

    protected MavenRepository getRepositoryOrThrow(final String repository) {
        return QuarkusTransaction.runner(TransactionSemantics.JOIN_EXISTING).call(() -> {
            final MavenRepository repo = repositoryManager.findRepository(repository, PackageManager.MAVEN);
            if (repo == null) throw new NotFoundException("Unknown repository");

            return repo;
        });
    }

    @PUT
    public void deployArtifact(final @PathParam("repository") String repository,
                               final @PathParam("path") String path,
                               final @HeaderParam(HttpHeaders.CONTENT_TYPE) String contentType,
                               final @HeaderParam(HttpHeaders.CONTENT_LENGTH) long contentLength,
                               final InputStream data) {
        final MavenRepository repo = getRepositoryOrThrow(repository);
        final User user = this.authenticatedUser.getSelf();

        repo.put(AuthContext.ofUser(user), path, new StreamHandle(data, contentType, contentLength));
    }

    @GET
    public Response getArtifact(final @PathParam("repository") String repository,
                                final @PathParam("path") String path) {
        final MavenRepository repo = getRepositoryOrThrow(repository);
        final User user = this.authenticatedUser.getSelf();

        final StreamHandle handle = repo.get(AuthContext.ofUser(user), path);
        if (handle == null) throw new NotFoundException("Artifact not found");

        return Response.ok()
                .header(HttpHeaders.CONTENT_TYPE, handle.contentType())
                .header(HttpHeaders.CONTENT_LENGTH, handle.contentLength())
                .entity(handle.stream())
                .build();
    }

}
