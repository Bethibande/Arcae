package com.bethibande.repository.jpa.user;

import com.bethibande.process.annotation.EntityDTO;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import java.time.Instant;

@Entity
@EntityDTO
@EntityDTO(excludeProperties = {"token", "owner"}, name = "AccessTokenDTOWithoutToken")
@EntityDTO(excludeProperties = {"id", "token", "owner"}, name = "AccessTokenDTOWithoutId")
public class AccessToken extends PanacheEntity {

    @ManyToOne(optional = false)
    public User owner;

    @Column(nullable = false, columnDefinition = "varchar(64)")
    public String name;

    @Column(nullable = false, columnDefinition = "varchar(256)", unique = true)
    public String token;

    @Column(columnDefinition = "timestamptz")
    public Instant expiresAfter;

}
