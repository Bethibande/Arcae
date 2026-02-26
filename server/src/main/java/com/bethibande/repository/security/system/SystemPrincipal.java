package com.bethibande.repository.security.system;

import java.security.Principal;

public class SystemPrincipal implements Principal {

    public static final String NAME = "system";

    public static final SystemPrincipal INSTANCE = new SystemPrincipal();

    private SystemPrincipal() {
    }

    @Override
    public String getName() {
        return NAME;
    }
}
