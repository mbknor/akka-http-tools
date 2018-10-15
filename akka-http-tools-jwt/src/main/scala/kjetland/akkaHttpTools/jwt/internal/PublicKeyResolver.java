package kjetland.akkaHttpTools.jwt.internal;

import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.SigningKeyResolver;

import java.security.Key;

public class PublicKeyResolver implements SigningKeyResolver {

    private final JwkProvider jwkProvider;

    public PublicKeyResolver(JwkProvider jwkProvider) {
        this.jwkProvider = jwkProvider;
    }

    @Override
    public Key resolveSigningKey(JwsHeader header, Claims claims) {
        return resolveKey(header.getKeyId());
    }

    @Override
    public Key resolveSigningKey(JwsHeader header, String plaintext) {
        return resolveKey(header.getKeyId());
    }

    private Key resolveKey(String kid) {
        try {
            return jwkProvider.get(kid).getPublicKey();
        } catch (JwkException e) {
            throw new RuntimeException(e);
        }
    }
}
