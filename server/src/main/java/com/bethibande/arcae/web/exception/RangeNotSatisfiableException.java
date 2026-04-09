package com.bethibande.arcae.web.exception;

import jakarta.ws.rs.WebApplicationException;
import org.apache.http.HttpStatus;

public class RangeNotSatisfiableException extends WebApplicationException {

    public RangeNotSatisfiableException() {
        super(HttpStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
    }

    public RangeNotSatisfiableException(final String message) {
        super(message, HttpStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
    }
}
