package com.bethibande.arcae.jpa.user;

import com.bethibande.process.annotation.EntityDTO;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.persistence.*;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Indexed
@RegisterForReflection
@Table(name = "Users")
@EntityDTO(excludeProperties = "id")
@EntityDTO(excludeProperties = {"id", "roles", "twoFAMethods"}, name = "UserDTOWithoutIdAndRoles")
// TODO: Field whitelist instead
@EntityDTO(excludeProperties = {"id", "roles", "password", "name", "email"}, name = "Update2FAMethodsUserDTO")
@EntityDTO(excludeProperties = "password")
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

    @Enumerated(EnumType.STRING)
    @ElementCollection(fetch = FetchType.EAGER)
    @Column(columnDefinition = "varchar(64)")
    public List<TwoFAMethod> twoFAMethods;

    @Override
    public String getName() {
        return this.name;
    }

    public Set<String> getRolesAsString() {
        return roles.stream()
                .map(UserRole::toString)
                .collect(Collectors.toSet());
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
