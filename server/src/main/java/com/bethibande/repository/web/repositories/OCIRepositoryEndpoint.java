package com.bethibande.repository.web.repositories;

import com.bethibande.repository.jpa.repository.PackageManager;
import com.bethibande.repository.jpa.repository.RepositoryManager;
import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.repository.StreamHandle;
import com.bethibande.repository.repository.oci.OCIContentInfo;
import com.bethibande.repository.repository.oci.OCIRepository;
import com.bethibande.repository.repository.oci.OCIStreamHandle;
import com.bethibande.repository.repository.oci.UploadSessionHandle;
import com.bethibande.repository.web.AuthenticatedUser;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;

import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

@Path("/repositories/oci/{repositoryId}/v2")
public class OCIRepositoryEndpoint {

    // Min length of 5 MB due to limitations of S3 multipart uploads
    public static final long MIN_CHUNK_LENGTH = 5_242_880;

    public static final String HEADER_CONTENT_DIGEST = "Docker-Content-Digest";
    public static final String HEADER_MIN_CHUNK_LENGTH = "OCI-Chunk-Min-Length";

    private final RepositoryManager repositoryManager;
    private final AuthenticatedUser authenticatedUser;

    @Inject
    public OCIRepositoryEndpoint(final RepositoryManager repositoryManager, final AuthenticatedUser authenticatedUser) {
        this.repositoryManager = repositoryManager;
        this.authenticatedUser = authenticatedUser;
    }

    protected OCIRepository repositoryOrThrow(final String repositoryId) {
        final OCIRepository repository = repositoryManager.findRepository(repositoryId, PackageManager.OCI);
        if (repository == null) throw new NotFoundException("Unknown repository");
        return repository;
    }

    @GET
    @Transactional
    public Response get(final @PathParam("repositoryId") String repositoryId) {
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();

        if (!repository.canView(user)) throw new ForbiddenException("Unauthorized");


        return Response.ok().build();
    }

    @HEAD
    @Transactional
    @Path("/{namespace: .*}/blobs/{digest}")
    public Response headBlob(final @PathParam("repositoryId") String repositoryId,
                             final @PathParam("namespace") String namespace,
                             final @PathParam("digest") String digest) {
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();

        final OCIContentInfo info = repository.getBlobInfo(user, namespace, digest);
        if (info == null) throw new NotFoundException("Unknown blob");

        return Response.ok()
                .header(HttpHeaders.CONTENT_LENGTH, info.size())
                .header(HEADER_CONTENT_DIGEST, info.digest())
                .build();
    }

    @HEAD
    @Transactional
    @Path("/{namespace: .*}/manifests/{reference}")
    public Response headManifest(final @PathParam("repositoryId") String repositoryId,
                                 final @PathParam("namespace") String namespace,
                                 final @PathParam("reference") String reference) {
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();

        final OCIContentInfo info = repository.getManifestInfo(user, namespace, reference);
        if (info == null) throw new NotFoundException("Unknown manifest");

        return Response.ok()
                .header(HttpHeaders.CONTENT_LENGTH, info.size())
                .header(HEADER_CONTENT_DIGEST, info.digest())
                .build();
    }

    @GET
    @Transactional
    @Path("/{namespace: .*}/blobs/{digest}")
    public Response getBlob(final @PathParam("repositoryId") String repositoryId,
                            final @PathParam("namespace") String namespace,
                            final @PathParam("digest") String digest) {
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();

        final StreamHandle handle = repository.getBlob(user, namespace, digest);
        if (handle == null) throw new NotFoundException("Unknown blob");

        return Response.ok(handle.stream())
                .header(HttpHeaders.CONTENT_TYPE, handle.contentType())
                .header(HttpHeaders.CONTENT_LENGTH, handle.contentLength())
                .header(HEADER_CONTENT_DIGEST, digest)
                .build();
    }

    @GET
    @Transactional
    @Path("/{namespace: .*}/manifests/{reference}")
    public Response getManifest(final @PathParam("repositoryId") String repositoryId,
                                final @PathParam("namespace") String namespace,
                                final @PathParam("reference") String reference) {
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();

        final OCIStreamHandle handle = repository.getManifest(user, namespace, reference);
        if (handle == null) throw new NotFoundException("Unknown blob");

        final StreamHandle streamHandle = handle.streamHandle();

        return Response.ok(streamHandle.stream())
                .header(HttpHeaders.CONTENT_TYPE, streamHandle.contentType())
                .header(HttpHeaders.CONTENT_LENGTH, streamHandle.contentLength())
                .header(HEADER_CONTENT_DIGEST, handle.digest())
                .build();
    }

    @POST
    @Transactional
    @Path("/{namespace: .*}/blobs/uploads")
    public Response createUpload(final @PathParam("repositoryId") String repositoryId,
                                 final @PathParam("namespace") String namespace,
                                 final @QueryParam("digest") String digest,
                                 final @HeaderParam(HttpHeaders.CONTENT_LENGTH) Long contentLength,
                                 final InputStream content) {
        // TODO: Mounts
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();
        final StreamHandle handle = new StreamHandle(
                content,
                ContentType.APPLICATION_OCTET_STREAM.getMimeType(),
                contentLength
        );

        if (digest != null) {
            repository.uploadBlob(user, namespace, digest, handle);

            final String url = "/v2/%s/blobs/%s".formatted(namespace, digest);
            return Response.created(URI.create(url))
                    .build();
        } else {
            final UploadSessionHandle session = repository.startUploadSession(user, namespace);

            int nextPart = 1;
            String range = null;
            if (contentLength != null && contentLength > 0) {
                repository.uploadPart(user, namespace, session, 1, handle);
                range = "0-%d".formatted(contentLength - 1);
                nextPart++;
            }

            final String url = "/v2/%s/blobs/uploads/%s?uploadId=%s&part=%d".formatted(namespace, session.sessionId(), session.uploadId(), nextPart);
            return Response.accepted()
                    .location(URI.create(url))
                    .header(HEADER_MIN_CHUNK_LENGTH, MIN_CHUNK_LENGTH)
                    .header("Range", range)
                    .build();
        }
    }

    @PATCH
    @Transactional
    @Path("/{namespace: .*}/blobs/uploads/{sessionId}")
    public Response uploadChunk(final @PathParam("repositoryId") String repositoryId,
                                final @QueryParam("namespace") String namespace,
                                final @PathParam("sessionId") UUID sessionId,
                                final @QueryParam("uploadId") String uploadId,
                                final @QueryParam("part") int partNumber,
                                final @HeaderParam(HttpHeaders.CONTENT_LENGTH) long contentLength,
                                final @HeaderParam(HttpHeaders.CONTENT_RANGE) String contentRange,
                                final InputStream content) {
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();

        // TODO: Check part order
        final UploadSessionHandle sessionHandle = new UploadSessionHandle(sessionId, uploadId);
        final StreamHandle streamHandle = new StreamHandle(
                content,
                ContentType.APPLICATION_OCTET_STREAM.getMimeType(),
                contentLength
        );

        repository.uploadPart(user, namespace, sessionHandle, partNumber, streamHandle);

        final long rangeEnd = Long.parseLong(contentRange.substring(contentRange.lastIndexOf("-") + 1));
        final String url = "/v2/%s/blobs/uploads/%s?uploadId=%s&part=%d".formatted(namespace, sessionId, uploadId, partNumber + 1);

        return Response.accepted()
                .header(HEADER_MIN_CHUNK_LENGTH, MIN_CHUNK_LENGTH)
                .header("Range", "0-%d".formatted(rangeEnd))
                .location(URI.create(url))
                .build();
    }

    @PUT
    @Transactional
    @Path("/{namespace: .*}/blobs/uploads/{sessionId}")
    public Response completeUpload(final @PathParam("repositoryId") String repositoryId,
                                   final @QueryParam("namespace") String namespace,
                                   final @PathParam("sessionId") UUID sessionId,
                                   final @QueryParam("digest") String digest,
                                   final @QueryParam("uploadId") String uploadId,
                                   final @QueryParam("part") int partNumber,
                                   final @HeaderParam(HttpHeaders.CONTENT_LENGTH) Long contentLength,
                                   final InputStream content) {
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();
        final UploadSessionHandle handle = new UploadSessionHandle(sessionId, uploadId);

        if (contentLength != null && contentLength > 0) {
            final StreamHandle streamHandle = new StreamHandle(content, ContentType.APPLICATION_OCTET_STREAM.getMimeType(), contentLength);
            repository.uploadPart(user, namespace, handle, partNumber, streamHandle);
        }

        repository.completeUploadSession(user, namespace, digest, handle);

        final String url = "/v2/%s/blobs/%s".formatted(namespace, digest);
        return Response.created(URI.create(url))
                .build();
    }

    @PUT
    @Transactional
    @Path("/{namespace: .*}/manifests/{reference}")
    public Response putManifest(final @PathParam("repositoryId") String repositoryId,
                                final @PathParam("namespace") String namespace,
                                final @PathParam("reference") String reference,
                                final @HeaderParam(HttpHeaders.CONTENT_TYPE) String contentType,
                                final @HeaderParam(HttpHeaders.CONTENT_LENGTH) long contentLength,
                                final InputStream data) {
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();

        repository.putManifest(user, namespace, reference, new StreamHandle(data, contentType, contentLength));
        final String url = "/v2/%s/manifests/%s".formatted(namespace, reference);
        return Response.created(URI.create(url))
                .build();
    }

    @DELETE
    @Transactional
    @Path("/{namespace: .*}/blobs/uploads/{sessionId}")
    public Response abortUpload(final @PathParam("repositoryId") String repositoryId,
                                final @QueryParam("namespace") String namespace,
                                final @PathParam("sessionId") UUID sessionId,
                                final @QueryParam("uploadId") String uploadId) {
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();

        repository.abortUpload(user, uploadId, namespace, sessionId);
        return Response.noContent().build();
    }
}
