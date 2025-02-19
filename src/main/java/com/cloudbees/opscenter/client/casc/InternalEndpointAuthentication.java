package com.cloudbees.opscenter.client.casc;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import edu.umd.cs.findbugs.annotations.NonNull;

@Restricted(NoExternalUse.class)
public class InternalEndpointAuthentication {
    private static final Logger LOGGER = Logger.getLogger(InternalEndpointAuthentication.class.getName());

    public static final String CIPHER_TRANSFORMATION = "RSA/ECB/PKCS1Padding";
    public static final String WRAPPED_KEY_ALGORITHM = "HmacSHA512";
    public static final String SINGATURE_ALGORITHM = "HmacSHA256";

    public static final String HMAC_HEADER = "X-cbci-token";
    public static final String HMAC_MESSAGE_HEADER = "X-cbci-token-message";

    private static final String BUNDLE_LOCATION = System.getProperty("core.casc.config.bundle");

    private static final String WRAPPED_TOKEN_FILE_NAME = ".wrappedToken";
    private static final String WRAPPED_TOKEN_PATH = ".retriever-cache/" + WRAPPED_TOKEN_FILE_NAME;
    private static final String WRAPPED_TOKEN_CONFIGURABLE_PATH = System.getProperty("core.casc.retriever.cache.location");

    private static InternalEndpointAuthentication INSTANCE;

    private File wrappedToken;

    private byte[] token;

    private InternalEndpointAuthentication() { }

    public static InternalEndpointAuthentication get() {
        if (INSTANCE == null) {
            InternalEndpointAuthentication newInstance = new InternalEndpointAuthentication();
            newInstance.wrappedToken = getTokenFile();
            LOGGER.log(Level.FINE, String.format("Expected token path: %s", newInstance.wrappedToken.getAbsolutePath()));
            INSTANCE = newInstance;
        }
        return INSTANCE;
    }

    public boolean validate(@NonNull HttpServletRequest req) {
        String hmacSignature = req.getHeader(HMAC_HEADER);
        if (StringUtils.isBlank(hmacSignature)) {
            LOGGER.log(Level.WARNING, "Received unsigned request, rejecting request");
            return false;
        }
        String message = req.getHeader(HMAC_MESSAGE_HEADER);
        if (StringUtils.isBlank(message)) {
            LOGGER.log(Level.WARNING, "Received signed request with no message, rejecting request");
            return false;
        }
        readToken();
        if (token == null) {
            LOGGER.log(Level.WARNING, String.format("Could not find token in %s, rejecting request", wrappedToken.getAbsolutePath()));
            return false;
        }
        hmacSignature = hmacSignature.replaceFirst("sha256=", "");
        try {
            boolean accepted = MessageDigest.isEqual(calculateSha(message), hmacSignature.getBytes(StandardCharsets.UTF_8));
            if (!accepted) {
                LOGGER.log(Level.WARNING, "Received request with invalid token, rejecting request");
            } else {
                LOGGER.log(Level.FINE, "Validated token for incoming request");
            }
            return accepted;
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            LOGGER.log(Level.WARNING, "Could not unwrap token, rejecting request", ex);
            return false;
        }
    }

    private static File getTokenFile() {
        if (StringUtils.isBlank(WRAPPED_TOKEN_CONFIGURABLE_PATH)) {
            // We're expecting wrappedToken to be in ${core.casc.config.bundle}/../.retriever-cache/.wrappedToken unless specified by property
            if (BUNDLE_LOCATION != null) { // Failsafe, should not be null
                Path parent = Paths.get(BUNDLE_LOCATION).getParent();
                if (parent != null) {
                    return parent.resolve(WRAPPED_TOKEN_PATH).toFile();
                }
            }
        }
        // Absolute path is configured, so using it
        return  Paths.get(WRAPPED_TOKEN_CONFIGURABLE_PATH).resolve(WRAPPED_TOKEN_FILE_NAME).toFile();
    }

    private byte[] calculateSha(String message) throws NoSuchAlgorithmException, InvalidKeyException{
        Mac mac = Mac.getInstance(SINGATURE_ALGORITHM);
        Key tokenKey = new SecretKeySpec(token, SINGATURE_ALGORITHM);
        mac.init(tokenKey);
        return Base64.getEncoder().encode(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }

    private void readToken() {
        if (wrappedToken != null && wrappedToken.exists()) { // We're updating the in memory token only if the file exists
            try {
                byte[] wrappedTokenBytes = FileUtils.readFileToByteArray(wrappedToken);
                token = tokenUnwrap(wrappedTokenBytes);
                LOGGER.log(Level.INFO, "Retriever communication token updated");
            }catch (IOException ex) {
                LOGGER.log(Level.WARNING, "Could not manipulate wrapped token file (maybe permissions?)", ex);
            }
        } 
    }

    private byte[] tokenUnwrap(byte[] wrappedToken) {
        try {
            InstanceIdentity identity = new InstanceIdentity();
            Cipher c = Cipher.getInstance(CIPHER_TRANSFORMATION);
            c.init(Cipher.UNWRAP_MODE, identity.getPrivate());
            SecretKey unwrappedToken = (SecretKey) c.unwrap(wrappedToken, WRAPPED_KEY_ALGORITHM, Cipher.SECRET_KEY);
            return unwrappedToken.getEncoded();
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Could not get instance identity, token-authenticated endpoints will not be available", ex);
            return null;
        } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException ex){
            LOGGER.log(Level.WARNING, "Could not unwrap token, token-authenticated endpoints will not be available", ex);
            return null;
        }
    }
}
