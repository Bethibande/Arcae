package com.bethibande.arcae.jpa.security;

import com.bethibande.process.annotation.EntityDTO;
import com.bethibande.arcae.jpa.user.User;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

@Entity
@Table(
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "provider_id"}),
                @UniqueConstraint(columnNames = {"provider_id", "subject"})
        }
)
@EntityDTO(excludeProperties = {"subject", "provider.clientId", "provider.clientSecret", "provider.discoveryUrl"}, expandProperties = "provider", name = "OpenIDConnectionDTO")
public class OpenIDConnection extends PanacheEntity {

    @ManyToOne(optional = false)
    public User user;

    @ManyToOne(optional = false)
    public OpenIDConnectProvider provider;

    @Column(nullable = false, columnDefinition = "varchar(255)")
    public String subject;

}
