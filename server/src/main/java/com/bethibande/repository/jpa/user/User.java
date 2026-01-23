package com.bethibande.repository.jpa.user;

import com.bethibande.process.annotation.EntityDTO;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.util.Set;

@Entity
@Table(name = "Users")
@EntityDTO(excludeProperties = "password")
public class User extends PanacheEntity {

    @Column(nullable = false, unique = true, columnDefinition = "varchar(64)")
    public String name;

    @Column(nullable = false, unique = true, columnDefinition = "varchar(512)")
    public String email;

    @Column
    public String password;

    @Column
    @Enumerated(EnumType.STRING)
    public Set<UserRole> roles;

}
