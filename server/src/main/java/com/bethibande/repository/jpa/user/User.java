package com.bethibande.repository.jpa.user;

import com.bethibande.process.annotation.EntityDTO;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.util.List;
import java.util.Set;

@Entity
@Table(name = "Users")
@EntityDTO(excludeProperties = "id")
@EntityDTO(excludeProperties = "password")
public class User extends PanacheEntity {

    @Column(nullable = false, unique = true, columnDefinition = "varchar(64)")
    public String name;

    @Column(nullable = false, unique = true, columnDefinition = "varchar(512)")
    public String email;

    @Column(nullable = false, columnDefinition = "varchar(512)")
    public String password;

    @Enumerated(EnumType.STRING)
    @ElementCollection(fetch = FetchType.EAGER)
    @Column(columnDefinition = "varchar(255)")
    public List<UserRole> roles;

}
