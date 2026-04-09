package com.bethibande.arcae.test;

import com.bethibande.arcae.repository.S3Config;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.util.Map;

public class MinioResource implements QuarkusTestResourceLifecycleManager {

    private static final String KEY_HOST = "repository.s3.host";
    private static final String KEY_REGION = "repository.s3.region";
    private static final String KEY_BUCKET = "repository.s3.bucket";
    private static final String KEY_ACCESS_KEY = "repository.s3.accessKey";
    private static final String KEY_SECRET_KEY = "repository.s3.secretKey";

    private static final String VALUE_ACCESS_KEY = "minio";
    private static final String VALUE_SECRET_KEY = "12345678";
    private static final String VALUE_BUCKET = "repository";

    private static final GenericContainer<?> minio = new GenericContainer<>("minio/minio:latest")
            .withEnv("MINIO_ROOT_USER", VALUE_ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", VALUE_SECRET_KEY)
            .withExposedPorts(9000)
            .withCommand("server /data")
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    public static S3Config getConfig() {
        final Config config = ConfigProvider.getConfig();
        return new S3Config(
                config.getValue(KEY_HOST, String.class),
                config.getValue(KEY_REGION, String.class),
                config.getValue(KEY_BUCKET, String.class),
                config.getValue(KEY_ACCESS_KEY, String.class),
                config.getValue(KEY_SECRET_KEY, String.class)
        );
    }

    @Override
    public Map<String, String> start() {
        minio.start();

        final String host = minio.getHost();
        final int port = minio.getFirstMappedPort();
        final String endpoint = String.format("http://%s:%d", host, port);

        try {
            minio.execInContainer("mc", "mb", "minio/" + VALUE_BUCKET);
        } catch (final IOException | InterruptedException ex) {
            throw new RuntimeException("Failed to create bucket", ex);
        }

        return Map.of(
                KEY_HOST, endpoint,
                KEY_REGION, "default",
                KEY_BUCKET, VALUE_BUCKET,
                KEY_ACCESS_KEY, VALUE_ACCESS_KEY,
                KEY_SECRET_KEY, VALUE_SECRET_KEY
        );
    }

    @Override
    public void stop() {
        minio.stop();
    }
}
