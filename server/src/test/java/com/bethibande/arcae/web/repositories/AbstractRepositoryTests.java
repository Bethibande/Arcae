package com.bethibande.arcae.web.repositories;

import com.bethibande.arcae.jpa.repository.PackageManager;
import com.bethibande.arcae.jpa.repository.Repository;
import com.bethibande.arcae.jpa.repository.permissions.PermissionScope;
import com.bethibande.arcae.repository.cleanup.CleanupPolicies;
import com.bethibande.arcae.repository.cleanup.MaxAgeCleanupPolicy;
import com.bethibande.arcae.repository.cleanup.MaxVersionCountPolicy;
import com.bethibande.arcae.web.AbstractWebTests;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.narayana.jta.QuarkusTransaction;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.time.temporal.ChronoUnit;
import java.util.List;

public class AbstractRepositoryTests extends AbstractWebTests {

    public static void createRepository(final String name,
                                        final PackageManager packageManager,
                                        final Object settings,
                                        final List<PermissionScope> permissions) {
        try (final InstanceHandle<ObjectMapper> instance = Arc.container().instance(ObjectMapper.class)) {
            final ObjectMapper mapper = instance.get();

            QuarkusTransaction.requiringNew().run(() -> {
                final Repository repository = new Repository();
                repository.name = name;
                repository.packageManager = packageManager;
                repository.settings = mapper.convertValue(settings, String.class);
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
            });
        }

    }

}
