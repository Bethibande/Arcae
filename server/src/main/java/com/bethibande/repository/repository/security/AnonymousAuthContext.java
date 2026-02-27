package com.bethibande.repository.repository.security;

public final class AnonymousAuthContext extends AbstractAuthContext {

    AnonymousAuthContext(final AuthContext parent) {
        super(parent);
    }

    AnonymousAuthContext() {
    }
}
