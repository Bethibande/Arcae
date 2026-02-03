package com.bethibande.repository.jpa.user;

import com.bethibande.process.annotation.EntityDTO;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

import java.security.Principal;
import java.util.List;
import java.util.Objects;

@Entity
@Indexed
@Table(name = "Users")
@EntityDTO(excludeProperties = "id")
@EntityDTO(excludeProperties = "password")
@EntityDTO(excludeProperties = "roles")
public class User extends PanacheEntity implements Principal {

    @FullTextField
    @Column(nullable = false, unique = true, columnDefinition = "varchar(64)")
    public String name;

    @FullTextField
    @Column(nullable = false, unique = true, columnDefinition = "varchar(512)")
    public String email;

    @Column(nullable = false, columnDefinition = "varchar(512)")
    public String password;

    @Enumerated(EnumType.STRING)
    @ElementCollection(fetch = FetchType.EAGER)
    @Column(columnDefinition = "varchar(255)")
    public List<UserRole> roles;

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof final User user)) return false;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
