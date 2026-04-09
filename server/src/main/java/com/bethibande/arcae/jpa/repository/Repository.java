package com.bethibande.arcae.jpa.repository;

import com.bethibande.process.annotation.EntityDTO;
import com.bethibande.process.annotation.VirtualDTOField;
import com.bethibande.arcae.jpa.repository.permissions.PermissionScope;
import com.bethibande.arcae.jpa.user.UserRole;
import com.bethibande.arcae.repository.ManagedRepository;
import com.bethibande.arcae.repository.cleanup.CleanupPolicies;
import com.bethibande.arcae.repository.security.AuthContext;
import com.bethibande.arcae.repository.security.UserAuthContext;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;

import java.util.List;
import java.util.Map;

@Entity
@EntityDTO(excludeProperties = {"permissions", "publicAccessAllowed"}, name = "RepositoryDTO")
@EntityDTO(excludeProperties = {"id", "permissions", "publicAccessAllowed"}, name = "RepositoryDTOWithoutId")
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

    /**
     * This ia a generated field. Call {@link #updateMetadata()} to update its value.
     * Never overwrite this value manually, any changes will be lost when persisting the entity.
     */
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    public Map<String, Object> metadata;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    public CleanupPolicies cleanupPolicies;

    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<PermissionScope> permissions;

    @PreUpdate
    @PrePersist
    public void updateMetadata() {
        try (final InstanceHandle<RepositoryManager> handle = Arc.container().instance(RepositoryManager.class)) {
            final RepositoryManager manager = handle.get();
            final ManagedRepository repository = manager.manage(this);

            this.metadata = repository.generateMetadata();
        }
    }

    @VirtualDTOField
    public Boolean isPublicAccessAllowed() {
        return permissions == null
                || permissions.isEmpty()
                || canView(AuthContext.ofUser(null));
    }

    public boolean canView(final AuthContext auth) {
        if (auth.isSystem()) return true;
        if (auth instanceof UserAuthContext userAuth && userAuth.getUser().roles.contains(UserRole.ADMIN)) return true;
        if (this.permissions == null || this.permissions.isEmpty()) return true;

        for (int i = 0; i < this.permissions.size(); i++) {
            if (this.permissions.get(i).canView(auth)) return true;
        }
        return false;
    }

    public boolean canWrite(final AuthContext auth) {
        if (auth.isSystem()) return true;
        if (auth instanceof UserAuthContext userAuth && userAuth.getUser().roles.contains(UserRole.ADMIN)) return true;
        if (this.permissions == null || this.permissions.isEmpty()) return false;

        for (int i = 0; i < this.permissions.size(); i++) {
            if (this.permissions.get(i).canWrite(auth)) return true;
        }
        return false;
    }

}
