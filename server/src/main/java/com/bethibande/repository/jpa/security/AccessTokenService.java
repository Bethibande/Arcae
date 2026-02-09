package com.bethibande.repository.jpa.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AccessTokenService {

    @Transactional
    public AccessToken getToken(final String token) {
        return AccessToken.find("token = ?1", token).firstResult();
    }

    @Transactional
    public AccessToken getToken(final String username, final String token) {
        return AccessToken.find("owner.name = ?1 AND token = ?2", username, token).firstResult();
    }

}
