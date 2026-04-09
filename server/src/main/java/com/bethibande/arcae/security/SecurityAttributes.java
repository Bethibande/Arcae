package com.bethibande.arcae.security;

import com.bethibande.arcae.jpa.security.AccessToken;
import com.bethibande.arcae.jpa.security.UserSession;

public class SecurityAttributes {

    /**
     * The session attribute key on the SecurityIdentity.
     * @see UserSession
     */
    public static final String SESSION = "session";

    /**
     * The access token attribute key on the SecurityIdentity.
     * @see AccessToken
     */
    public static final String ACCESS_TOKEN = "access-token";

}
