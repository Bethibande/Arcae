package com.bethibande.repository.web.repositories.oci;

import com.bethibande.repository.jpa.artifact.Artifact;
import com.bethibande.repository.jpa.artifact.ArtifactVersion;
import com.bethibande.repository.jpa.files.OCISubject;
import com.bethibande.repository.jpa.repository.PackageManager;
import com.bethibande.repository.jpa.repository.RepositoryManager;
import com.bethibande.repository.jpa.security.AccessToken;
import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.repository.StreamHandle;
import com.bethibande.repository.repository.backend.MultipartUploadStatus;
import com.bethibande.repository.repository.oci.*;
import com.bethibande.repository.repository.security.AuthContext;
import com.bethibande.repository.security.BearerTokenIdentityProvider;
import com.bethibande.repository.web.AuthenticatedUser;
import com.bethibande.repository.web.exception.RangeNotSatisfiableException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.quarkiverse.bucket4j.runtime.RateLimited;
import io.quarkiverse.bucket4j.runtime.resolver.IpResolver;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.TransactionSemantics;
import io.quarkus.security.UnauthorizedException;
import jakarta.inject.Inject;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Path("/repositories/oci/{repositoryId}")
public class OCIRepositoryEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(OCIRepositoryEndpoint.class);

    // Min length of 5 MB due to limitations of S3 multipart uploads
    public static final long MIN_CHUNK_LENGTH = 5_242_880;

    public static final String HEADER_CONTENT_DIGEST = "Docker-Content-Digest";
    public static final String HEADER_MIN_CHUNK_LENGTH = "OCI-Chunk-Min-Length";
    public static final String HEADER_OCI_SUBJECT = "OCI-Subject";
    public static final String HEADER_OCI_FILTERS_APPLIED = "OCI-Filters-Applied";

    @Inject
    protected RepositoryManager repositoryManager;

    @Inject
    protected AuthenticatedUser authenticatedUser;

    @Inject
    protected ObjectMapper mapper;

    private final PackageManager packageManager;

    public OCIRepositoryEndpoint() {
        this(PackageManager.OCI);
    }

    protected OCIRepositoryEndpoint(final PackageManager packageManager) {
        this.packageManager = packageManager;
    }

    protected OCIRepository repositoryOrThrow(final String repositoryId) {
        return QuarkusTransaction.runner(TransactionSemantics.JOIN_EXISTING).call(() -> {
            final OCIRepository repository = repositoryManager.findRepository(repositoryId, this.packageManager);
            if (repository == null) throw new NotFoundException(
                    Response.status(Response.Status.NOT_FOUND)
                            .entity(OCIError.of(OCIErrorCodes.NAME_UNKNOWN, "Unknown repository", "The specified repository does not exist"))
                            .build()
            );
            return repository;
        });
    }

    @GET
    @Path("/v2/auth")
    @Transactional
    // We need to use the repositoryId parameter here, otherwise the OpenAPI generator will complain...
    public Response authenticate(final @PathParam("repositoryId") String repositoryId) {
        final User user = authenticatedUser.getSelf();

        LOGGER.debug("Login for repository {} and user: {}", repositoryId, user != null ? user.name : "anonymous");

        if (user == null) {
            return createTokenResponse(
                    BearerTokenIdentityProvider.ANONYMOUS_TOKEN,
                    Duration.ofHours(1),
                    Instant.now()
            );
        }

        final Instant now = Instant.now();
        final AccessToken accessToken = authenticatedUser.getAccessToken();
        final Duration duration = accessToken.expiresAfter == null
                ? Duration.ofDays(7)
                : Duration.between(now, accessToken.expiresAfter);

        return createTokenResponse(accessToken.token, duration, now);
    }

    protected Response createTokenResponse(final String token, final Duration duration, final Instant now) {
        return Response.ok()
                .entity(Map.of(
                        "token", token,
                        "access_token", token,
                        "expires_in", duration.toSeconds(),
                        "issued_at", now.toString()
                ))
                .build();
    }

    @GET
    @Path("/v2")
    @Transactional
    public Response get(final @PathParam("repositoryId") String repositoryId) {
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();

        if (!repository.canView(AuthContext.ofUser(user))) {
            if (user == null) throw new UnauthorizedException();
            throw new ForbiddenException("Unauthorized");
        }

        return Response.ok().build();
    }

    @HEAD
    @Path("/v2/{namespace: .*}/blobs/{digest}")
    public Response headBlob(final @PathParam("repositoryId") String repositoryId,
                             final @PathParam("namespace") String namespace,
                             final @PathParam("digest") String digest) {
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();

        final OCIContentInfo info = repository.getBlobInfo(AuthContext.ofUser(user), namespace, OCIDigestHelper.extractDigest(digest));
        if (info == null) throw new NotFoundException("Unknown blob");

        return Response.ok()
                .header(HttpHeaders.CONTENT_LENGTH, info.size())
                .header(HttpHeaders.CONTENT_TYPE, info.contentType())
                .header(HEADER_CONTENT_DIGEST, info.digest())
                .build();
    }

    @HEAD
    @Path("/v2/{namespace: .*}/manifests/{reference}")
    public Response headManifest(final @PathParam("repositoryId") String repositoryId,
                                 final @PathParam("namespace") String namespace,
                                 final @PathParam("reference") String reference) {
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();

        final OCIContentInfo info = repository.getManifestInfo(AuthContext.ofUser(user), namespace, OCIDigestHelper.referenceOrDigest(reference));
        if (info == null) throw new NotFoundException("Unknown manifest");

        return Response.ok()
                .header(HttpHeaders.CONTENT_LENGTH, info.size())
                .header(HttpHeaders.CONTENT_TYPE, info.contentType())
                .header(HEADER_CONTENT_DIGEST, info.digest())
                .build();
    }

    protected Pair<Long, Long> parseContentRange(final String rangeHeader) {
        final String[] parts = rangeHeader.substring(6).split("-");
        final long offset = Long.parseLong(parts[0]);
        final long length = parts.length == 2
                ? Long.parseLong(parts[1]) - offset + 1
                : Long.MAX_VALUE;

        return Pair.of(offset, length);
    }

    protected StreamHandle getBlob(final OCIRepository repository,
                                   final User user,
                                   final String namespace,
                                   final String digest,
                                   final Pair<Long, Long> range) {
        final AuthContext auth = AuthContext.ofUser(user);
        if (range != null) {
            return repository.getBlob(auth, namespace, digest, range.getLeft(), range.getRight());
        }
        return repository.getBlob(auth, namespace, digest);
    }

    @GET
    @Path("/v2/{namespace: .*}/blobs/{digest}")
    @RateLimited(bucket = "oci-blobs", identityResolver = IpResolver.class)
    public Response getBlob(final @PathParam("repositoryId") String repositoryId,
                            final @PathParam("namespace") String namespace,
                            final @PathParam("digest") String digest,
                            final @HeaderParam(HttpHeaders.RANGE) String rangeHeader) {
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();

        final String actualDigest = OCIDigestHelper.extractDigest(digest);
        final Pair<Long, Long> range = rangeHeader != null ? parseContentRange(rangeHeader) : null;
        final StreamHandle handle = getBlob(repository, user, namespace, actualDigest, range);
        if (handle == null) throw new NotFoundException("Unknown blob");

        final String contentRange = range != null
                ? "%d-%d".formatted(range.getLeft(), range.getRight() + range.getLeft() - 1)
                : "0-%d".formatted(handle.contentLength() - 1);

        return Response.ok(handle.stream())
                .header(HttpHeaders.CONTENT_TYPE, handle.contentType())
                .header(HttpHeaders.CONTENT_LENGTH, handle.contentLength())
                .header(HEADER_CONTENT_DIGEST, actualDigest)
                .header(HttpHeaders.CONTENT_RANGE, contentRange)
                .build();
    }

    @GET
    @Path("/v2/{namespace: .*}/manifests/{reference}")
    public Response getManifest(final @PathParam("repositoryId") String repositoryId,
                                final @PathParam("namespace") String namespace,
                                final @PathParam("reference") String reference) {
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();

        final OCIStreamHandle handle = repository.getManifest(AuthContext.ofUser(user), namespace, OCIDigestHelper.referenceOrDigest(reference));
        if (handle == null) throw new NotFoundException("Unknown blob");

        final StreamHandle streamHandle = handle.streamHandle();

        return Response.ok(streamHandle.stream())
                .header(HttpHeaders.CONTENT_TYPE, streamHandle.contentType())
                .header(HttpHeaders.CONTENT_LENGTH, streamHandle.contentLength())
                .header(HEADER_CONTENT_DIGEST, handle.digest())
                .build();
    }

    protected Response badDigestResponse() {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(OCIError.of(OCIErrorCodes.DIGEST_INVALID, "Invalid digest", "The specified digest is invalid"))
                .build();
    }

    @POST
    @Path("/v2/{namespace: .*}/blobs/uploads")
    public Response createUpload(final @PathParam("repositoryId") String repositoryId,
                                 final @PathParam("namespace") String namespace,
                                 final @QueryParam("digest") String digest,
                                 final @HeaderParam(HttpHeaders.CONTENT_LENGTH) Long contentLength,
                                 final InputStream content) {
        // TODO: Mounts
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();
        final AuthContext auth = AuthContext.ofUser(user);
        final StreamHandle handle = new StreamHandle(
                content,
                ContentType.APPLICATION_OCTET_STREAM.getMimeType(),
                contentLength
        );

        if (digest != null) {
            if (!OCIDigestHelper.isDigest(digest)) return badDigestResponse();

            final String actualDigest = OCIDigestHelper.extractDigest(digest);
            repository.uploadBlob(auth, namespace, actualDigest, handle);

            final String url = "/v2/%s/blobs/%s".formatted(namespace, actualDigest);
            return Response.created(URI.create(url))
                    .header(HEADER_CONTENT_DIGEST, actualDigest)
                    .build();
        } else {
            final UploadSessionHandle session = repository.startUploadSession(auth, namespace);

            int nextPart = 1;
            String range = null;
            if (contentLength != null && contentLength > 0) {
                repository.uploadPart(auth, namespace, session, 1, handle);
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
    @Path("/v2/{namespace: .*}/blobs/uploads/{sessionId}")
    public Response uploadChunk(final @PathParam("repositoryId") String repositoryId,
                                final @PathParam("namespace") String namespace,
                                final @PathParam("sessionId") UUID sessionId,
                                final @QueryParam("uploadId") String uploadId,
                                final @QueryParam("part") int partNumber,
                                final @HeaderParam(HttpHeaders.CONTENT_LENGTH) long contentLength,
                                final @HeaderParam(HttpHeaders.CONTENT_RANGE) String contentRange,
                                final InputStream content) {
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();
        final AuthContext auth = AuthContext.ofUser(user);

        final UploadSessionHandle sessionHandle = new UploadSessionHandle(sessionId, uploadId);

        // Doing this for each request is suboptimal, find a better way to track this
        final MultipartUploadStatus status = repository.getUploadStatus(auth, namespace, sessionHandle);
        if (status.partNumber() + 1 != partNumber) throw new RangeNotSatisfiableException();
        if (contentRange != null) {
            final String[] parts = contentRange.split("-");
            final long offset = Long.parseLong(parts[0]);

            if (offset != status.offset()) throw new RangeNotSatisfiableException();
        }

        final StreamHandle streamHandle = new StreamHandle(
                content,
                ContentType.APPLICATION_OCTET_STREAM.getMimeType(),
                contentLength
        );

        repository.uploadPart(auth, namespace, sessionHandle, partNumber, streamHandle);

        final String url = "/v2/%s/blobs/uploads/%s?uploadId=%s&part=%d".formatted(namespace, sessionId, uploadId, partNumber + 1);

        return Response.accepted()
                .header(HEADER_MIN_CHUNK_LENGTH, MIN_CHUNK_LENGTH)
                .header("Range", "0-%d".formatted(status.offset() + contentLength - 1))
                .location(URI.create(url))
                .build();
    }

    @PUT
    @Path("/v2/{namespace: .*}/blobs/uploads/{sessionId}")
    public Response completeUpload(final @PathParam("repositoryId") String repositoryId,
                                   final @PathParam("namespace") String namespace,
                                   final @PathParam("sessionId") UUID sessionId,
                                   final @QueryParam("digest") String digest,
                                   final @QueryParam("uploadId") String uploadId,
                                   final @QueryParam("part") int partNumber,
                                   final @HeaderParam(HttpHeaders.CONTENT_LENGTH) Long contentLength,
                                   final InputStream content) {
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();
        final AuthContext auth = AuthContext.ofUser(user);
        final UploadSessionHandle handle = new UploadSessionHandle(sessionId, uploadId);

        if (contentLength != null && contentLength > 0) {
            final StreamHandle streamHandle = new StreamHandle(content, ContentType.APPLICATION_OCTET_STREAM.getMimeType(), contentLength);
            repository.uploadPart(auth, namespace, handle, partNumber, streamHandle);
        }

        if (!OCIDigestHelper.isDigest(digest)) return badDigestResponse();

        final String actualDigest = OCIDigestHelper.extractDigest(digest);
        repository.completeUploadSession(auth, namespace, actualDigest, handle);

        final String url = "/v2/%s/blobs/%s".formatted(namespace, actualDigest);
        return Response.created(URI.create(url))
                .header(HEADER_CONTENT_DIGEST, actualDigest)
                .build();
    }

    @GET
    @Path("/v2/{namespace: .*}/blobs/uploads/{sessionId}")
    public Response getUploadStatus(final @PathParam("repositoryId") String repositoryId,
                                    final @PathParam("namespace") String namespace,
                                    final @PathParam("sessionId") UUID sessionId,
                                    final @QueryParam("uploadId") String uploadId) {
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();
        final AuthContext auth = AuthContext.ofUser(user);

        final UploadSessionHandle handle = new UploadSessionHandle(sessionId, uploadId);
        final MultipartUploadStatus status = repository.getUploadStatus(auth, namespace, handle);

        final String url = "/v2/%s/blobs/uploads/%s?uploadId=%s&part=%d".formatted(
                namespace,
                sessionId,
                uploadId,
                status.partNumber() + 1
        );

        return Response.noContent()
                .location(URI.create(url))
                .header(HttpHeaders.RANGE, "0-%d".formatted(status.offset() - 1))
                .build();
    }

    @PUT
    @Transactional
    @Path("/v2/{namespace: .*}/manifests/{reference}")
    public Response putManifest(final @PathParam("repositoryId") String repositoryId,
                                final @PathParam("namespace") String namespace,
                                final @PathParam("reference") String reference,
                                final @HeaderParam(HttpHeaders.CONTENT_TYPE) String contentType,
                                final @HeaderParam(HttpHeaders.CONTENT_LENGTH) long contentLength,
                                final InputStream data) {
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();

        final String actualReference = OCIDigestHelper.referenceOrDigest(reference);
        final OCIPutManifestResult result = repository.putManifest(
                AuthContext.ofUser(user),
                namespace,
                actualReference,
                new StreamHandle(data, contentType, contentLength),
                false
        );

        final String url = "/v2/%s/manifests/%s".formatted(namespace, actualReference);
        return Response.created(URI.create(url))
                .header(HEADER_OCI_SUBJECT, result.subject() != null ? result.subject().subjectDigest : null)
                .header(HEADER_CONTENT_DIGEST, result.digest())
                .build();
    }

    public record TagList(String name, List<String> tags) {
    }

    @GET
    @Transactional
    @Path("/v2/{namespace: .*}/tags/list")
    public TagList listTags(final @PathParam("repositoryId") String repositoryId,
                            final @PathParam("namespace") String namespace,
                            final @QueryParam("n") Integer n,
                            final @QueryParam("last") String last) {
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();

        final Artifact artifact = repository.getArtifact(AuthContext.ofUser(user), namespace);
        final List<ArtifactVersion> versions = ArtifactVersion.list("artifact = ?1 order by version asc", artifact);

        if (n != null && n == 0) return new TagList(namespace, List.of());

        final List<String> result = new ArrayList<>();
        final int lastIndex = last != null
                ? versions.indexOf(versions.stream().filter(v -> Objects.equals(v.version, last)).findFirst().orElse(null))
                : -1;

        final int startIndex = lastIndex == -1 ? 0 : lastIndex + 1;
        for (int i = startIndex; i < versions.size() && (n == null || i < n); i++) {
            result.add(versions.get(i).version);
        }

        return new TagList(namespace, result);
    }

    private JsonNode buildReferrersResponse(final List<OCISubject> subjects) {
        final List<ObjectNode> manifests = new ArrayList<>();
        for (int i = 0; i < subjects.size(); i++) {
            final OCISubject subject = subjects.get(i);
            ObjectNode descriptor = mapper.createObjectNode();
            descriptor.put("mediaType", subject.source.contentType);
            descriptor.put("digest", subject.sourceDigest);
            descriptor.put("size", subject.source.contentLength);
            descriptor.put("artifactType", subject.artifactType);

            // ADD THIS PART:
            if (subject.annotations != null && !subject.annotations.isEmpty()) {
                descriptor.set("annotations", mapper.valueToTree(subject.annotations));
            }

            manifests.add(descriptor);
        }

        final ObjectNode root = mapper.createObjectNode();
        root.set("schemaVersion", new IntNode(2));
        root.set("mediaType", new TextNode("application/vnd.oci.image.index.v1+json"));
        root.set("manifests", mapper.valueToTree(manifests));

        return root;
    }

    @GET
    @Transactional
    @Path("/v2/{namespace: .*}/referrers/{digest}")
    public Response getReferrers(final @PathParam("repositoryId") String repositoryId,
                                 final @PathParam("namespace") String namespace,
                                 final @PathParam("digest") String digest,
                                 final @QueryParam("artifactType") String artifactType) throws JsonProcessingException {
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();

        if (!repository.canView(AuthContext.ofUser(user))) throw new ForbiddenException("Unauthorized");

        final CriteriaBuilder builder = OCISubject.getEntityManager().getCriteriaBuilder();
        final CriteriaQuery<OCISubject> query = builder.createQuery(OCISubject.class);

        final Root<OCISubject> root = query.from(OCISubject.class);
        final List<Predicate> predicates = new ArrayList<>();
        predicates.add(root.get("namespace").equalTo(namespace));
        predicates.add(root.get("subjectDigest").equalTo(OCIDigestHelper.extractDigest(digest)));
        if (artifactType != null) predicates.add(root.get("artifactType").equalTo(artifactType));

        query.where(builder.and(predicates.toArray(Predicate[]::new)));

        final List<OCISubject> subjects = OCISubject.getEntityManager()
                .createQuery(query)
                .getResultList();

        final JsonNode node = buildReferrersResponse(subjects);
        final String json = mapper.writeValueAsString(node);
        return Response.ok(json)
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.oci.image.index.v1+json")
                .header(HEADER_OCI_FILTERS_APPLIED, artifactType != null ? "artifactType" : null)
                .build();
    }

    @DELETE
    @Transactional
    @Path("/v2/{namespace: .*}/manifests/{reference}")
    public Response deleteManifest(final @PathParam("repositoryId") String repositoryId,
                                   final @PathParam("namespace") String namespace,
                                   final @PathParam("reference") String reference) {
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();

        repository.deleteManifest(AuthContext.ofUser(user), namespace, OCIDigestHelper.referenceOrDigest(reference));
        return Response.accepted().build();
    }

    @DELETE
    @Transactional
    @Path("/v2/{namespace: .*}/blobs/{digest}")
    public Response deleteBlob(final @PathParam("repositoryId") String repositoryId,
                               final @PathParam("namespace") String namespace,
                               final @PathParam("digest") String digest) {
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();

        repository.deleteBlob(AuthContext.ofUser(user), namespace, OCIDigestHelper.extractDigest(digest));
        return Response.accepted().build();
    }

    @DELETE
    @Transactional
    @Path("/v2/{namespace: .*}/blobs/uploads/{sessionId}")
    public Response abortUpload(final @PathParam("repositoryId") String repositoryId,
                                final @PathParam("namespace") String namespace,
                                final @PathParam("sessionId") UUID sessionId,
                                final @QueryParam("uploadId") String uploadId) {
        final OCIRepository repository = repositoryOrThrow(repositoryId);
        final User user = authenticatedUser.getSelf();

        repository.abortUpload(AuthContext.ofUser(user), uploadId, namespace, sessionId);
        return Response.noContent().build();
    }
}
