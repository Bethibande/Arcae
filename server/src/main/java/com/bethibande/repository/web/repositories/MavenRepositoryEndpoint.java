package com.bethibande.repository.web.repositories;

import com.bethibande.repository.jpa.repository.PackageManager;
import com.bethibande.repository.jpa.repository.RepositoryManager;
import com.bethibande.repository.jpa.user.AccessToken;
import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.repository.StreamHandle;
import com.bethibande.repository.repository.maven.MavenRepository;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.apache.http.HttpHeaders;

import java.io.InputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

@Path("/repositories/maven/{repository}/{path:.*}")
public class MavenRepositoryEndpoint {

    private final RepositoryManager repositoryManager;

    @Inject
    public MavenRepositoryEndpoint(final RepositoryManager repositoryManager) {
        this.repositoryManager = repositoryManager;
    }

    protected User extractUser(final String authorization) {
        User user = null;
        if (authorization != null && authorization.startsWith("Basic ")) {
            final String credentials = new String(Base64.getDecoder().decode(authorization.substring("Basic ".length())));
            final String[] parts = credentials.split(":");

            final String username = parts[0];
            final String tokenString = parts[1];

            final AccessToken token = AccessToken.find("token = ?1", tokenString).firstResult();
            if (token == null
                    || !Objects.equals(token.owner.name, username)
                    || token.isExpired(Instant.now())) throw new ForbiddenException("Invalid token");

            user = token.owner;
        }

        return user;
    }

    @PUT
    @Transactional
    public void deployArtifact(final @PathParam("repository") String repository,
                               final @PathParam("path") String path,
                               final @HeaderParam(HttpHeaders.CONTENT_TYPE) String contentType,
                               final @HeaderParam(HttpHeaders.CONTENT_LENGTH) long contentLength,
                               final @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
                               final InputStream data) {
        final MavenRepository repo = repositoryManager.findRepository(repository, PackageManager.MAVEN_3);
        if (repo == null) throw new NotFoundException("Unknown repository");

        final User user = extractUser(authorization);

        repo.put(user, path, new StreamHandle(data, contentType, contentLength), false);
    }

    @GET
    @Transactional
    public Response getArtifact(final @PathParam("repository") String repository,
                                final @PathParam("path") String path,
                                final @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
        final MavenRepository repo = repositoryManager.findRepository(repository, PackageManager.MAVEN_3);
        if (repo == null) throw new NotFoundException("Unknown repository");

        final User user = extractUser(authorization);

        final StreamHandle handle = repo.get(user, path);
        if (handle == null) throw new NotFoundException("Artifact not found");

        return Response.ok()
                .header(HttpHeaders.CONTENT_TYPE, handle.contentType())
                .header(HttpHeaders.CONTENT_LENGTH, handle.contentLength())
                .entity(handle.stream())
                .build();
    }

}
