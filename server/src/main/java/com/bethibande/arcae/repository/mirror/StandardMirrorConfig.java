package com.bethibande.arcae.repository.mirror;

import com.bethibande.arcae.jpa.repository.Repository;
import com.bethibande.arcae.repository.maven.MirrorConnectionSettings;
import com.bethibande.arcae.repository.security.AuthContext;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record StandardMirrorConfig(
        List<MirrorConnectionSettings> connections,
        boolean enabled,
        boolean storeArtifacts,
        Boolean authorizedUsersOnly
) {

    @JsonIgnore
    public boolean canMirror(final AuthContext auth, final Repository repository) {
        return this.authorizedUsersOnly == null
                || !this.authorizedUsersOnly
                || repository.canWrite(auth);
    }

}
