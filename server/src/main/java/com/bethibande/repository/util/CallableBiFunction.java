package com.bethibande.repository.util;

public interface CallableBiFunction<A, B, R, E extends Exception> {

    R call(final A a, final B b) throws E;

}
