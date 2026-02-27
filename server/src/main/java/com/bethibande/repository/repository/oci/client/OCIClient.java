package com.bethibande.repository.repository.oci.client;

import com.bethibande.repository.repository.StreamHandle;
import com.bethibande.repository.repository.maven.MirrorConnectionSettings;
import com.bethibande.repository.repository.mirror.MirrorAuthType;
import com.bethibande.repository.repository.oci.OCIContentInfo;
import com.bethibande.repository.repository.oci.OCIStreamHandle;
import com.bethibande.repository.web.repositories.OCIRepositoryEndpoint;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO: Clean this mess up
public class OCIClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(OCIClient.class);

    private static final Pattern AUTHENTICATE_HEADER_PATTERN = Pattern.compile("(\\w+)=\"([^\"]*)\"");

    private final OCIWebClient client;
    private final OCITokenCache tokenCache;

    private final MirrorConnectionSettings connection;

    public OCIClient(final HttpClient client, final MirrorConnectionSettings connection, final OCITokenCache tokenCache) {
        this.client = new OCIWebClient(client, connection);
        this.tokenCache = tokenCache;
        this.connection = connection;
    }

    public static Map<String, String> parse(String header) {
        final Map<String, String> params = new HashMap<>();
        final Matcher matcher = AUTHENTICATE_HEADER_PATTERN.matcher(header);
        while (matcher.find()) {
            params.put(matcher.group(1), matcher.group(2));
        }
        return params;
    }

    protected String authenticate(final String namespace, final String authHeader) throws IOException {
        final String existing = this.tokenCache.get(this.connection, namespace);
        if (existing != null) return existing;

        final Map<String, String> params = parse(authHeader);
        final String auth = this.client.buildAuthHeader();

        final String realm = params.get("realm");
        final String separator = realm.contains("?") ? "&" : "?";
        final String url = realm + separator + "service=" + params.get("service") + "&scope=repository:" + namespace + ":pull";

        final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(url));
        if (auth != null) requestBuilder.header(HttpHeaders.AUTHORIZATION, auth);

        final HttpRequest request = requestBuilder.build();

        final HttpResponse<Map<String, String>> response = this.client.send(request, new TypeReference<>() {});
        if (response.statusCode() != 200) {
            return null;
        }

        final Map<String, String> result = response.body();
        if (result == null) return null;
        final String token = result.get("token") != null
                ? result.get("token")
                : result.get("access_token");

        if (token != null) {
            this.tokenCache.put(this.connection, namespace, token);
        }

        return token;
    }

    protected OCIContentInfo head(final String namespace, final String path, final Map<String, String> additionalHeaders) throws IOException {
        final HttpRequest.Builder requestBuilder = this.client.head("/v2/" + namespace + path);
        if (additionalHeaders != null) additionalHeaders.forEach(requestBuilder::header);

        final HttpRequest request = requestBuilder.build();
        final HttpResponse<Void> response = this.client.sendVoid(request);

        if (response.statusCode() == 404) return null;
        if (response.statusCode() == 401) {
            final String authRequest = response.headers().firstValue(HttpHeaders.WWW_AUTHENTICATE).orElseThrow();
            final String token = authenticate(namespace, authRequest);

            if (token != null) {
                return head(namespace, path, Map.of(HttpHeaders.AUTHORIZATION, "Bearer " + token));
            } else {
                LOGGER.warn("Failed to authenticate for path HEAD {} on repository {}", path, namespace);
                return null;
            }
        }

        final long contentLength = response.headers()
                .firstValueAsLong("Content-Length")
                .orElseThrow();

        final String contentType = response.headers()
                .firstValue("Content-Type")
                .orElseThrow();

        final String digest = response.headers()
                .firstValue(OCIRepositoryEndpoint.HEADER_CONTENT_DIGEST)
                .orElse(null);

        return new OCIContentInfo(digest, contentLength, contentType);
    }

    protected OCIStreamHandle get(final String namespace,
                                  final String path,
                                  final Map<String, String> additionalHeaders) throws IOException {
        final HttpRequest.Builder requestBuilder = this.client.get("/v2/" + namespace + path);
        if (additionalHeaders != null) {
            additionalHeaders.forEach(requestBuilder::header);
        }

        final HttpRequest request = requestBuilder.build();
        final HttpResponse<InputStream> response = this.client.send(request);

        if (response.statusCode() == 404) return null;
        if (response.statusCode() == 401) {
            final String authRequest = response.headers().firstValue(HttpHeaders.WWW_AUTHENTICATE).orElseThrow();
            final String token = authenticate(namespace, authRequest);

            if (token != null) {
                final Map<String, String> headers = new HashMap<>();
                if (additionalHeaders != null) headers.putAll(additionalHeaders);
                headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + token);

                return get(namespace, path, headers);
            } else {
                LOGGER.warn("Failed to authenticate for path GET {} on repository {}", path, namespace);
                return null;
            }
        }

        final long contentLength = response.headers()
                .firstValueAsLong("Content-Length")
                .orElseThrow();
        final String contentType = response.headers()
                .firstValue("Content-Type")
                .orElseThrow();

        final String digest = response.headers()
                .firstValue(OCIRepositoryEndpoint.HEADER_CONTENT_DIGEST)
                .orElse(null);

        return new OCIStreamHandle(
                new StreamHandle(response.body(), contentType, contentLength),
                digest
        );
    }

    public OCIContentInfo headBlob(final String namespace, final String digest) throws IOException {
        return head(namespace, "/blobs/" + digest, null);
    }

    public OCIContentInfo headManifest(final String namespace, final String reference) throws IOException {
        return head(namespace, "/manifests/" + reference, null);
    }

    public OCIStreamHandle getBlob(final String namespace, final String digest) throws IOException {
        return get(namespace, "/blobs/" + digest, null);
    }

    public OCIStreamHandle getBlobRange(final String namespace,
                                        final String digest,
                                        final long start,
                                        final long end) throws IOException {
        return get(namespace, "/blobs/" + digest, Map.of(HttpHeaders.RANGE, "bytes=%d-%d".formatted(start, end)));
    }

    public OCIStreamHandle getManifest(final String namespace, final String reference) throws IOException {
        return get(namespace, "/manifests/" + reference, null);
    }

}
