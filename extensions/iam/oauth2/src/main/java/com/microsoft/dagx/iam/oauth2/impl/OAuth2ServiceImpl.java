package com.microsoft.dagx.iam.oauth2.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import com.microsoft.dagx.iam.oauth2.spi.OAuth2Service;
import com.microsoft.dagx.iam.oauth2.spi.TokenResult;
import com.microsoft.dagx.iam.oauth2.spi.VerificationResult;
import com.microsoft.dagx.spi.DagxException;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.microsoft.dagx.iam.oauth2.impl.Fingerprint.sha1Base64Fingerprint;

public class OAuth2ServiceImpl implements OAuth2Service {
    private static final long EXPIRATION = 300; // 5 minutes

    private static final String GRANT_TYPE = "client_credentials";
    private static final String ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
    private static final String CONTENT_TYPE = "application/x-www-form-urlencoded";

    private OAuth2Configuration configuration;

    private RSAKeyProvider credentialProvider;
    private JWTVerifier verifier;

    public OAuth2ServiceImpl(OAuth2Configuration configuration) {
        this.configuration = configuration;
        credentialProvider = new PairedProviderWrapper(configuration.getPrivateKeyResolver(), configuration.getCertificateResolver(), configuration.getPrivateKeyAlias());

        RSAKeyProvider verifierProvider = new PublicKeyProviderWrapper(configuration.getIdentityProviderKeyResolver());
        verifier = JWT.require(Algorithm.RSA256(verifierProvider)).build();
    }

    @Override
    public TokenResult obtainClientCredentials(String scope) {

        String assertion = buildJwt();

        RequestBody requestBody = new FormBody.Builder()
                .add("client_assertion_type", ASSERTION_TYPE)
                .add("grant_type", GRANT_TYPE)
                .add("client_assertion", assertion)
                .add("scope", scope)
                .build();

        Request request = new Request.Builder().url(configuration.getTokenUrl()).addHeader("Content-Type", CONTENT_TYPE).post(requestBody).build();

        OkHttpClient client = createClient();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                try (var body = response.body()) {
                    String message = body == null ? "<empty body>" : body.string();
                    return TokenResult.Builder.newInstance().error(message).build();
                }
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                return TokenResult.Builder.newInstance().error("<empty token body>").build();
            }

            String responsePayload = responseBody.string();
            @SuppressWarnings("unchecked") LinkedHashMap<String, Object> deserialized = configuration.getObjectMapper().readValue(responsePayload, LinkedHashMap.class);
            String token = (String) deserialized.get("access_token");
            long expiresIn = ((Integer) deserialized.get("expires_in")).longValue();
            return TokenResult.Builder.newInstance().token(token).expiresIn(expiresIn).build();
        } catch (IOException e) {
            throw new DagxException(e);
        }
    }

    @Override
    public VerificationResult verifyJwtToken(String token) {
        try {
            DecodedJWT jwt = verifier.verify(token);
            return VerificationResult.VALID_TOKEN;
        } catch (JWTVerificationException e) {
            return new VerificationResult(e.getMessage());
        }
    }

    private OkHttpClient createClient() {
        return new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build();
    }

    private String buildJwt() {
        try {
            X509Certificate certificate = configuration.getCertificateResolver().resolveCertificate(configuration.getPublicCertificateAlias());
            if (certificate == null) {
                throw new DagxException("Public certificate not found: " + configuration.getPublicCertificateAlias());
            }

            JWTCreator.Builder jwtBuilder = JWT.create();

            jwtBuilder.withHeader(Map.of("x5t", sha1Base64Fingerprint(certificate.getEncoded())));

            jwtBuilder.withAudience(configuration.getAudience());

            jwtBuilder.withIssuer(configuration.getClientId());
            jwtBuilder.withSubject(configuration.getClientId());
            jwtBuilder.withJWTId(UUID.randomUUID().toString());
            jwtBuilder.withNotBefore(new Date());
            jwtBuilder.withExpiresAt(Date.from(Instant.now().plusSeconds(EXPIRATION)));

            return jwtBuilder.sign(Algorithm.RSA256(credentialProvider));
        } catch (GeneralSecurityException e) {
            throw new DagxException(e);
        }
    }
}