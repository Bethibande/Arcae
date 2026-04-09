package com.bethibande.arcae.web.repositories.oci;

/**
 * All OCI erorr codes as specified <a href="https://github.com/opencontainers/distribution-spec/blob/main/spec.md#error-codes">here</a>
 */
public class OCIErrorCodes {

    public static final String BLOB_UNKNOWN = "BLOB_UNKNOWN";
    public static final String BLOB_UPLOAD_INVALID = "BLOB_UPLOAD_INVALID";
    public static final String BLOB_UPLOAD_UNKNOWN = "BLOB_UPLOAD_UNKNOWN";
    public static final String DIGEST_INVALID = "DIGEST_INVALID";
    public static final String MANIFEST_BLOB_UNKNOWN = "MANIFEST_BLOB_UNKNOWN";
    public static final String MANIFEST_INVALID = "MANIFEST_INVALID";
    public static final String MANIFEST_UNKNOWN = "MANIFEST_UNKNOWN";
    public static final String NAME_INVALID = "NAME_INVALID";
    public static final String NAME_UNKNOWN = "NAME_UNKNOWN";
    public static final String SIZE_INVALID = "SIZE_INVALID";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String DENIED = "DENIED";
    public static final String UNSUPPORTED = "UNSUPPORTED";
    public static final String TOO_MANY_REQUESTS = "TOOMANYREQUESTS";

}
