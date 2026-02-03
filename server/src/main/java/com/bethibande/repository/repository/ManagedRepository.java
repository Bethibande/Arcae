package com.bethibande.repository.repository;

import com.bethibande.repository.jpa.artifact.ArtifactVersion;
import com.bethibande.repository.jpa.user.User;

public interface ManagedRepository {

    void delete(final User user, final ArtifactVersion version, final boolean skipAuth);

}
