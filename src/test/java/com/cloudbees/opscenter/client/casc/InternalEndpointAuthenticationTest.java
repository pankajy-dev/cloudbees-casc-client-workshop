package com.cloudbees.opscenter.client.casc;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.logging.Level;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.FileUtils;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

public class InternalEndpointAuthenticationTest {
    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public LoggerRule logger = new LoggerRule();

    @After
    public void tearDown() {
        System.clearProperty("core.casc.config.bundle");
    }

    @Test
    public void internalEndpointValidatesTokenTest() throws Exception {
        // we can not use WithSystemProperty in a temporary folder
        System.setProperty("core.casc.config.bundle", temporaryFolder.getRoot().getAbsolutePath());

        InstanceIdentity instanceIdentity = new InstanceIdentity();

        // Let's wrap a token with the pub key
        byte[] wrappedTokenBytes = wrapInPublicKey(instanceIdentity.getPublic(), "token");
        File wrappedTokenFile = temporaryFolder.getRoot().toPath().resolve(".wrappedToken").toFile();
        FileUtils.writeByteArrayToFile(wrappedTokenFile, wrappedTokenBytes);

        InternalEndpointAuthentication internalEndpointAuthentication = InternalEndpointAuthentication.get();

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getHeader("X-cbci-token")).thenReturn("invalidHeader");
        logger.record(InternalEndpointAuthentication.class, Level.INFO);
        logger.capture(2);
        assertThat("Wrapped token file exists", wrappedTokenFile.exists(), is(true));
        boolean validationPasses = internalEndpointAuthentication.validate(request, "body");
        boolean tokenProcessedLog = logger.getRecords().stream().filter(log -> log.getLevel().equals(Level.INFO)).anyMatch(record -> record.getMessage().contains("token updated"));
        assertThat("token file has been processed", tokenProcessedLog, is(true));
        assertThat("Validation doesn't pass", validationPasses, is(false));
        assertThat("Wrapped token no longer exists", wrappedTokenFile.exists(), is(false));

        Mockito.when(request.getHeader("X-cbci-token")).thenReturn(null);
        logger.capture(2);
        validationPasses = internalEndpointAuthentication.validate(request, "body");
        tokenProcessedLog = logger.getRecords().stream().filter(log -> log.getLevel().equals(Level.INFO)).anyMatch(record -> record.getMessage().contains("token updated"));
        assertThat("Token was not updated", tokenProcessedLog, is(false));
        assertThat("Validation doesn't pass", validationPasses, is(false));

        // Generating a valid token
        Mockito.when(request.getHeader("X-cbci-token")).thenReturn(calculateSha("body", "token"));
        validationPasses = internalEndpointAuthentication.validate(request, "body");
        assertThat("Validation passes", validationPasses, is(true));

        // Regenerate the token to simulate requester token expiration
        wrappedTokenBytes = wrapInPublicKey(instanceIdentity.getPublic(), "anothertoken");
        FileUtils.writeByteArrayToFile(wrappedTokenFile, wrappedTokenBytes);

        logger.capture(2);
        validationPasses = internalEndpointAuthentication.validate(request, "body");
        tokenProcessedLog = logger.getRecords().stream().filter(log -> log.getLevel().equals(Level.INFO)).anyMatch(record -> record.getMessage().contains("token updated"));
        assertThat("token file has been processed", tokenProcessedLog, is(true));
        assertThat("Validation fails, as token has been refreshed", validationPasses, is(false));
    }

    private String calculateSha(String message, String token) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        Key tokenKey = new SecretKeySpec(token.getBytes(), "HmacSHA256");
        mac.init(tokenKey);
        byte[] bytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(bytes);
    }

    private byte[] wrapInPublicKey(Key publicKey, String token) throws Exception{
        Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        X509EncodedKeySpec spec = new X509EncodedKeySpec(publicKey.getEncoded());
        c.init(Cipher.WRAP_MODE, publicKey);
        Key sessionKey = new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "RSA");
        return c.wrap(sessionKey);
    }
}
