package com.bethibande.repository.jpa.repository;

import com.bethibande.process.annotation.EntityDTO;
import com.bethibande.repository.jpa.repository.permissions.PermissionScope;
import com.bethibande.repository.jpa.user.User;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;

import java.util.List;

@Entity
@EntityDTO(excludeProperties = "permissions", name = "RepositoryDTO")
@EntityDTO(excludeProperties = {"id", "permissions"}, name = "RepositoryDTOWithoutId")
@EntityDTO(excludeProperties = {"settings", "permissions"}, name = "PublicRepositoryDTO")
public class Repository extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @GenericField(sortable = Sortable.YES)
    public Long id;

    @Column(length = 64, nullable = false, unique = true)
    public String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    public PackageManager packageManager;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    public String settings;

    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<PermissionScope> permissions;

    public boolean canView(final User user) {
        for (int i = 0; i < permissions.size(); i++) {
            if (permissions.get(i).isAllowed(user)) return true;
        }
        return false;
    }

}
