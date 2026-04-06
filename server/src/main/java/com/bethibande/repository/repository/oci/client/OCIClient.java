package com.bethibande.repository.repository.oci.client;

import com.bethibande.repository.repository.StreamHandle;
import com.bethibande.repository.repository.maven.MirrorConnectionSettings;
import com.bethibande.repository.repository.mirror.MirrorAuthType;
import com.bethibande.repository.repository.oci.OCIContentInfo;
import com.bethibande.repository.repository.oci.OCIStreamHandle;
import com.bethibande.repository.util.HttpClientUtil;
import com.bethibande.repository.web.repositories.oci.OCIRepositoryEndpoint;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple OCI client implementation used for retrieving data from remote registries.
 * This implementation also ensures requests are automatically authenticated and retried if required.
 * Access tokens retrieved from remote registries are cached internally for 10 minutes.
 *
 * @see OCIClient#headBlob(String, String)
 * @see OCIClient#getBlob(String, String)
 * @see OCIClient#headManifest(String, String)
 * @see OCIClient#getManifest(String, String)
 */
public class OCIClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(OCIClient.class);

    private static final Cache<String, String> TOKEN_CACHE = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    private static final Pattern AUTHENTICATE_HEADER_PATTERN = Pattern.compile("(\\w+)=\"([^\"]*)\"");

    private final HttpClient client;

    private final MirrorConnectionSettings connection;

    public OCIClient(final HttpClient client, final MirrorConnectionSettings connection) {
        this.client = client;
        this.connection = connection;
    }

    private HttpRequest buildRequest(final OCIRequest<?> request) {
        final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(this.connection.url().replaceAll("/+$", "") + "/v2/" + request.namespace() + request.subpath()))
                .method(request.method(), HttpRequest.BodyPublishers.noBody());

        if (request.additionalHeaders() != null) {
            for (Map.Entry<String, List<String>> entry : request.additionalHeaders().entrySet()) {
                for (String value : entry.getValue()) {
                    requestBuilder.header(entry.getKey(), value);
                }
            }
        }

        final String authCacheKey = tokenCacheKey(request.namespace());
        final String token = TOKEN_CACHE.getIfPresent(authCacheKey);
        if (token != null) requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        return requestBuilder.build();
    }

    private String tokenCacheKey(final String namespace) {
        return this.connection.url() + ":" + namespace;
    }

    public static Map<String, String> parseAuthenticateHeaderParams(String header) {
        final Map<String, String> params = new HashMap<>();
        final Matcher matcher = AUTHENTICATE_HEADER_PATTERN.matcher(header);
        while (matcher.find()) {
            params.put(matcher.group(1), matcher.group(2));
        }
        return params;
    }

    public String buildAuthHeader() {
        final MirrorAuthType type = this.connection.authType();
        if (type == MirrorAuthType.NONE) return null;
        if (type == MirrorAuthType.BASIC && this.connection.username() != null && this.connection.password() != null) {
            final String value = Base64.getEncoder().encodeToString("%s:%s".formatted(this.connection.username(), this.connection.password()).getBytes());
            return "Basic " + value;
        }
        if (type == MirrorAuthType.BEARER && this.connection.password() != null) {
            return "Bearer " + this.connection.password();
        }
        return null;
    }

    protected boolean authenticate(final String namespace, final String authHeader) throws IOException, InterruptedException {
        final String cacheKey = tokenCacheKey(namespace);
        final String existing = TOKEN_CACHE.getIfPresent(cacheKey);
        if (existing != null) return true;

        final Map<String, String> params = parseAuthenticateHeaderParams(authHeader);
        final String auth = buildAuthHeader();

        final String realm = params.get("realm");
        final String separator = realm.contains("?") ? "&" : "?";
        final String url = realm + separator + "service=" + params.get("service") + "&scope=repository:" + namespace + ":pull";

        final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(url));
        if (auth != null) requestBuilder.header(HttpHeaders.AUTHORIZATION, auth);

        final HttpRequest request = requestBuilder.build();

        final HttpResponse<Map<String, String>> response = this.client.send(request, HttpClientUtil.jsonBodyHandler(new TypeReference<>() {
        }));
        if (response.statusCode() != 200) {
            return false;
        }

        final Map<String, String> result = response.body();
        if (result == null) return false;
        final String token = result.get("token") != null
                ? result.get("token")
                : result.get("access_token");

        if (token != null) {
            TOKEN_CACHE.put(cacheKey, token);
        }

        return token != null;
    }

    private boolean tryAuthenticate(final HttpResponse<?> response, final String namespace) throws IOException, InterruptedException {
        final String authenticateHeader = response.headers()
                .firstValue(HttpHeaders.WWW_AUTHENTICATE)
                .orElse(null);
        if (authenticateHeader == null) return false;

        return authenticate(namespace, authenticateHeader);
    }

    private <T> HttpResponse<T> send(final OCIRequest<T> request) throws IOException {
        try {
            final HttpRequest actualRequest = buildRequest(request);

            final HttpResponse<T> response = this.client.send(actualRequest, request.bodyHandler());
            if (response.statusCode() == 401) {
                // Return if sent request already contained the authorization header and still failed
                if (actualRequest.headers().firstValue(HttpHeaders.AUTHORIZATION).isPresent()) return response;

                if (tryAuthenticate(response, request.namespace())) {
                    return send(request); // Re-try
                } else {
                    LOGGER.warn("Failed to authenticate for path {} on repository {} with method {}", request.subpath(), request.namespace(), request.method());
                }
            }

            return response;
        } catch (final InterruptedException ex) {
            throw new IOException("Interrupted while sending request", ex);
        }
    }

    private OCIContentInfo extractInfo(final HttpResponse<?> response) {
        final java.net.http.HttpHeaders headers = response.headers();
        final String contentType = headers.firstValue(HttpHeaders.CONTENT_TYPE).orElseThrow();
        final long contentLength = headers.firstValueAsLong(HttpHeaders.CONTENT_LENGTH).orElseThrow();
        final String contentDigest = headers.firstValue(OCIRepositoryEndpoint.HEADER_CONTENT_DIGEST).orElse(null);

        return new OCIContentInfo(contentDigest, contentLength, contentType);
    }

    private OCIContentInfo head(final String namespace, final String subpath) throws IOException {
        final HttpResponse<Void> response = send(new OCIRequest<>(
                namespace,
                subpath,
                "HEAD",
                HttpResponse.BodyHandlers.discarding(),
                null
        ));

        if (response.statusCode() == 404) return null;
        if (response.statusCode() != 200) {
            throw new IOException("Failed to head object: %d - %s".formatted(response.statusCode(), response.body()));
        }

        return extractInfo(response);
    }

    private OCIStreamHandle get(final String namespace,
                                final String subpath,
                                final Map<String, List<String>> additionalHeaders) throws IOException {
        final HttpResponse<InputStream> response = send(new OCIRequest<>(
                namespace,
                subpath,
                "GET",
                HttpResponse.BodyHandlers.ofInputStream(),
                additionalHeaders
        ));

        if (response.statusCode() == 404) return null;
        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch object: %d - %s".formatted(response.statusCode(), response.body()));
        }

        final OCIContentInfo info = extractInfo(response);

        return new OCIStreamHandle(
                new StreamHandle(response.body(), info.contentType(), info.size()),
                info.digest()
        );
    }

    public OCIContentInfo headBlob(final String namespace, final String digest) throws IOException {
        return head(namespace, "/blobs/" + digest);
    }

    public OCIContentInfo headManifest(final String namespace, final String reference) throws IOException {
        return head(namespace, "/manifests/" + reference);
    }

    public OCIStreamHandle getBlob(final String namespace, final String digest) throws IOException {
        return get(namespace, "/blobs/" + digest, null);
    }

    public OCIStreamHandle getBlobRange(final String namespace,
                                        final String digest,
                                        final long offset,
                                        final long end) throws IOException {
        return get(namespace, "/blobs/" + digest, Map.of("Range", List.of("bytes=%d-%d".formatted(offset, end))));
    }

    public OCIStreamHandle getManifest(final String namespace, final String reference) throws IOException {
        return get(namespace, "/manifests/" + reference, null);
    }
}
