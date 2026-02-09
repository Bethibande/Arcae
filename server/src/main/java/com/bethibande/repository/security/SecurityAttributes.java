package com.bethibande.repository.security;

public class SecurityAttributes {

    /**
     * The session attribute key on the SecurityIdentity.
     * @see com.bethibande.repository.jpa.security.UserSession
     */
    public static final String SESSION = "session";

    /**
     * The access token attribute key on the SecurityIdentity.
     * @see com.bethibande.repository.jpa.security.AccessToken
     */
    public static final String ACCESS_TOKEN = "access-token";

}
