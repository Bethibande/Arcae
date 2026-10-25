package com.bethibande.arcae.security.oidc;

import com.bethibande.arcae.jpa.security.OpenIDConnectProvider;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OpenIdConnectServiceTests {

    private static final String CLIENT_ID = "arcae";
    private static final String NONCE = "test-nonce";
    private static final String SUBJECT = "user-1";

    private static Vertx vertx;
    private static HttpServer server;
    private static String baseUrl;

    private static volatile String tokenResponse;
    private static volatile String jwksResponse;
    private static final AtomicInteger jwksFetches = new AtomicInteger();

    private RsaJsonWebKey key;
    private MutableClock clock;
    private OpenIdConnectService service;

    private static final class MutableClock extends Clock {

        private Instant instant = Instant.now();

        void advance(final Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return this.instant;
        }

    }

    @BeforeAll
    public static void startProvider() throws Exception {
        vertx = Vertx.vertx();
        server = vertx.createHttpServer()
                .requestHandler(request -> {
                    switch (request.path()) {
                        case "/token" -> request.response()
                                .putHeader("Content-Type", "application/json")
                                .end(tokenResponse);
                        case "/jwks" -> {
                            jwksFetches.incrementAndGet();
                            request.response()
                                    .putHeader("Content-Type", "application/json")
                                    .end(jwksResponse);
                        }
                        case "/userinfo" -> request.response()
                                .putHeader("Content-Type", "application/json")
                                .end(new JsonObject().put("id", 42).encode());
                        default -> request.response().setStatusCode(404).end();
                    }
                })
                .listen(0)
                .toCompletionStage()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        baseUrl = "http://localhost:" + server.actualPort();
    }

    @AfterAll
    public static void stopProvider() throws Exception {
        vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    @BeforeEach
    public void setUp() throws Exception {
        this.key = generateKey("key-1");
        jwksResponse = new JsonWebKeySet(this.key).toJson();
        jwksFetches.set(0);

        this.clock = new MutableClock();
        this.service = new OpenIdConnectService(vertx, new JwksCache(vertx, this.clock));
    }

    private static RsaJsonWebKey generateKey(final String keyId) throws Exception {
        final RsaJsonWebKey key = RsaJwkGenerator.generateJwk(2048);
        key.setKeyId(keyId);

        return key;
    }

    private OpenIDConnectProvider provider() {
        final OpenIDConnectProvider provider = new OpenIDConnectProvider();
        provider.name = "test";
        provider.clientId = CLIENT_ID;
        provider.clientSecret = "secret";
        provider.authorizationEndpoint = baseUrl + "/authorize";
        provider.tokenEndpoint = baseUrl + "/token";
        provider.userInfoEndpoint = baseUrl + "/userinfo";
        provider.issuer = baseUrl;
        provider.jwksUri = baseUrl + "/jwks";

        return provider;
    }

    private JwtClaims validClaims() {
        final JwtClaims claims = new JwtClaims();
        claims.setIssuer(baseUrl);
        claims.setAudience(CLIENT_ID);
        claims.setSubject(SUBJECT);
        claims.setIssuedAtToNow();
        claims.setExpirationTimeMinutesInTheFuture(5);
        claims.setClaim("nonce", NONCE);

        return claims;
    }

    private void respondWithIdToken(final RsaJsonWebKey signingKey, final Consumer<JwtClaims> customizer) throws Exception {
        final JwtClaims claims = validClaims();
        customizer.accept(claims);

        final JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setKey(signingKey.getPrivateKey());
        jws.setKeyIdHeaderValue(signingKey.getKeyId());
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);

        tokenResponse = new JsonObject()
                .put("access_token", "access-token")
                .put("token_type", "Bearer")
                .put("id_token", jws.getCompactSerialization())
                .encode();
    }

    private String getSubject(final OpenIDConnectProvider provider, final String nonce) throws Exception {
        final Future<String> future = this.service.getSubjectFromCode("code", provider, baseUrl + "/callback", nonce);
        return future.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    private void assertRejected(final OpenIDConnectProvider provider, final String nonce) {
        final ExecutionException ex = assertThrows(ExecutionException.class, () -> getSubject(provider, nonce));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    public void testValidIdToken() throws Exception {
        respondWithIdToken(this.key, _ -> {});

        assertEquals(SUBJECT, getSubject(provider(), NONCE));
    }

    @Test
    public void testNonceMismatch() throws Exception {
        respondWithIdToken(this.key, _ -> {});

        assertRejected(provider(), "other-nonce");
    }

    @Test
    public void testMissingNonceCookie() throws Exception {
        respondWithIdToken(this.key, _ -> {});

        assertRejected(provider(), null);
    }

    @Test
    public void testMissingNonceClaim() throws Exception {
        respondWithIdToken(this.key, claims -> claims.unsetClaim("nonce"));

        assertRejected(provider(), NONCE);
    }

    @Test
    public void testWrongIssuer() throws Exception {
        respondWithIdToken(this.key, claims -> claims.setIssuer("https://evil.example.com"));

        assertRejected(provider(), NONCE);
    }

    @Test
    public void testWrongAudience() throws Exception {
        respondWithIdToken(this.key, claims -> claims.setAudience("other-client"));

        assertRejected(provider(), NONCE);
    }

    @Test
    public void testMultipleAudiencesWithoutAuthorizedParty() throws Exception {
        respondWithIdToken(this.key, claims -> claims.setAudience(CLIENT_ID, "other-client"));

        assertRejected(provider(), NONCE);
    }

    @Test
    public void testWrongAuthorizedParty() throws Exception {
        respondWithIdToken(this.key, claims -> claims.setClaim("azp", "other-client"));

        assertRejected(provider(), NONCE);
    }

    @Test
    public void testExpiredToken() throws Exception {
        respondWithIdToken(this.key, claims -> {
            final long now = NumericDate.now().getValue();
            claims.setIssuedAt(NumericDate.fromSeconds(now - 3600));
            claims.setExpirationTime(NumericDate.fromSeconds(now - 600));
        });

        assertRejected(provider(), NONCE);
    }

    @Test
    public void testUntrustedSigningKey() throws Exception {
        // Same key id as the published key, but a different key pair
        respondWithIdToken(generateKey("key-1"), _ -> {});

        assertRejected(provider(), NONCE);
    }

    @Test
    public void testKeyRotation() throws Exception {
        respondWithIdToken(this.key, _ -> {});
        assertEquals(SUBJECT, getSubject(provider(), NONCE));

        final RsaJsonWebKey rotatedKey = generateKey("key-2");
        jwksResponse = new JsonWebKeySet(List.of(this.key, rotatedKey)).toJson();
        respondWithIdToken(rotatedKey, _ -> {});

        // Unknown key ids don't trigger a re-fetch until the minimum refresh interval passed
        assertRejected(provider(), NONCE);
        assertEquals(1, jwksFetches.get());

        this.clock.advance(JwksCache.MIN_REFRESH_INTERVAL);
        assertEquals(SUBJECT, getSubject(provider(), NONCE));
        assertEquals(2, jwksFetches.get());

        // Known key ids are served from the cache
        assertEquals(SUBJECT, getSubject(provider(), NONCE));
        assertEquals(2, jwksFetches.get());
    }

    @Test
    public void testUserInfoFallbackWithoutIdTokenValidation() throws Exception {
        tokenResponse = new JsonObject().put("access_token", "access-token").encode();

        final OpenIDConnectProvider provider = provider();
        provider.issuer = null;
        provider.jwksUri = null;

        assertEquals("42", getSubject(provider, null));
    }

}
