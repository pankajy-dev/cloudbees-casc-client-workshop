package com.cloudbees.opscenter.client.casc;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import javax.servlet.http.HttpServletResponse;

import com.google.common.net.HttpHeaders;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.model.User;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import jenkins.model.Jenkins;

import com.cloudbees.jenkins.plugins.casc.permissions.CascPermission;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Checks permissions for {@link BundleValidateHttpEndpointTest} (response content there) but without {@link Jenkins#SYSTEM_READ}
 */
@Issue("BEE-49270")
public class BundleValidateHttpEndpointWithoutSystemReadTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    private User admin;
    private User cascAdmin;
    private User user;

    @Before
    public void setUp() throws Exception {
        rule.jenkins.setSecurityRealm(new HudsonPrivateSecurityRealm(false, false, null));
        rule.jenkins.setAuthorizationStrategy(new ProjectMatrixAuthorizationStrategy());

        HudsonPrivateSecurityRealm realm = (HudsonPrivateSecurityRealm) rule.jenkins.getSecurityRealm();
        admin = realm.createAccount("admin", "password");
        rule.jenkins.setSecurityRealm(realm);
        ProjectMatrixAuthorizationStrategy authorizationStrategy = (ProjectMatrixAuthorizationStrategy) rule.jenkins.getAuthorizationStrategy();
        authorizationStrategy.add(Jenkins.ADMINISTER, admin.getId());
        rule.jenkins.setAuthorizationStrategy(authorizationStrategy);

        cascAdmin = realm.createAccount("cascAdmin", "password");
        rule.jenkins.setSecurityRealm(realm);
        authorizationStrategy.add(CascPermission.CASC_ADMIN, cascAdmin.getId());
        authorizationStrategy.add(Jenkins.READ, cascAdmin.getId());
        rule.jenkins.setAuthorizationStrategy(authorizationStrategy);

        user = realm.createAccount("user", "password");
        rule.jenkins.setSecurityRealm(realm);
        authorizationStrategy.add(CascPermission.CASC_READ, user.getId());
        authorizationStrategy.add(Jenkins.READ, admin.getId());
        rule.jenkins.setAuthorizationStrategy(authorizationStrategy);
    }

    @Test
    public void smokes() throws Exception {
        // Without permissions
        HttpURLConnection conn = post("valid-bundle.zip", user);
        assertThat("User user should not have permissions", conn.getResponseCode(), is(HttpServletResponse.SC_FORBIDDEN));
        conn.disconnect();

        // With CASC_ADMIN permissions
        conn = post("valid-bundle.zip", cascAdmin);
        assertThat("User cascAdmin should not have permissions", conn.getResponseCode(), is(HttpServletResponse.SC_FORBIDDEN));
        conn.disconnect();

        // With Jenkins.ADMINISTER permission
        conn = post("valid-bundle.zip", admin);
        assertThat("User admin should have permissions", conn.getResponseCode(), is(HttpServletResponse.SC_OK));
        conn.disconnect();
    }

    private HttpURLConnection post(String bundle, User user) throws Exception {
        final String spec = "casc-bundle-mgnt/casc-bundle-validate?commit=COMMIT_HASH";

        HttpURLConnection conn = (HttpURLConnection) new URL(rule.getURL(), spec).openConnection();
        conn.setRequestMethod("POST");
        String apiToken = rule.createApiToken(user);
        org.apache.commons.codec.binary.Base64.encodeBase64String((user.getId() + ":" +  apiToken).getBytes(StandardCharsets.UTF_8));
        String authCode = new String(Base64.getEncoder().encode((user.getId() + ":" +  apiToken).getBytes(StandardCharsets.UTF_8)));
        conn.setRequestProperty(HttpHeaders.AUTHORIZATION, "Basic " + authCode);
        conn.setRequestProperty("Content-Type", "application/zip; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        try (OutputStream out = conn.getOutputStream()) {
            Files.copy(Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/BundleValidateHttpEndpointTest/",
                bundle
            ), out);
        }

        return conn;
    }
}