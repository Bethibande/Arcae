package com.bethibande.arcae.util;

@FunctionalInterface
public interface CallableFunction<T, R, E extends Exception> {

    R call(final T value) throws E;

}
