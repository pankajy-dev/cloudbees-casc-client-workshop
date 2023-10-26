package com.cloudbees.opscenter.client.casc;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.Key;
import java.util.Base64;
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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class InternalEndpointAuthenticationWithConfigTest {
    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public LoggerRule logger = new LoggerRule();

    @After
    public void tearDown() {
        System.clearProperty("core.casc.config.bundle");
        System.clearProperty("core.casc.retriever.cache.location");
    }

    @Test
    public void internalAuthenticationWithConfigPathTest() throws Exception {
        System.setProperty("core.casc.config.bundle", temporaryFolder.newFolder().getCanonicalPath());
        String configurablePath = temporaryFolder.newFolder().getCanonicalPath();
        System.setProperty("core.casc.retriever.cache.location", configurablePath);
        assertThat("Properties point to a different folder", System.getProperty("core.casc.config.bundle"), not(equalTo(System.getProperty("core.casc.retriever.cache.location"))));

        // Writing the token in configured path
        InstanceIdentity instanceIdentity = new InstanceIdentity();

        // Let's wrap a token with the pub key
        byte[] wrappedTokenBytes = wrapInPublicKey(instanceIdentity.getPublic(), "token");
        File wrappedTokenFile = Paths.get(configurablePath).resolve(".wrappedToken").toFile();
        FileUtils.writeByteArrayToFile(wrappedTokenFile, wrappedTokenBytes);

        // Validation should pass
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getHeader("X-cbci-token")).thenReturn(calculateSha("body", "token"));
        Mockito.when(request.getHeader(InternalEndpointAuthentication.HMAC_MESSAGE_HEADER)).thenReturn("body");
        boolean validationPasses = InternalEndpointAuthentication.get().validate(request);
        assertThat("Validation passes", validationPasses, is(true));
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
        c.init(Cipher.WRAP_MODE, publicKey);
        Key sessionKey = new SecretKeySpec(Base64.getEncoder().encode(token.getBytes(StandardCharsets.UTF_8)), "RSA");
        return c.wrap(sessionKey);
    }
}
