package com.bethibande.arcae.util;

import java.util.Collection;

public interface Registration {

    static <T> Registration addAndReturn(final T instance, final Collection<T> listeners) {
        listeners.add(instance);
        return () -> listeners.remove(instance);
    }

    void remove();

}
