package com.cloudbees.opscenter.client.casc;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;

/**
 * Utility class to reduce code duplication when testing InternalEndpointAuthentication bits
 */
public class InternalEndpointAuthTestHelper {
    public static String calculateSha(String message, String token) throws Exception {
        Mac mac = Mac.getInstance(InternalEndpointAuthentication.SINGATURE_ALGORITHM);
        Key tokenKey = new SecretKeySpec(token.getBytes(), InternalEndpointAuthentication.SINGATURE_ALGORITHM);
        mac.init(tokenKey);
        byte[] bytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] wrapInPublicKey(Key publicKey, String token) throws Exception{
        Cipher c = Cipher.getInstance(InternalEndpointAuthentication.CIPHER_TRANSFORMATION);
        c.init(Cipher.WRAP_MODE, publicKey);
        Key sessionKey = new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), InternalEndpointAuthentication.WRAPPED_KEY_ALGORITHM);
        return c.wrap(sessionKey);
    }

}
