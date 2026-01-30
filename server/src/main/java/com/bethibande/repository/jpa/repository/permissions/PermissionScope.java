package com.bethibande.repository.jpa.repository.permissions;

import com.bethibande.repository.jpa.repository.Repository;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

@Entity
public class PermissionScope extends PanacheEntity {

    @ManyToOne(optional = false)
    public Repository repository;

    @ManyToOne(optional = false)
    public UserSelection selection;

    @Column(nullable = false)
    public PermissionLevel level;

}
