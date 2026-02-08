package com.bethibande.repository.repository.backend;

import com.bethibande.repository.repository.S3Config;
import com.bethibande.repository.repository.StreamHandle;
import com.bethibande.repository.web.repositories.OCIRepositoryEndpoint;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.auth.aws.signer.SignerConstant;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.util.List;

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

    public String createMultipartUpload(final String path) {
        return this.client.createMultipartUpload(b -> b.bucket(this.config.bucket()).key(path).contentType("application/octet-stream"))
                .uploadId();
    }

    public void uploadPart(final String uploadId, final String path, final int partNumber, final StreamHandle handle) {
        final RequestBody body = RequestBody.fromInputStream(handle.stream(), handle.contentLength());
        this.client.uploadPart(
                b -> b.bucket(this.config.bucket())
                        .key(path)
                        .contentLength(handle.contentLength())
                        .uploadId(uploadId)
                        .partNumber(partNumber),
                body
        );
    }

    public void completeMultipartUpload(final String uploadId, final String path) {
        final List<CompletedPart> parts = this.client.listParts(b -> b.bucket(this.config.bucket()).key(path).uploadId(uploadId))
                .parts()
                .stream()
                .map(part -> CompletedPart.builder()
                        .eTag(part.eTag())
                        .partNumber(part.partNumber())
                        .build())
                .toList();

        this.client.completeMultipartUpload(b -> b.bucket(this.config.bucket())
                .key(path)
                .uploadId(uploadId)
                .multipartUpload(u -> u.parts(parts)));
    }

    public MultipartUploadStatus headUpload(final String uploadId, final String path) {
        // TODO: Handle truncated responses
        final ListPartsResponse response = this.client.listParts(b -> b.bucket(this.config.bucket())
                .key(path)
                .uploadId(uploadId));

        final long offset = response.parts()
                .stream()
                .mapToLong(Part::size)
                .sum();

        final int partNumber = response.parts().size();

        return new MultipartUploadStatus(offset, partNumber);
    }

    public void abortMultipartUpload(final String uploadId, final String path) {
        this.client.abortMultipartUpload(b -> b.bucket(this.config.bucket()).key(path).uploadId(uploadId));
    }

    public void move(final String source, final String destination) {
        final long contentLength = this.client.headObject(b -> b.bucket(this.config.bucket()).key(source))
                .contentLength();

        if (contentLength < 5_000_000_000L) {
            this.client.copyObject(b -> b.destinationBucket(this.config.bucket())
                    .destinationKey(destination)
                    .sourceBucket(this.config.bucket())
                    .sourceKey(source));
        } else {
            final String uploadId = this.createMultipartUpload(destination);

            int part = 1;
            long offset = 0;
            while (offset < contentLength) {
                final long currentOffset = offset;
                final int currentPart = part++;

                final long partSize = Math.min(OCIRepositoryEndpoint.MIN_CHUNK_LENGTH, contentLength - offset);
                this.client.uploadPartCopy(b -> b.sourceBucket(this.config.bucket())
                        .destinationBucket(this.config.bucket())
                        .sourceKey(source)
                        .destinationKey(destination)
                        .uploadId(uploadId)
                        .partNumber(currentPart)
                        .copySourceRange("bytes=%d-%d".formatted(currentOffset, currentOffset + partSize - 1)));
                offset += partSize;
            }

            this.completeMultipartUpload(uploadId, destination);
        }

        delete(source);
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
    public ObjectInfo headObject(final String path) {
        try {
            final HeadObjectResponse response = this.client.headObject(b -> b.bucket(this.config.bucket()).key(path));
            return new ObjectInfo(response.contentLength(), response.contentType());
        } catch (final NoSuchKeyException ex) {
            return null;
        }
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
