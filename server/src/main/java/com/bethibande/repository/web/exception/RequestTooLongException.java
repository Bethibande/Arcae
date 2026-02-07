package com.bethibande.repository.web.exception;

import jakarta.ws.rs.WebApplicationException;
import org.apache.http.HttpStatus;

public class RequestTooLongException extends WebApplicationException {

    public RequestTooLongException() {
        super(HttpStatus.SC_REQUEST_TOO_LONG);
    }

    public RequestTooLongException(final String message) {
        super(message, HttpStatus.SC_REQUEST_TOO_LONG);
    }
}
