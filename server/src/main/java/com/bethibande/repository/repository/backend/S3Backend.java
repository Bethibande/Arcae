package com.bethibande.repository.repository.backend;

import com.bethibande.repository.jpa.repository.RepositoryBackend;
import com.bethibande.repository.repository.ArtifactDescriptor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class S3Backend implements IRepositoryBackend {

    protected final RepositoryBackend info;
    protected final S3BackendConfig config;

    protected final S3Client client;

    public S3Backend(final RepositoryBackend info, final ObjectMapper objectMapper) throws JsonProcessingException {
        this.info = info;
        this.config = objectMapper.readValue(info.settings, S3BackendConfig.class);

        this.client = S3Client.builder()
                .region(Region.of(this.config.region()))
                .endpointOverride(URI.create(this.config.host()))
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(this.config.accessKey(), this.config.secretKey())))
                .build();
    }

    @Override
    public RepositoryBackend getBackendInfo() {
        return info;
    }

    @Override
    public CompletableFuture<Void> put(final ArtifactDescriptor descriptor) {
        final PutObjectRequest request = PutObjectRequest.builder()
                .bucket(this.config.bucket())
                .contentType(descriptor.contentType())
                .contentLength(descriptor.contentLength())
                .key(descriptor.path())
                .build();

        final RequestBody body = RequestBody.fromInputStream(descriptor.stream(), descriptor.contentLength());

        try {
            this.client.putObject(request, body);
            return CompletableFuture.completedFuture(null);
        } catch (final Throwable th) {
            return CompletableFuture.failedFuture(th);
        }
    }

    @Override
    public Optional<ArtifactDescriptor> get(final String path) {
        final GetObjectRequest request = GetObjectRequest.builder()
                .bucket(this.config.bucket())
                .key(path)
                .build();

        try {
            final ResponseInputStream<GetObjectResponse> stream = this.client.getObject(request);
            final GetObjectResponse response = stream.response();

            return Optional.of(new ArtifactDescriptor(
                    path,
                    response.contentLength(),
                    response.contentType(),
                    stream
            ));
        } catch (final NoSuchKeyException _) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(final String path) {
        final DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(this.config.bucket())
                .key(path)
                .build();

        this.client.deleteObject(request);
    }
}
