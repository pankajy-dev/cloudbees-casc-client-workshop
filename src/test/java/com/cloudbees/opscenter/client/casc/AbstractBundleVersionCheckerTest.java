package com.cloudbees.opscenter.client.casc;

import java.util.stream.Collectors;

import com.cloudbees.jenkins.cjp.installmanager.AbstractCJPTest;
import com.cloudbees.jenkins.plugins.updates.envelope.Envelope;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopeProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.User;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;
import net.sf.json.JSONObject;
import org.hamcrest.Matcher;
import org.junit.Before;

import static com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes.beer12;
import static com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes.e;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public abstract class AbstractBundleVersionCheckerTest extends AbstractCJPTest {

    protected User admin;

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
        admin.addProperty(new ApiTokenProperty());
        admin.getProperty(ApiTokenProperty.class).changeApiToken();
    }

    protected void assertUpdateType(JSONObject jsonResult, String bundle, String updateType) {
        if (updateType != null) {
            assertTrue("Bundle " + bundle + ": should contain update-type", jsonResult.containsKey("update-type"));
            assertThat("Bundle " + bundle + ": update-type is " + updateType, jsonResult.getString("update-type"), is(updateType));
        } else {
            assertFalse("Bundle " + bundle + ": should not contain update-type", jsonResult.containsKey("update-type"));
        }
    }

    protected void assertVersions(JSONObject jsonResult, String bundle, String currentVersion, String newVersion, boolean newIsValid) {
        assertVersions(jsonResult, bundle, currentVersion, anything(), newVersion, anything(), newIsValid);
    }

    protected void assertVersions(JSONObject jsonResult, String bundle, String currentVersion, Matcher currentValidations, String newVersion, Matcher newValidations, boolean newIsValid) {
        assertTrue("Bundle " + bundle + ": should contain versions", jsonResult.containsKey("versions"));
        JSONObject versions = jsonResult.getJSONObject("versions");
        assertTrue("Bundle " + bundle + ": should contain current version", versions.containsKey("current-bundle"));
        JSONObject current = versions.getJSONObject("current-bundle");
        assertThat("Bundle " + bundle + ": current version is " + currentVersion, current.getString("version"), is(currentVersion));
        assertThat("Bundle " + bundle + ": validating current validations", current.getJSONArray("validations").stream().filter(msg -> !msg.toString().startsWith("INFO")).collect(Collectors.toList()), currentValidations);
        if (newVersion != null) {
            assertTrue("Bundle " + bundle + ": should contain new version", versions.containsKey("new-version"));
            JSONObject newVersionObj = versions.getJSONObject("new-version");
            assertThat("Bundle " + bundle + ": new version is " + newVersion, newVersionObj.getString("version"), is(newVersion));
            assertThat("Bundle " + bundle + ": new version valid is " + newIsValid, newVersionObj.getBoolean("valid"), is(newIsValid));
            assertThat("Bundle " + bundle + ": validating new version", newVersionObj.getJSONArray("validations").stream().filter(msg -> !msg.toString().startsWith("INFO")).collect(Collectors.toList()), newValidations);
        } else {
            assertFalse("Bundle " + bundle + ": should not contain new version", versions.containsKey("new-version"));
        }
    }

    protected void assertUpdateAvailable(JSONObject jsonResult, String bundle, boolean updateAvailable) {
        assertTrue("Bundle " + bundle + " should contain update-available", jsonResult.containsKey("update-available"));
        assertThat("Bundle " + bundle + ": update-available should be " + updateAvailable, jsonResult.getBoolean("update-available"), is(updateAvailable));
    }

    public static final class TestEnvelope implements TestEnvelopeProvider {
        @NonNull
        @Override
        public Envelope call() throws Exception {
            return e("2.332.2.6", 1, "2.332.2-cb-2", beer12());
        }
    }

}