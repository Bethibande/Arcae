package com.bethibande.repository.repository.oci.client;

import com.bethibande.repository.repository.maven.MirrorConnectionSettings;
import com.bethibande.repository.repository.mirror.MirrorAuthType;
import com.bethibande.repository.util.HttpClientUtil;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

public class OCIWebClient {

    private final HttpClient httpClient;

    private final MirrorConnectionSettings connection;

    public OCIWebClient(final HttpClient httpClient, final MirrorConnectionSettings connection) {
        this.httpClient = httpClient;
        this.connection = connection;
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

    private HttpRequest.Builder request(final String method, final String path) {
        final URI uri = URI.create(this.connection.url() + path);
        return HttpRequest.newBuilder()
                .uri(uri)
                .method(method, HttpRequest.BodyPublishers.noBody());
    }

    public HttpRequest.Builder get(final String path) {
        return request("GET", path);
    }

    public HttpRequest.Builder head(final String path) {
        return request("HEAD", path);
    }

    public HttpResponse<Void> sendVoid(final HttpRequest request) throws IOException {
        return send(request, HttpResponse.BodyHandlers.discarding());
    }

    public <T> HttpResponse<T> send(final HttpRequest request,
                                    final HttpResponse.BodyHandler<T> bodyHandler) throws IOException {
        try {
            return this.httpClient.send(request, bodyHandler);
        } catch (final InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    public <T> HttpResponse<T> send(final HttpRequest request,
                                    final TypeReference<T> type) throws IOException {
        return send(request, HttpClientUtil.jsonBodyHandler(type));
    }

    public HttpResponse<InputStream> send(final HttpRequest request) throws IOException {
        return send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

}
