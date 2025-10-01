package searchapp; 

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Utility for creating DPoP proofs for Bluesky OAuth.
 */
public final class DPoPUtil {

    private static ECKey privateECKey; // ECKey with both private+public
    private static ECKey publicJWK;    // public-only

    private DPoPUtil() {}

    // Generate a P-256 keypair once for the app/session
    public static synchronized void init() {
        if (privateECKey != null) return;
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(256); // P-256
            KeyPair kp = kpg.generateKeyPair();

            ECPublicKey pub = (ECPublicKey) kp.getPublic();
            ECPrivateKey priv = (ECPrivateKey) kp.getPrivate();

            privateECKey = new ECKey.Builder(Curve.P_256, pub)
                    .privateKey(priv)
                    .build();

            publicJWK = privateECKey.toPublicJWK();

            System.out.println("✅ DPoP keypair generated");
        } catch (Exception e) {
            throw new RuntimeException("DPoP init failed", e);
        }
    }

    /**
     * Build a DPoP proof for the given HTTP method and URL.
     * @param method HTTP verb (e.g. POST, GET)
     * @param url exact URL (e.g. https://bsky.social/oauth/par)
     * @param nonce optional nonce (if provided by server on 401)
     */
    public static String buildDPoP(String method, String url, String nonce) {
        try {
            if (privateECKey == null) init();

            // Header: typ=dpop+jwt, alg=ES256, jwk=public JWK object
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                    .type(new JOSEObjectType("dpop+jwt"))
                    .jwk(publicJWK)
                    .build();

            // Claims: jti, htm, htu, iat, optional nonce
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(Instant.now()))
                    .claim("htm", method.toUpperCase())
                    .claim("htu", new URI(url).toString());

            if (nonce != null && !nonce.isEmpty()) {
                claims.claim("nonce", nonce);
            }

            SignedJWT jwt = new SignedJWT(header, claims.build());
            ECDSASigner signer = new ECDSASigner(privateECKey);
            jwt.sign(signer);

            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Error creating DPoP proof", e);
        }
    }

    // Optional: getter for public JWK
    public static ECKey getPublicJWK() {
        if (publicJWK == null) init();
        return publicJWK;
    }
}
