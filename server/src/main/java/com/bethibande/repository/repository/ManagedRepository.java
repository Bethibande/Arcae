package com.bethibande.repository.repository;

import com.bethibande.repository.jpa.artifact.ArtifactVersion;
import com.bethibande.repository.jpa.repository.Repository;
import com.bethibande.repository.jpa.user.User;

public interface ManagedRepository {

    Repository getInfo();

    default boolean canView(final User user) {
        return getInfo().canView(user);
    }

    default boolean canWrite(final User user) {
        return getInfo().canWrite(user);
    }

    void delete(final User user, final ArtifactVersion version, final boolean skipAuth);

}
