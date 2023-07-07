package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.plugins.updates.envelope.Envelope;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopeProvider;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;
import com.cloudbees.opscenter.client.casc.cli.BundleReloadCommand;
import com.cloudbees.opscenter.client.casc.cli.BundleVersionCheckerCommand;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.cli.CLICommandInvoker;
import hudson.model.User;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;
import net.sf.json.JSONObject;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static hudson.cli.CLICommandInvoker.Matcher.hasNoErrorOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;

/**
 * Dedicated test for BEE-34438.
 * <p>
 * The purpose of this test is only to validate the detection of new bundles.
 * <p>
 * This test mix CLI and API calls to make sure there is no side effect.
 */
public class BundleVersionUpdatesDetectionTest extends AbstractBundleVersionCheckerTest {

    @Test
    @Issue({"BEE-34438"})
    @WithEnvelope(TwoPluginsV2dot289.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/plugin/casc/bundle-with-catalog")
    public void testBundleVersionUpdatesDetection()
            throws IOException {
        rule.jenkins.setSecurityRealm(new HudsonPrivateSecurityRealm(false, false, null));
        rule.jenkins.setAuthorizationStrategy(new ProjectMatrixAuthorizationStrategy());

        URL checkBundleUpdateURL = new URL(rule.getURL(), "casc-bundle-mgnt/check-bundle-update");
        User admin = setSecurityRealmUser(rule);

        try (CJPRule.WebClient wc = rule.createWebClient()) {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);

            WebResponse resp;
            CLICommandInvoker.Result result;
            // the expected current version
            String currentVersion;
            // the expected new version
            String newVersion;
            // the expected new version validity
            boolean newVersionIsValid;

            // WHEN the current version is 1
            currentVersion = "1";
            // AND there's a new version of the bundle
            newBundleAvailable("2");
            // AND this bundle is not ignored
            newVersion = "2";
            // AND this version is valid
            newVersionIsValid = true;

            // For the transition from version 1 to 2, the API is called first

            // WHEN the API is requested before the CLI
            resp = requestWithToken(checkBundleUpdateURL, admin, wc);
            // THEN the API should detect this new version
            assertThatIsSuccessful(resp);
            assertVersions(resp, currentVersion, newVersion, newVersionIsValid);

            // WHEN the CLI is called after the API
            result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
            // THEN the CLI should detect this new version
            assertThatIsSuccessful(result);
            assertVersions(result, currentVersion, newVersion, newVersionIsValid);

            // WHEN the CLI is called a second time
            result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
            // THEN the CLI should detect this new version
            assertThatIsSuccessful(result);
            assertVersions(result, currentVersion, newVersion, newVersionIsValid);

            // WHEN the API is requested a second time after the CLI
            resp = requestWithToken(checkBundleUpdateURL, admin, wc);
            // THEN the API should detect this new version
            assertThatIsSuccessful(resp);
            assertVersions(resp, currentVersion, newVersion, newVersionIsValid);

            // WHEN version 2 is NOT reloaded
            // (currentVersion stays the same)
            // AND a new bundle is available
            newBundleAvailable("3");
            // AND this bundle is ignored (it has no version)
            // (newVersion stays the same)
            // (newVersionIsValid is not used)

            // WHEN the CLI is called before the API
            result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
            // THEN the CLI should detect this new version
            assertThatIsSuccessful(result);
            assertVersions(result, currentVersion, newVersion, newVersionIsValid);

            // WHEN the API is requested after the CLI
            resp = requestWithToken(checkBundleUpdateURL, admin, wc);
            // THEN the API should not detect an invalid version
            assertThatIsSuccessful(resp);
            assertVersions(resp, currentVersion, newVersion, newVersionIsValid);

            // WHEN version 2 is NOT reloaded
            // AND version 3 is ignored
            // (currentVersion stays the same)
            // AND a new bundle is available
            newBundleAvailable("4");
            // AND this bundle is not ignored
            newVersion = "4";
            // AND this bundle is invalid
            newVersionIsValid = false;

            // WHEN the CLI is called before the API
            result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
            // THEN the CLI should detect this new version
            assertThatIsSuccessful(result);
            assertVersions(result, currentVersion, newVersion, newVersionIsValid);

            // WHEN the API is requested after the CLI
            resp = requestWithToken(checkBundleUpdateURL, admin, wc);
            // THEN the API should detect this new version
            assertThatIsSuccessful(resp);
            assertVersions(resp, currentVersion, newVersion, newVersionIsValid);

            // WHEN version 2 is NOT reloaded
            // AND version 3 is ignored
            // AND version 4 is invalid
            //  (currentVersion stays the same)
            // AND a new bundle is available
            newBundleAvailable("5");
            // AND this bundle is not ignored
            newVersion = "5";
            // AND this bundle is valid
            newVersionIsValid = true;

            // WHEN the CLI is called before the API
            result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
            // THEN the CLI should detect a new version
            assertThatIsSuccessful(result);
            assertVersions(result, currentVersion, newVersion, newVersionIsValid);

            // WHEN the API is requested after the CLI
            resp = requestWithToken(checkBundleUpdateURL, admin, wc);
            // THEN the API should detect a new version
            assertThatIsSuccessful(resp);
            assertVersions(resp, currentVersion, newVersion, newVersionIsValid);

            // WHEN version 5 is reloaded by the admin
            reloadBundleWithCLI(admin);
            currentVersion = "5";
            newVersion = null;
            newVersionIsValid = false;

            // WHEN the CLI is called before the API
            result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
            // THEN the CLI should not detect a new version
            assertThatIsSuccessful(result);
            assertVersions(result, currentVersion, newVersion, newVersionIsValid);

            // WHEN the API is requested after the CLI
            resp = requestWithToken(checkBundleUpdateURL, admin, wc);
            // THEN the API should not detect a new version
            assertThatIsSuccessful(resp);
            assertVersions(resp, currentVersion, newVersion, newVersionIsValid);

            // WHEN version 5 is reloaded
            //  currentVersion stays the same
            // AND a new bundle is available
            newBundleAvailable("7");
            // AND this bundle is not ignored
            newVersion = "7";
            // AND this bundle is valid
            newVersionIsValid = true;

            // For the transition from version 5 to 7, the CLI is called first

            // WHEN the CLI is called before the API
            result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
            // THEN the CLI should detect this new version
            assertThatIsSuccessful(result);
            assertVersions(result, currentVersion, newVersion, newVersionIsValid);

            // WHEN the API is requested after the CLI
            resp = requestWithToken(checkBundleUpdateURL, admin, wc);
            // THEN the API should detect this new version
            assertThatIsSuccessful(resp);
            assertVersions(resp, currentVersion, newVersion, newVersionIsValid);

            // WHEN the CLI is called a second time after the API
            result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser(admin.getId()).invoke();
            // THEN the CLI should detect this new version
            assertThatIsSuccessful(result);
            assertVersions(result, currentVersion, newVersion, newVersionIsValid);

            // WHEN the API is requested a second time after the CLI
            resp = requestWithToken(checkBundleUpdateURL, admin, wc);
            // THEN the API should detect this new version
            assertThatIsSuccessful(resp);
            assertVersions(resp, currentVersion, newVersion, newVersionIsValid);
        }
    }

    private void reloadBundleWithCLI(User admin) {
        new CLICommandInvoker(rule, BundleReloadCommand.COMMAND_NAME).asUser(admin.getId()).invoke(); // Apply new version
        // Wait for async reload to complete
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());
    }

    private void newBundleAvailable(String number) {
        String path = String.format(
                "src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-%s.zip",
                number
        );
        System.setProperty("core.casc.config.bundle", Paths.get(path).toFile().getAbsolutePath());
    }

    private void assertThatIsSuccessful(WebResponse resp) {
        assertThat("We should get a 200", resp.getStatusCode(), is(HttpServletResponse.SC_OK));
    }

    private void assertThatIsSuccessful(CLICommandInvoker.Result result) {
        assertThat(result, allOf(succeeded(), hasNoErrorOutput()));
    }

    private void assertVersions(WebResponse resp, String currentVersion, String newVersion, boolean newIsValid) {
        assertVersions(resp.getContentAsString(), "version-" + currentVersion + ".zip", currentVersion, newVersion, newIsValid);
    }

    private void assertVersions(CLICommandInvoker.Result result, String currentVersion, String newVersion, boolean newIsValid) {
        assertVersions(result.stdout(), "version-" + currentVersion + ".zip", currentVersion, newVersion, newIsValid);
    }

    private void assertVersions(String content, String bundle, String currentVersion, String newVersion, boolean newIsValid) {
        assertVersions(JSONObject.fromObject(content), bundle, currentVersion, newVersion, newIsValid);
    }

    private WebResponse requestWithToken(URL fullURL, User asUser, CJPRule.WebClient wc) throws IOException {
        try {
            WebRequest getRequest = new WebRequest(fullURL, HttpMethod.GET);
            return wc.withBasicApiToken(asUser).getPage(getRequest).getWebResponse();
        } catch (FailingHttpStatusCodeException exception) {
            return exception.getResponse();
        }
    }

    private static User setSecurityRealmUser(CJPRule j) throws IOException {
        HudsonPrivateSecurityRealm realm = (HudsonPrivateSecurityRealm) j.jenkins.getSecurityRealm();
        User user = realm.createAccount("admin", "password");
        j.jenkins.setSecurityRealm(realm);
        ProjectMatrixAuthorizationStrategy authorizationStrategy = (ProjectMatrixAuthorizationStrategy) j.jenkins.getAuthorizationStrategy();
        authorizationStrategy.add(Jenkins.ADMINISTER, user.getId());
        j.jenkins.setAuthorizationStrategy(authorizationStrategy);
        user.addProperty(new ApiTokenProperty());
        user.getProperty(ApiTokenProperty.class).changeApiToken();
        return user;
    }

    public static final class TwoPluginsV2dot289 implements TestEnvelopeProvider {
        @NonNull
        public Envelope call() {
            return TestEnvelopes.e("2.289.1", 1, "", TestEnvelopes.beer12(), TestEnvelopes.p("manage-permission", "1.0.1"));
        }
    }
}