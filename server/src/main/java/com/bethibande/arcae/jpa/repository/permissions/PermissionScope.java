package com.bethibande.arcae.jpa.repository.permissions;

import com.bethibande.process.annotation.EntityDTO;
import com.bethibande.process.annotation.VirtualDTOField;
import com.bethibande.arcae.jpa.repository.Repository;
import com.bethibande.arcae.jpa.user.User;
import com.bethibande.arcae.repository.security.AuthContext;
import com.bethibande.arcae.repository.security.UserAuthContext;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import java.util.Objects;

@Entity
@EntityDTO
@EntityDTO(excludeProperties = "id")
public class PermissionScope extends PanacheEntity {

    @ManyToOne(optional = false)
    public Repository repository;

    @Column(nullable = false)
    public UserSelectionType type;

    @ManyToOne
    public User user;

    @Column(nullable = false)
    public PermissionLevel level;

    @VirtualDTOField
    public String userName() {
        return user != null ? user.name : null;
    }

    public boolean canView(final AuthContext auth) {
        return switch (type) {
            case ANONYMOUS -> true;
            case AUTHENTICATED -> !auth.isAnonymous();
            case USER -> auth instanceof UserAuthContext userAuth && Objects.equals(this.user.id, userAuth.getUser().id);
        };
    }

    public boolean canWrite(final AuthContext auth) {
        if (level == PermissionLevel.READ) return false;

        return canView(auth); // Same check
    }

}
