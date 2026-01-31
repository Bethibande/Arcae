package com.bethibande.repository.jpa.repository.permissions;

import com.bethibande.process.annotation.EntityDTO;
import com.bethibande.process.annotation.VirtualDTOField;
import com.bethibande.repository.jpa.repository.Repository;
import com.bethibande.repository.jpa.user.User;
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

    public boolean canView(final User user) {
        return switch (type) {
            case ANONYMOUS -> true;
            case AUTHENTICATED -> user != null;
            case USER -> Objects.equals(this.user.id, user.id);
        };
    }

    public boolean canWrite(final User user) {
        if (level == PermissionLevel.READ) return false;

        return switch (type) {
            case ANONYMOUS -> true;
            case AUTHENTICATED -> user != null;
            case USER -> Objects.equals(this.user.id, user.id);
        };
    }

}
