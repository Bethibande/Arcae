package com.bethibande.repository.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class HttpClientUtil {

    public static HttpRequest.BodyPublisher jsonBodyPublisher(final Object object) {
        try (final InstanceHandle<ObjectMapper> objectMapperHandle = Arc.container().instance(ObjectMapper.class)) {
            final ObjectMapper objectMapper = objectMapperHandle.get();
            final String json = objectMapper.writeValueAsString(object);

            return HttpRequest.BodyPublishers.ofString(json);
        } catch (final JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialize value", ex);
        }
    }

    public static <T> HttpResponse.BodyHandler<T> jsonBodyHandler(final CallableBiFunction<ObjectMapper, String, T, JsonProcessingException> converter) {
        try (final InstanceHandle<ObjectMapper> objectMapperHandle = Arc.container().instance(ObjectMapper.class)) {
            final ObjectMapper objectMapper = objectMapperHandle.get();

            return (_) -> HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8), (json) -> {
                try {
                    if (json == null) return null;
                    return converter.call(objectMapper, json);
                } catch (final JsonProcessingException ex) {
                    throw new RuntimeException(ex);
                }
            });

        }
    }

    public static <T> HttpResponse.BodyHandler<T> jsonBodyHandler(final TypeReference<T> type) {
        return jsonBodyHandler((objectMapper, json) -> objectMapper.readValue(json, type));
    }

    public static <T> HttpResponse.BodyHandler<T> jsonBodyHandler(final Class<T> clazz) {
        return jsonBodyHandler((objectMapper, json) -> objectMapper.readValue(json, clazz));
    }

}
