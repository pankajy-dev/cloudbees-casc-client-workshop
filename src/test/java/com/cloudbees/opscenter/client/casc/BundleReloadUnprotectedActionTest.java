package com.cloudbees.opscenter.client.casc;

import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Key;
import java.util.Base64;
import java.util.logging.Level;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import edu.umd.cs.findbugs.annotations.NonNull;
import net.sf.json.JSONObject;

import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.ProjectMatrixAuthorizationStrategy;

import com.cloudbees.jenkins.cjp.installmanager.AbstractIMTest;
import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.plugins.updates.envelope.Envelope;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopeProvider;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class BundleReloadUnprotectedActionTest extends AbstractIMTest {

    public static final String CHECK_BUNDLE_UPDATE_AUTH_TOKEN = "/casc-internal/check-bundle-update";
    public static final String BUNDLE_VALIDATE_AUTH_TOKEN = "/casc-internal/casc-bundle-validate/";

    @Rule
    public final CJPRule rule;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public LoggerRule loggerRule = new LoggerRule();

    public BundleReloadUnprotectedActionTest() {
        this.rule = new CJPRule(this.tmp);
    }

    @Override
    protected CJPRule rule() {
        return this.rule;
    }

    private static final String BUNDLE_YAML = "apiVersion: \"1\"\n" + "id: \"bundle\"\n" + "description: \"This is a config bundle\"\n" + "version: \"1\"";

    @Before
    public void setup() {
        initializeRealm(rule);
    }

    @Test
    @WithEnvelope(TwoPluginsV2dot289.class)
    public void checkResponseIsAcceptedWithTokenAndSecurityInBundleUpdate() throws Exception {
        // We don't need a bundle to check these endpoints, just the path
        File bundlePath = temporaryFolder.newFolder();
        FileUtils.writeStringToFile(bundlePath.toPath().resolve("bundle.yaml").toFile(), BUNDLE_YAML, Charset.defaultCharset());
        System.setProperty("core.casc.config.bundle", bundlePath.getAbsolutePath());

        CJPRule.WebClient wc = rule.createWebClient();
        wc.getOptions().setPrintContentOnFailingStatusCode(false);

        // Accessing any other non unprotected endpoint should throw a 403
        loggerRule.record(BundleReloadAction.class, Level.WARNING);
        loggerRule.capture(1);
        JenkinsRule.JSONWebResponse resp = wc.getJSON(rule.getURL() + "/casc-bundle-mgmt/check-bundle-update");
        assertThat("We should get a 403", resp.getStatusCode(), is(HttpServletResponse.SC_FORBIDDEN));
        assertThat("Due to secured realm we're not reaching endpoint", loggerRule.getMessages().isEmpty(), is(true));

        // Setting up token
        InstanceIdentity instanceIdentity = new InstanceIdentity();
        String validToken = "token";
        FileUtils.writeByteArrayToFile(bundlePath.toPath().resolve(".wrappedToken").toFile(), wrapInPublicKey(instanceIdentity.getPublic(), validToken));
        String validSha = calculateSha("legitMessage", validToken);

        loggerRule.record(InternalEndpointAuthentication.class, Level.INFO);
        loggerRule.capture(1);

        // Invoking endpoint without a token should return an error
        resp = wc.getJSON(rule.getURL() + CHECK_BUNDLE_UPDATE_AUTH_TOKEN);
        assertThat("We should get a 403", resp.getStatusCode(), is(HttpServletResponse.SC_FORBIDDEN));
        assertThat("Unprotected endpoint, we have a log checking token", loggerRule.getMessages().isEmpty(), is(false));

        // Invoking endpoint with an invalid token should also return an error
        wc.addRequestHeader(InternalEndpointAuthentication.HMAC_HEADER, "sha256=invalid");
        resp = wc.getJSON(rule.getURL() + CHECK_BUNDLE_UPDATE_AUTH_TOKEN);
        assertThat("We should get a 403", resp.getStatusCode(), is(HttpServletResponse.SC_FORBIDDEN));

        // Token not matching message hash is rejected
        wc.addRequestHeader(InternalEndpointAuthentication.HMAC_HEADER, "sha256=" + validSha);
        wc.addRequestHeader(InternalEndpointAuthentication.HMAC_MESSAGE_HEADER, "unexpectedMessage");
        resp = wc.getJSON(rule.getURL() + CHECK_BUNDLE_UPDATE_AUTH_TOKEN);
        assertThat("We should get a 403", resp.getStatusCode(), is(HttpServletResponse.SC_FORBIDDEN));

        // Expected message with invalid sha is rejected
        wc.addRequestHeader(InternalEndpointAuthentication.HMAC_HEADER, "sha256=invalid");
        wc.addRequestHeader(InternalEndpointAuthentication.HMAC_MESSAGE_HEADER, "legitMessage");
        resp = wc.getJSON(rule.getURL() + CHECK_BUNDLE_UPDATE_AUTH_TOKEN);
        assertThat("We should get a 403", resp.getStatusCode(), is(HttpServletResponse.SC_FORBIDDEN));

        // Expected message with valid sha is accepted
        wc.addRequestHeader(InternalEndpointAuthentication.HMAC_HEADER, "sha256=" + validSha);
        wc.addRequestHeader(InternalEndpointAuthentication.HMAC_MESSAGE_HEADER, "legitMessage");
        WebRequest request = new WebRequest(new URL(rule.getURL(), rule.contextPath + CHECK_BUNDLE_UPDATE_AUTH_TOKEN), HttpMethod.GET);
        resp = wc.getJSON(rule.getURL() + CHECK_BUNDLE_UPDATE_AUTH_TOKEN);
        assertThat("We should get a 200", resp.getStatusCode(), is(HttpServletResponse.SC_OK));
    }

    @Test
    @WithEnvelope(TwoPluginsV2dot289.class)
    public void checkResponseIsAcceptedWithTokenAndSecurityInBundleValidate() throws Exception {
        // We don't need a bundle to check these endpoints, just the path
        File bundlePath = temporaryFolder.newFolder();
        System.setProperty("core.casc.config.bundle", bundlePath.getAbsolutePath());

        CJPRule.WebClient wc = rule.createWebClient();
        wc.getOptions().setPrintContentOnFailingStatusCode(true);

        // Accessing any other non unprotected endpoint should throw a 403
        loggerRule.record(BundleReloadAction.class, Level.WARNING);
        loggerRule.capture(1);
        WebResponse resp = wc.getJSON(rule.getURL() + "/casc-bundle-mgmt/casc-bundle-validate");
        assertThat("We should get a 403", resp.getStatusCode(), is(HttpServletResponse.SC_FORBIDDEN));
        assertThat("Due to secured realm we're not reaching endpoint", loggerRule.getMessages().isEmpty(), is(true));

        // Setting up token
        InstanceIdentity instanceIdentity = new InstanceIdentity();
        String validToken = "token";
        FileUtils.writeByteArrayToFile(bundlePath.toPath().resolve(".wrappedToken").toFile(), wrapInPublicKey(instanceIdentity.getPublic(), validToken));
        String validSha = calculateSha("legitMessage", validToken);

        loggerRule.record(InternalEndpointAuthentication.class, Level.INFO);
        loggerRule.capture(1);

        // Invoking endpoint without a token should return an error
        resp = wc.postJSON(rule.getURL() + BUNDLE_VALIDATE_AUTH_TOKEN + "/?commit=aaa", new JSONObject());
        assertThat("We should get a 403", resp.getStatusCode(), is(HttpServletResponse.SC_FORBIDDEN));
        assertThat("Unprotected endpoint, we have a log checking token", loggerRule.getMessages().isEmpty(), is(false));

        // Invoking endpoint with an invalid token should also return an error
        wc.addRequestHeader(InternalEndpointAuthentication.HMAC_HEADER, "sha256=invalid");
        resp = wc.postJSON(rule.getURL() + BUNDLE_VALIDATE_AUTH_TOKEN, new JSONObject());
        assertThat("We should get a 403", resp.getStatusCode(), is(HttpServletResponse.SC_FORBIDDEN));

        // Token not matching message hash is rejected
        wc.addRequestHeader(InternalEndpointAuthentication.HMAC_HEADER, "sha256=" + validSha);
        wc.addRequestHeader(InternalEndpointAuthentication.HMAC_MESSAGE_HEADER, "unexpectedMessage");
        resp = wc.postJSON(rule.getURL() + BUNDLE_VALIDATE_AUTH_TOKEN, new JSONObject());
        assertThat("We should get a 403", resp.getStatusCode(), is(HttpServletResponse.SC_FORBIDDEN));

        // Expected message with invalid sha is rejected
        wc.addRequestHeader(InternalEndpointAuthentication.HMAC_HEADER, "sha256=invalid");
        wc.addRequestHeader(InternalEndpointAuthentication.HMAC_MESSAGE_HEADER, "legitMessage");
        resp = wc.postJSON(rule.getURL() + BUNDLE_VALIDATE_AUTH_TOKEN, new JSONObject());
        assertThat("We should get a 403", resp.getStatusCode(), is(HttpServletResponse.SC_FORBIDDEN));

        // Expected message with valid sha is accepted
        wc.addRequestHeader(InternalEndpointAuthentication.HMAC_HEADER, "sha256=" + validSha);
        wc.addRequestHeader(InternalEndpointAuthentication.HMAC_MESSAGE_HEADER, "legitMessage");
        resp = wc.postJSON(rule.getURL() + BUNDLE_VALIDATE_AUTH_TOKEN + "?commit=aaaaa", new JSONObject());
        // We're getting a 500 instead of a 200 because we're not providing a zip file
        assertThat("We should get a 500", resp.getStatusCode(), is(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));

        // Posting with a real dummy bundle, using HttpConnection instead of wc
        HttpURLConnection conn = (HttpURLConnection) new URL(rule.getURL(), rule.contextPath + BUNDLE_VALIDATE_AUTH_TOKEN).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/zip; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty(InternalEndpointAuthentication.HMAC_HEADER, "sha256=" + validSha);
        conn.setRequestProperty(InternalEndpointAuthentication.HMAC_MESSAGE_HEADER, "legitMessage");
        conn.setDoOutput(true);
        try (OutputStream out = conn.getOutputStream()) {
            Files.copy(Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/BundleReloadUnprotectedActionTest/valid-bundle.zip"), out);
        }
        assertThat("We should get a 200", conn.getResponseCode(), is(HttpServletResponse.SC_OK));
    }

    private static void initializeRealm(CJPRule j){
        j.jenkins.setSecurityRealm(new HudsonPrivateSecurityRealm(false, false, null));
        j.jenkins.setAuthorizationStrategy(new ProjectMatrixAuthorizationStrategy());
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

    @After
    public void after() {
        System.clearProperty("core.casc.config.bundle");
    }

    public static final class TwoPluginsV2dot289 implements TestEnvelopeProvider {
        public TwoPluginsV2dot289() {
        }

        @NonNull
        public Envelope call() {
            return TestEnvelopes.e("2.289.1", 1, "", TestEnvelopes.beer12(), TestEnvelopes.p("manage-permission", "1.0.1"));
        }
    }
}
