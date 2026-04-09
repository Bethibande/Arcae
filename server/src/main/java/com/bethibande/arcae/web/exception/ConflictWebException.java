package com.bethibande.arcae.web.exception;

import jakarta.ws.rs.WebApplicationException;
import org.apache.http.HttpStatus;

public class ConflictWebException extends WebApplicationException {

    public ConflictWebException() {
        super(HttpStatus.SC_CONFLICT);
    }

    public ConflictWebException(final String message) {
        super(message, HttpStatus.SC_CONFLICT);
    }
}
