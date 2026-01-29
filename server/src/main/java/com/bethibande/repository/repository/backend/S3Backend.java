package com.bethibande.repository.repository.backend;

import com.bethibande.repository.repository.S3Config;
import com.bethibande.repository.repository.StreamHandle;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;

public class S3Backend implements RepositoryBackend {

    private final S3Config config;
    private final S3Client client;

    public S3Backend(final S3Config config) {
        this.config = config;
        this.client = S3Client.builder()
                .endpointOverride(URI.create(config.url()))
                .region(Region.of(config.region()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(config.accessKey(), config.secretKey())))
                .forcePathStyle(true)
                .build();
    }

    @Override
    public void put(final String path, final StreamHandle handle) {
        final PutObjectRequest request = PutObjectRequest.builder()
                .bucket(this.config.bucket())
                .contentType(handle.contentType())
                .contentLength(handle.contentLength())
                .key(path)
                .build();

        final RequestBody body = RequestBody.fromInputStream(handle.stream(), handle.contentLength());

        this.client.putObject(request, body);
    }

    @Override
    public boolean head(final String path) {
        try {
            final HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(this.config.bucket())
                    .key(path)
                    .build();
            this.client.headObject(request);
            return true;
        } catch (final NoSuchKeyException ex) {
            return false;
        }
    }

    @Override
    public StreamHandle get(final String path) {
        final GetObjectRequest request = GetObjectRequest.builder()
                .bucket(this.config.bucket())
                .key(path)
                .build();

        try {
            final ResponseBytes<GetObjectResponse> response = this.client.getObjectAsBytes(request);

            return new StreamHandle(
                    response.asInputStream(),
                    response.response().contentType(),
                    response.response().contentLength()
            );
        } catch (final NoSuchKeyException ex) {
            return null;
        }
    }

    @Override
    public void delete(final String path) {
        this.client.deleteObject(b -> b.bucket(this.config.bucket()).key(path));
    }
}
