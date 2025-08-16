package com.example.streamsplunkwebhook.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

@Component
public class SignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(SignatureVerifier.class);
    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

    public boolean verifySignature(String rawBody, String signature, String apiSecret) {
        if (rawBody == null || signature == null || apiSecret == null) {
            log.warn("Missing rawBody, signature, or apiSecret for verification.");
            return false;
        }

        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            mac.init(secretKeySpec);

            byte[] hmacSha256 = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String calculatedSignature = toHexString(hmacSha256);

            log.debug("Received X-Signature: {}", signature);
            log.debug("Calculated Signature: {}", calculatedSignature);
            log.debug("API Secret used: {}", apiSecret);
            log.debug("Raw Body (decoded): {}", rawBody);

            // Use constant-time comparison to prevent timing attacks
            return MessageDigestComparator.isEqual(calculatedSignature.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error during signature verification: {}", e.getMessage(), e);
            return false;
        }
    }

    private String toHexString(byte[] bytes) {
        Formatter formatter = new Formatter();
        for (byte b : bytes) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    // Simple constant-time comparison for security (prevents timing attacks)
    private static class MessageDigestComparator {
        public static boolean isEqual(byte[] digest1, byte[] digest2) {
            if (digest1.length != digest2.length) {
                return false;
            }
            int result = 0;
            for (int i = 0; i < digest1.length; i++) {
                result |= digest1[i] ^ digest2[i];
            }
            return result == 0;
        }
    }
}