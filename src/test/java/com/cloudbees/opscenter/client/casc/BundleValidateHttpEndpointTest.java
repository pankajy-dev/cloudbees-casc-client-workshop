package com.cloudbees.opscenter.client.casc;

import com.google.common.net.HttpHeaders;
import hudson.model.User;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.logging.Level;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BundleValidateHttpEndpointTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Rule
    public LoggerRule logger = new LoggerRule();

    private User admin;
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

        user = realm.createAccount("user", "password");
        rule.jenkins.setSecurityRealm(realm);
        authorizationStrategy.add(Jenkins.READ, user.getId());
        rule.jenkins.setAuthorizationStrategy(authorizationStrategy);
    }

    @Test
    public void smokes() throws Exception {
        // Without permissions
        HttpURLConnection conn = post("valid-bundle.zip", user);
        assertThat("User user should not have permissions", conn.getResponseCode(), is(HttpServletResponse.SC_FORBIDDEN));
        conn.disconnect();

        // Valid without warnings
        logger.record(ConfigurationUpdaterHelper.class, Level.INFO).capture(5);
        conn = post("valid-bundle.zip", admin);
        assertThat("User admin should have permissions", conn.getResponseCode(), is(HttpServletResponse.SC_OK));
        JSONObject response = JSONObject.fromObject(readResponse(conn.getInputStream()));
        assertTrue("valid-bundle.zip should be valid", response.getBoolean("valid"));
        assertFalse("valid-bundle.zip should not have validation messages", response.containsKey("validation-messages"));
        assertThat("Logs should contain the commit", logger.getMessages().contains("Validating bundles associated with commit COMMIT_HASH"));
        conn.disconnect();

        // Valid but with warnings
        conn = post("only-with-warnings.zip", admin);
        assertThat("User admin should have permissions", conn.getResponseCode(), is(HttpServletResponse.SC_OK));
        response = JSONObject.fromObject(readResponse(conn.getInputStream()));
        assertTrue("only-with-warnings.zip should be valid", response.getBoolean("valid"));
        assertTrue("only-with-warnings.zip should have validation messages", response.containsKey("validation-messages"));
        assertThat("only-with-warnings.zip should have validation messages", response.getJSONArray("validation-messages"), contains("WARNING - [JCASC] - It is impossible to validate the Jenkins configuration. Please review your Jenkins and plugin configurations. Reason: jenkins: error configuring 'jenkins' with class io.jenkins.plugins.casc.core.JenkinsConfigurator configurator"));
        conn.disconnect();

        // No valid
        conn = post("invalid-bundle.zip", admin);
        assertThat("User admin should have permissions", conn.getResponseCode(), is(HttpServletResponse.SC_OK));
        response = JSONObject.fromObject(readResponse(conn.getInputStream()));
        assertFalse("invalid-bundle.zip should not be valid", response.getBoolean("valid"));
        assertTrue("invalid-bundle.zip should have validation messages", response.containsKey("validation-messages"));
        assertThat("invalid-bundle.zip should have validation messages", response.getJSONArray("validation-messages"),
                containsInAnyOrder(
                        "ERROR - [APIVAL] - 'apiVersion' property in the bundle.yaml file must be an integer.",
                        "WARNING - [JCASC] - It is impossible to validate the Jenkins configuration. Please review your Jenkins and plugin configurations. Reason: jenkins: error configuring 'jenkins' with class io.jenkins.plugins.casc.core.JenkinsConfigurator configurator"
                ));
        conn.disconnect();

        // Not a zip file
        conn = post("bundle.yaml", admin);
        assertThat("bundle.yaml is not a zip", conn.getResponseCode(), is(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
        response = JSONObject.fromObject(readResponse(conn.getErrorStream()));
        assertThat("bundle.yaml is not a zip", response.getString("error"), is("Invalid zip file: Cannot be unzipped"));
        conn.disconnect();

        // Without descriptor
        conn = post("without-descriptor.zip", admin);
        assertThat("without-descriptor.zip should not have descriptor", conn.getResponseCode(), is(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
        response = JSONObject.fromObject(readResponse(conn.getErrorStream()));
        assertThat("without-descriptor.zip should not have descriptor", response.getString("error"), is("Invalid bundle - Missing descriptor"));
        conn.disconnect();
    }

    private String readResponse(InputStream is) throws Exception {
        try(BufferedReader br = new BufferedReader(new InputStreamReader(is, "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            return response.toString();
        }
    }

    private HttpURLConnection post(String bundle, User user) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(rule.getURL(), "casc-bundle-mgnt/casc-bundle-validate?commit=COMMIT_HASH").openConnection();
        conn.setRequestMethod("POST");
        String apiToken = rule.createApiToken(user);
        org.apache.commons.codec.binary.Base64.encodeBase64String((user.getId() + ":" +  apiToken).getBytes(StandardCharsets.UTF_8));
        String authCode = new String(Base64.getEncoder().encode((user.getId() + ":" +  apiToken).getBytes(StandardCharsets.UTF_8)));
        conn.setRequestProperty(HttpHeaders.AUTHORIZATION, "Basic " + authCode);
        conn.setRequestProperty("Content-Type", "application/zip; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        try (OutputStream out = conn.getOutputStream()) {
            Files.copy(Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/BundleValidateHttpEndpointTest/", bundle), out);
        }

        return conn;
    }
}