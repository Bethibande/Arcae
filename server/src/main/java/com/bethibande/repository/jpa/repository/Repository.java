package com.bethibande.repository.jpa.repository;

import com.bethibande.process.annotation.EntityDTO;
import com.bethibande.repository.jpa.repository.permissions.PermissionScope;
import com.bethibande.repository.jpa.user.UserRole;
import com.bethibande.repository.repository.cleanup.CleanupPolicies;
import com.bethibande.repository.repository.security.AuthContext;
import com.bethibande.repository.repository.security.UserAuthContext;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;

import java.util.List;
import java.util.Map;

@Entity
@EntityDTO(excludeProperties = "permissions", name = "RepositoryDTO")
@EntityDTO(excludeProperties = {"id", "permissions"}, name = "RepositoryDTOWithoutId")
@EntityDTO(excludeProperties = {"settings", "permissions", "cleanupPolicies"}, name = "PublicRepositoryDTO")
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

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    public Map<RepositoryMetadataKey, Object> metadata;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    public CleanupPolicies cleanupPolicies;

    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<PermissionScope> permissions;

    @SuppressWarnings("unchecked")
    public <T> T getMetadata(final RepositoryMetadataKey key) {
        return (T) metadata.get(key);
    }

    public <T> T getMetadataOrDefault(final RepositoryMetadataKey key, final T defaultValue) {
        final T value = getMetadata(key);
        return value != null ? value : defaultValue;
    }

    public void setMetadata(final RepositoryMetadataKey key, final Object value) {
        if (value == null) {
            metadata.remove(key);
        } else {
            metadata.put(key, value);
        }
    }

    public boolean canView(final AuthContext auth) {
        if (auth.isSystem()) return true;
        if (auth instanceof UserAuthContext userAuth && userAuth.getUser().roles.contains(UserRole.ADMIN)) return true;
        if (permissions.isEmpty()) return true;

        for (int i = 0; i < permissions.size(); i++) {
            if (permissions.get(i).canView(auth)) return true;
        }
        return false;
    }

    public boolean canWrite(final AuthContext auth) {
        if (auth.isSystem()) return true;
        if (auth instanceof UserAuthContext userAuth && userAuth.getUser().roles.contains(UserRole.ADMIN)) return true;
        for (int i = 0; i < permissions.size(); i++) {
            if (permissions.get(i).canWrite(auth)) return true;
        }
        return false;
    }

}
