package com.bethibande.repository.jpa.security;

import com.bethibande.process.annotation.EntityDTO;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

@Entity
@EntityDTO
@EntityDTO(excludeProperties = "id")
public class OpenIDConnectProvider extends PanacheEntity {

    @Column(nullable = false, unique = true, columnDefinition = "varchar(64)")
    public String name;

    @Column(nullable = false, columnDefinition = "varchar(255)")
    public String clientId;

    @Column(nullable = false, columnDefinition = "varchar(255)")
    public String clientSecret;

    @Column(columnDefinition = "varchar(512)")
    public String discoveryUrl;

    @Column(nullable = false, columnDefinition = "varchar(512)")
    public String authorizationEndpoint;

    @Column(nullable = false, columnDefinition = "varchar(512)")
    public String tokenEndpoint;

    @Column(nullable = false, columnDefinition = "varchar(512)")
    public String userInfoEndpoint;

}
