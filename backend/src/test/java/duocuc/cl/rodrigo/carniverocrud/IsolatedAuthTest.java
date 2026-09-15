package duocuc.cl.rodrigo.carniverocrud;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@ActiveProfiles("test")
abstract class IsolatedAuthTest {
    static final String ISSUER = "https://issuer.test.invalid/v2.0";
    static final String AUDIENCE = "carnivario-test-api";
    static final RSAKey SIGNING_KEY;
    static final HttpServer KEYS_SERVER;

    static {
        try {
            SIGNING_KEY = new RSAKeyGenerator(2048).keyID("test-key").generate();
            KEYS_SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] publicKeys = new JWKSet(SIGNING_KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            KEYS_SERVER.createContext("/keys", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, publicKeys.length);
                try (var output = exchange.getResponseBody()) { output.write(publicKeys); }
            });
            KEYS_SERVER.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> KEYS_SERVER.stop(0)));
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @DynamicPropertySource
    static void isolatedProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.config.import", () -> "");
        properties.add("jwt.secret", () -> Base64.getEncoder().encodeToString(new byte[64]));
        properties.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        properties.add("spring.security.oauth2.resourceserver.jwt.audiences", () -> AUDIENCE);
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + KEYS_SERVER.getAddress().getPort() + "/keys");
    }

    static String azureToken(String email, String role) throws JOSEException {
        return azureToken(email, role, "Carnivario.Read Carnivario.Write", ISSUER, AUDIENCE,
                Instant.now().plusSeconds(300), SIGNING_KEY, "preferred_username");
    }

    static String azureToken(String email, String role, String scope, String issuer, String audience,
                             Instant expires, RSAKey key, String emailClaim) throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer).audience(audience).subject("microsoft-test-subject")
                .issueTime(Date.from(Instant.now().minusSeconds(600)))
                .expirationTime(Date.from(expires)).claim(emailClaim, email)
                .claim("name", "Usuario Microsoft").claim("roles", List.of(role)).claim("scp", scope).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
