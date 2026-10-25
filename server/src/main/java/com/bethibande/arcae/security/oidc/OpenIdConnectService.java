package com.bethibande.arcae.security.oidc;

import com.bethibande.arcae.jpa.security.OpenIDConnectProvider;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwx.JsonWebStructure;
import org.jose4j.keys.resolvers.JwksVerificationKeyResolver;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@ApplicationScoped
public class OpenIdConnectService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenIdConnectService.class);

    private static final String[] ALLOWED_ALGORITHMS = {
            AlgorithmIdentifiers.RSA_USING_SHA256,
            AlgorithmIdentifiers.RSA_USING_SHA384,
            AlgorithmIdentifiers.RSA_USING_SHA512,
            AlgorithmIdentifiers.RSA_PSS_USING_SHA256,
            AlgorithmIdentifiers.RSA_PSS_USING_SHA384,
            AlgorithmIdentifiers.RSA_PSS_USING_SHA512,
            AlgorithmIdentifiers.ECDSA_USING_P256_CURVE_AND_SHA256,
            AlgorithmIdentifiers.ECDSA_USING_P384_CURVE_AND_SHA384,
            AlgorithmIdentifiers.ECDSA_USING_P521_CURVE_AND_SHA512,
            AlgorithmIdentifiers.EDDSA
    };

    private static final int ALLOWED_CLOCK_SKEW_SECONDS = 60;

    private final WebClient client;
    private final JwksCache jwksCache;

    public OpenIdConnectService(final Vertx vertx, final JwksCache jwksCache) {
        this.client = WebClient.create(vertx);
        this.jwksCache = jwksCache;
    }

    public Future<OpenIDConnectOptions> fetchWellKnown(final String discoveryUrl) {
        return this.client.getAbs(discoveryUrl)
                .send()
                .map(response -> {
                    if (response.statusCode() != 200) {
                        LOGGER.error("Failed to fetch well-known config from OIDC provider {}, status {}", discoveryUrl, response.statusCode());
                        throw new RuntimeException("Failed to fetch well-known config from OIDC provider");
                    }

                    return response.bodyAsJsonObject()
                            .mapTo(OpenIDConnectOptions.class);
                }).onFailure(error -> LOGGER.error("Failed to fetch well-known config from OIDC provider", error));
    }

    /**
     * Whether ID tokens issued by the given provider are validated. Plain OAuth2 providers do not issue ID tokens,
     * for those the subject is retrieved from the user info endpoint instead.
     */
    public static boolean validatesIdToken(final OpenIDConnectProvider provider) {
        return provider.issuer != null && !provider.issuer.isBlank()
                && provider.jwksUri != null && !provider.jwksUri.isBlank();
    }

    public Future<String> getSubjectFromCode(final String code,
                                             final OpenIDConnectProvider provider,
                                             final String redirectUri,
                                             final String nonce) {
        final String credentials = Base64.getEncoder().encodeToString((provider.clientId + ":" + provider.clientSecret).getBytes());

        return this.client.postAbs(provider.tokenEndpoint)
                        .putHeader("Authorization", "Basic " + credentials)
                        .putHeader("Accept", "application/json")
                        .sendForm(MultiMap.caseInsensitiveMultiMap()
                                .add("grant_type", "authorization_code")
                                .add("code", code)
                                .add("redirect_uri", redirectUri)
                                .add("client_id", provider.clientId)
                                .add("client_secret", provider.clientSecret))
                .flatMap(response -> {
                    if (response.statusCode() != 200) {
                        LOGGER.warn("Failed to fetch access token from OIDC provider, status: {} - {}", response.statusCode(), response.bodyAsString());
                        throw new IllegalStateException("Failed to fetch access token from OIDC provider");
                    }

                    final JsonObject body = response.bodyAsJsonObject();
                    if (validatesIdToken(provider)) {
                        final String idToken = body.getString("id_token");
                        if (idToken == null) throw new IllegalStateException("OIDC provider did not return an ID token");

                        return getSubjectFromIdToken(idToken, provider, nonce);
                    }

                    return getSubjectFromUserInfo(body.getString("access_token"), provider);
                })
                .recover(error -> {
                    LOGGER.error("Failed to retrieve subject from OIDC provider", error);
                    return Future.failedFuture(error);
                });
    }

    private Future<String> getSubjectFromIdToken(final String idToken,
                                                 final OpenIDConnectProvider provider,
                                                 final String nonce) {
        if (nonce == null || nonce.isBlank()) return Future.failedFuture(new IllegalStateException("Missing nonce"));

        final String keyId;
        try {
            keyId = JsonWebStructure.fromCompactSerialization(idToken).getKeyIdHeaderValue();
        } catch (final JoseException ex) {
            return Future.failedFuture(new IllegalStateException("Malformed ID token", ex));
        }

        return this.jwksCache.getKeys(provider.jwksUri, keyId)
                .map(keys -> {
                    final JwtConsumer consumer = new JwtConsumerBuilder()
                            .setVerificationKeyResolver(new JwksVerificationKeyResolver(keys))
                            .setJwsAlgorithmConstraints(AlgorithmConstraints.ConstraintType.PERMIT, ALLOWED_ALGORITHMS)
                            .setExpectedIssuer(provider.issuer)
                            .setExpectedAudience(provider.clientId)
                            .setRequireExpirationTime()
                            .setRequireIssuedAt()
                            .setRequireSubject()
                            .setAllowedClockSkewInSeconds(ALLOWED_CLOCK_SKEW_SECONDS)
                            .build();

                    final JwtClaims claims;
                    try {
                        claims = consumer.processToClaims(idToken);
                    } catch (final InvalidJwtException ex) {
                        throw new IllegalStateException("Invalid ID token", ex);
                    }

                    try {
                        final String tokenNonce = claims.getStringClaimValue("nonce");
                        if (tokenNonce == null || !MessageDigest.isEqual(
                                tokenNonce.getBytes(StandardCharsets.UTF_8),
                                nonce.getBytes(StandardCharsets.UTF_8))) {
                            throw new IllegalStateException("ID token nonce mismatch");
                        }

                        // If present, the authorized party must be this client (OIDC Core 3.1.3.7)
                        final String authorizedParty = claims.getStringClaimValue("azp");
                        if (authorizedParty != null && !authorizedParty.equals(provider.clientId)) {
                            throw new IllegalStateException("ID token was issued to a different authorized party");
                        }
                        if (authorizedParty == null && claims.getAudience().size() > 1) {
                            throw new IllegalStateException("ID token with multiple audiences is missing the azp claim");
                        }

                        return claims.getSubject();
                    } catch (final MalformedClaimException ex) {
                        throw new IllegalStateException("Invalid ID token", ex);
                    }
                });
    }

    private Future<String> getSubjectFromUserInfo(final String accessToken, final OpenIDConnectProvider provider) {
        return this.client.getAbs(provider.userInfoEndpoint)
                .putHeader("Authorization", "Bearer " + accessToken)
                .putHeader("Accept", "application/json")
                .send()
                .map(response -> {
                    if (response.statusCode() != 200) {
                        LOGGER.warn("Failed to fetch user info from OIDC provider, status: {} - {}", response.statusCode(), response.bodyAsString());
                        throw new IllegalStateException("Failed to fetch user info from OIDC provider");
                    }

                    final JsonObject body = response.bodyAsJsonObject();
                    if (body.containsKey("sub")) return body.getString("sub");
                    final Object id = body.getValue("id");
                    if (id != null) return id.toString();

                    throw new IllegalStateException("Failed to retrieve subject from OIDC provider");
                });
    }

}
