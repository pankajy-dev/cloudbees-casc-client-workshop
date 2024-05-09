package com.cloudbees.opscenter.client.casc;

import com.google.common.net.HttpHeaders;
import hudson.model.User;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;

import net.sf.json.JSONObject;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
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

import com.cloudbees.jenkins.plugins.casc.permissions.CascPermission;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.IsIterableContaining.hasItem;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BundleValidateHttpEndpointTest {

    /**
     * Rule to restore system props after modifying them in a test: Enable the Jenkins.SYSTEM_READ permission
     */
    @ClassRule
    public static final FlagRule<String> systemReadProp = FlagRule.systemProperty("jenkins.security.SystemReadPermission", "true");

    @Rule
    public JenkinsRule rule = new JenkinsRule();
    @Rule
    public LoggerRule logger = new LoggerRule();

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
        assertThat("User cascAdmin should have permissions", conn.getResponseCode(), is(HttpServletResponse.SC_OK));
        conn.disconnect();

        // Content validation
        // Valid without warnings
        logger.record(ConfigurationUpdaterHelper.class, Level.INFO).capture(5);
        conn = post("valid-bundle.zip", admin);
        assertThat("User admin should have permissions", conn.getResponseCode(), is(HttpServletResponse.SC_OK));
        JSONObject response = JSONObject.fromObject(readResponse(conn.getInputStream()));
        assertTrue("valid-bundle.zip should be valid", response.getBoolean("valid"));
        assertTrue("valid-bundle.zip should only have info messages", response.getJSONArray("validation-messages").stream().allMatch(msg -> msg.toString().startsWith("INFO")));
        assertThat("Logs should contain the commit", logger.getMessages().contains("Validating bundles associated with commit COMMIT_HASH"));
        assertTrue("Validation response contains commit", response.containsKey("commit"));
        assertThat("Commit should contain indicated hash", response.getString("commit"), is("COMMIT_HASH"));
        conn.disconnect();

        // Valid but with warnings
        conn = post("only-with-warnings.zip", admin);
        assertThat("User admin should have permissions", conn.getResponseCode(), is(HttpServletResponse.SC_OK));
        response = JSONObject.fromObject(readResponse(conn.getInputStream()));
        assertTrue("only-with-warnings.zip should be valid", response.getBoolean("valid"));
        assertTrue("only-with-warnings.zip should have validation messages", response.containsKey("validation-messages"));
        assertThat("only-with-warnings.zip should have info messages", response.getJSONArray("validation-messages"), CasCMatchers.hasInfoMessage());
        assertThat("only-with-warnings.zip should have validation messages",
            response.getJSONArray("validation-messages"),
            hasItem(containsString("WARNING - [JCASC] - It is impossible to validate the Jenkins configuration. Please review your Jenkins and plugin configurations.")));
        conn.disconnect();

        // Valid but with warnings not quiet
        conn = post("only-with-warnings.zip", admin, false);
        assertThat("User admin should have permissions", conn.getResponseCode(), is(HttpServletResponse.SC_OK));
        response = JSONObject.fromObject(readResponse(conn.getInputStream()));
        assertTrue("only-with-warnings.zip should be valid", response.getBoolean("valid"));
        assertTrue("only-with-warnings.zip should have validation messages", response.containsKey("validation-messages"));
        assertThat("only-with-warnings should have info messages", response.getJSONArray("validation-messages"), CasCMatchers.hasInfoMessage());
        assertThat("only-with-warnings.zip should have validation messages",
            response.getJSONArray("validation-messages"),
            hasItem(containsString("WARNING - [JCASC] - It is impossible to validate the Jenkins configuration. Please review your Jenkins and plugin configurations.")));

        conn.disconnect();

        // Valid but with warnings quiet
        conn = post("only-with-warnings.zip", admin, true);
        assertThat("User admin should have permissions", conn.getResponseCode(), is(HttpServletResponse.SC_OK));
        response = JSONObject.fromObject(readResponse(conn.getInputStream()));
        assertTrue("only-with-warnings.zip should be valid", response.getBoolean("valid"));
        assertTrue("only-with-warnings.zip should have validation messages", response.containsKey("validation-messages"));
        assertThat("only-with-warnings.zip should have validation messages",
            response.getJSONArray("validation-messages"),
            hasItem(containsString("WARNING - [JCASC] - It is impossible to validate the Jenkins configuration. Please review your Jenkins and plugin configurations.")));

        assertThat("valid-bundle.zip should have info messages", response.getJSONArray("validation-messages"), not(CasCMatchers.hasInfoMessage()));
        conn.disconnect();

        // No valid
        conn = post("invalid-bundle.zip", admin);
        assertThat("User admin should have permissions", conn.getResponseCode(), is(HttpServletResponse.SC_OK));
        response = JSONObject.fromObject(readResponse(conn.getInputStream()));
        assertFalse("invalid-bundle.zip should not be valid", response.getBoolean("valid"));
        assertTrue("invalid-bundle.zip should have validation messages", response.containsKey("validation-messages"));
        assertThat("invalid-bundle.zip should have info messages", response.getJSONArray("validation-messages"), CasCMatchers.hasInfoMessage());
        assertThat("invalid-bundle.zip should have validation messages",
            response.getJSONArray("validation-messages").stream().filter(msg -> !msg.toString().startsWith("INFO")).collect(Collectors.toList()),
            allOf(
                hasItem(containsString("ERROR - [APIVAL] - 'apiVersion' property in the bundle.yaml file must be an integer.")),
                hasItem(containsString("WARNING - [JCASC] - It is impossible to validate the Jenkins configuration. Please review your Jenkins and plugin configurations."))
            ));
        conn.disconnect();

        // No valid not quiet
        conn = post("invalid-bundle.zip", admin, false);
        assertThat("User admin should have permissions", conn.getResponseCode(), is(HttpServletResponse.SC_OK));
        response = JSONObject.fromObject(readResponse(conn.getInputStream()));
        assertFalse("invalid-bundle.zip should not be valid", response.getBoolean("valid"));
        assertTrue("invalid-bundle.zip should have validation messages", response.containsKey("validation-messages"));
        assertThat("invalid-bundle.zip should have info messages", response.getJSONArray("validation-messages"), CasCMatchers.hasInfoMessage());
        assertThat("invalid-bundle.zip should have validation messages",
            response.getJSONArray("validation-messages").stream().filter(msg -> !msg.toString().startsWith("INFO")).collect(Collectors.toList()),
            allOf(
                hasItem(containsString("ERROR - [APIVAL] - 'apiVersion' property in the bundle.yaml file must be an integer.")),
                hasItem(containsString("WARNING - [JCASC] - It is impossible to validate the Jenkins configuration. Please review your Jenkins and plugin configurations."))
            ));
        conn.disconnect();

        // No valid quiet
        conn = post("invalid-bundle.zip", admin, true);
        assertThat("User admin should have permissions", conn.getResponseCode(), is(HttpServletResponse.SC_OK));
        response = JSONObject.fromObject(readResponse(conn.getInputStream()));
        assertFalse("invalid-bundle.zip should not be valid", response.getBoolean("valid"));
        assertTrue("invalid-bundle.zip should have validation messages", response.containsKey("validation-messages"));
        assertThat("valid-bundle.zip should have info messages", response.getJSONArray("validation-messages"), not(CasCMatchers.hasInfoMessage()));
        //assertTrue("only-with-warnings.zip should have validation messages", response.getJSONArray("validation-messages").stream().anyMatch(validMsg -> validMsg.toString().contains(searchMsg)));
        assertThat("invalid-bundle.zip should have validation messages",
            response.getJSONArray("validation-messages"),
            allOf(
                hasItem(containsString("ERROR - [APIVAL] - 'apiVersion' property in the bundle.yaml file must be an integer.")),
                hasItem(containsString("WARNING - [JCASC] - It is impossible to validate the Jenkins configuration. Please review your Jenkins and plugin configurations."))
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
        String spec = "casc-bundle-mgnt/casc-bundle-validate?commit=COMMIT_HASH";
        return post(spec, bundle, user);
    }

    private HttpURLConnection post(String bundle, User user, boolean quiet) throws Exception {
        String spec = "casc-bundle-mgnt/casc-bundle-validate?commit=COMMIT_HASH&quiet=" + quiet;
        return post(spec, bundle, user);
    }

    private HttpURLConnection post(String spec, String bundle, User user) throws IOException {
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