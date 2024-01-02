package com.cloudbees.opscenter.client.casc;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.Key;
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
        File bundleHome = temporaryFolder.newFolder();
        // we can not use WithSystemProperty in a temporary folder
        System.setProperty("core.casc.config.bundle", bundleHome.getAbsolutePath());

        InstanceIdentity instanceIdentity = new InstanceIdentity();

        // Let's wrap a token with the pub key
        byte[] wrappedTokenBytes = InternalEndpointAuthTestHelper.wrapInPublicKey(instanceIdentity.getPublic(), "token");
        File wrappedTokenFile = bundleHome.toPath().getParent().resolve(".retriever-cache/.wrappedToken").toFile();
        FileUtils.writeByteArrayToFile(wrappedTokenFile, wrappedTokenBytes);

        InternalEndpointAuthentication internalEndpointAuthentication = InternalEndpointAuthentication.get();

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getHeader(InternalEndpointAuthentication.HMAC_HEADER)).thenReturn("invalidHeader");
        Mockito.when(request.getHeader(InternalEndpointAuthentication.HMAC_MESSAGE_HEADER)).thenReturn("body");
        logger.record(InternalEndpointAuthentication.class, Level.INFO);
        logger.capture(2);
        assertThat("Wrapped token file exists", wrappedTokenFile.exists(), is(true));
        boolean validationPasses = internalEndpointAuthentication.validate(request);
        boolean tokenProcessedLog = logger.getRecords().stream().filter(log -> log.getLevel().equals(Level.INFO)).anyMatch(record -> record.getMessage().contains("token updated"));
        assertThat("token file has been processed", tokenProcessedLog, is(true));
        assertThat("Validation doesn't pass", validationPasses, is(false));

        Mockito.when(request.getHeader("X-cbci-token")).thenReturn(null);
        logger.capture(2);
        validationPasses = internalEndpointAuthentication.validate(request);
        tokenProcessedLog = logger.getRecords().stream().filter(log -> log.getLevel().equals(Level.INFO)).anyMatch(record -> record.getMessage().contains("token updated"));
        assertThat("Token was not updated", tokenProcessedLog, is(false));
        assertThat("Validation doesn't pass", validationPasses, is(false));

        // Generating a valid token
        Mockito.when(request.getHeader("X-cbci-token")).thenReturn(InternalEndpointAuthTestHelper.calculateSha("body", "token"));
        validationPasses = internalEndpointAuthentication.validate(request);
        assertThat("Validation passes", validationPasses, is(true));

        // Regenerate the token to simulate requester token expiration
        wrappedTokenBytes = InternalEndpointAuthTestHelper.wrapInPublicKey(instanceIdentity.getPublic(), "anotherToken");
        FileUtils.writeByteArrayToFile(wrappedTokenFile, wrappedTokenBytes);

        logger.capture(2);
        validationPasses = internalEndpointAuthentication.validate(request);
        tokenProcessedLog = logger.getRecords().stream().filter(log -> log.getLevel().equals(Level.INFO)).anyMatch(record -> record.getMessage().contains("token updated"));
        assertThat("token file has been processed", tokenProcessedLog, is(true));
        assertThat("Validation fails, as token has been refreshed", validationPasses, is(false));
    }
}
