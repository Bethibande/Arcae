package com.bethibande.arcae.web.repositories.oci;

import com.bethibande.arcae.jpa.repository.PackageManager;
import com.bethibande.arcae.jpa.user.User;
import com.bethibande.arcae.repository.StreamHandle;
import com.bethibande.arcae.repository.helm.HelmIndex;
import com.bethibande.arcae.repository.helm.HelmIndexEntry;
import com.bethibande.arcae.repository.helm.HelmRepository;
import com.bethibande.arcae.repository.security.AuthContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Path("/repositories/helm/{repositoryId}")
public class HelmRepositoryEndpoint extends OCIRepositoryEndpoint {

    private final ObjectMapper mapper = new YAMLMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    public HelmRepositoryEndpoint() {
        super(PackageManager.HELM);
    }

    @Override
    protected HelmRepository repositoryOrThrow(final String repositoryId) {
        return (HelmRepository) super.repositoryOrThrow(repositoryId);
    }

    @GET
    @Produces("text/yaml")
    @Path("/repo/{namespace:.*}/index.yaml")
    public String getIndex(final @PathParam("repositoryId") String repositoryId,
                           final @PathParam("namespace") String namespace,
                           final @Context UriInfo uriInfo) throws JsonProcessingException {
        final HelmRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();

        String baseUri = uriInfo.getBaseUri().toString();
        if (baseUri.endsWith("/")) {
            baseUri = baseUri.substring(0, baseUri.length() - 1);
        }
        final String urlTemplate = baseUri + "/repo/%s/%s/%s.tgz";
        final Map<String, List<HelmIndexEntry>> entries = repository.getIndex(AuthContext.ofUser(user), namespace, urlTemplate);

        return mapper.writeValueAsString(new HelmIndex(
                "v1",
                entries,
                Instant.now()
        ));
    }

    @GET
    @Path("/repo/{namespace:.*}/{digest}/{filename}.tgz")
    public Response getChart(final @PathParam("repositoryId") String repositoryId,
                             final @PathParam("namespace") String namespace,
                             final @PathParam("digest") String digest,
                             final @PathParam("filename") String filename) {
        final HelmRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();

        final String qualifiedDigestString = "sha256:%s".formatted(digest);
        final StreamHandle handle = repository.getBlob(AuthContext.ofUser(user), namespace, qualifiedDigestString);
        if (handle == null) throw new NotFoundException("Unknown chart");

        return Response.ok()
                .header("Content-Length", handle.contentLength())
                .header("Content-Type", handle.contentType())
                .header(OCIRepositoryEndpoint.HEADER_CONTENT_DIGEST, qualifiedDigestString)
                .header("Content-Disposition", "attachment; filename=\"%s.tgz\"".formatted(filename))
                .entity(handle.stream())
                .build();
    }

}
