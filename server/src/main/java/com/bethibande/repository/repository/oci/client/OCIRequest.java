package com.bethibande.repository.repository.oci.client;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

public record OCIRequest<T>(
        String namespace,
        String subpath,
        String method,
        HttpResponse.BodyHandler<T> bodyHandler,
        Map<String, List<String>> additionalHeaders
) {
}
