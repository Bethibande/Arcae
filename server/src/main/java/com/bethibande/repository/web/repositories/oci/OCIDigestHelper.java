package com.bethibande.repository.web.repositories.oci;

import java.util.regex.Pattern;

public class OCIDigestHelper {

    public static final Pattern DIGEST_PATTERN = Pattern.compile("^([a-z0-9]+-)*((sha256:[a-fA-F0-9]{64})|(sha512:[a-fA-F0-9]{128}))$");

    public static boolean isDigest(final String reference) {
        return DIGEST_PATTERN.matcher(reference).matches();
    }

    public static String extractDigest(final String reference) {
        return DIGEST_PATTERN.matcher(reference).replaceAll("$2");
    }

    public static String referenceOrDigest(final String reference) {
        return isDigest(reference)
                ? extractDigest(reference)
                : reference;
    }

}
