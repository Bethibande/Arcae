package com.bethibande.repository.jpa.repository.permissions;

import com.bethibande.repository.jpa.user.User;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

@Entity
public class UserSelection extends PanacheEntity {

    @Column(nullable = false)
    public UserSelectionType type;

    @ManyToOne
    public User user;
}
