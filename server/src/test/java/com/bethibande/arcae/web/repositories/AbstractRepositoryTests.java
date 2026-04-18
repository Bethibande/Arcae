package com.bethibande.arcae.web.repositories;

import com.bethibande.arcae.jpa.repository.PackageManager;
import com.bethibande.arcae.jpa.repository.Repository;
import com.bethibande.arcae.jpa.repository.permissions.PermissionScope;
import com.bethibande.arcae.repository.cleanup.CleanupPolicies;
import com.bethibande.arcae.repository.cleanup.MaxAgeCleanupPolicy;
import com.bethibande.arcae.repository.cleanup.MaxVersionCountPolicy;
import com.bethibande.arcae.web.AbstractWebTests;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.quarkus.narayana.jta.QuarkusTransaction;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class AbstractRepositoryTests extends AbstractWebTests {

    public static InputStream streamOf(final String content) {
        final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new ByteArrayInputStream(bytes);
    }

    public static String digest(final String content) {
        return digest(content.getBytes(StandardCharsets.UTF_8));
    }

    public static String digest(final byte[] data) {
        return DigestUtils.sha256Hex(data);
    }

    public Repository createRepository(final String name,
                                 final PackageManager packageManager,
                                 final Object settings,
                                 final List<PermissionScope> permissions) {
        return QuarkusTransaction.requiringNew().call(() -> {
            final Repository repository = new Repository();
            repository.name = name;
            repository.packageManager = packageManager;
            try {
                repository.settings = super.objectMapper.writeValueAsString(settings);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            repository.cleanupPolicies = new CleanupPolicies(
                    new MaxAgeCleanupPolicy(
                            false,
                            0,
                            ChronoUnit.DAYS
                    ),
                    new MaxVersionCountPolicy(
                            false,
                            10
                    )
            );
            repository.permissions = permissions;

            repository.persist();
            return repository;
        });

    }

}
