package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.plugins.updates.envelope.Envelope;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopeProvider;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;
import com.cloudbees.opscenter.client.casc.cli.BundleReloadCommand;
import com.cloudbees.opscenter.client.casc.cli.BundleVersionCheckerCommand;
import com.github.tomakehurst.wiremock.common.ClasspathFileSource;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import hudson.ExtensionList;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.cli.CLICommandInvoker;
import hudson.model.User;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;
import net.sf.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
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
    @ClassRule
    public static WireMockClassRule wiremock = new WireMockClassRule(wireMockConfig().dynamicPort().fileSource(new ClasspathFileSource("src/test/resources/wiremock/")));
    @ClassRule
    public static TemporaryFolder bundlesSrc = new TemporaryFolder();

    @BeforeClass
    public static void processBundles() throws Exception {
        wiremock.stubFor(get(urlEqualTo("/beer-1.2.hpi")).willReturn(aResponse().withStatus(200).withBodyFile("beer-1.2.hpi")));
        wiremock.stubFor(get(urlEqualTo("/manage-permission-1.0.1.hpi")).willReturn(aResponse().withStatus(200).withBodyFile("manage-permission-1.0.1.hpi")));

        FileUtils.copyDirectory(Paths.get("src/test/resources/com/cloudbees/opscenter/client/plugin/casc").toFile(), bundlesSrc.getRoot());

        // Sanitise plugin-catalog.yaml
        Path pcFile1 = bundlesSrc.getRoot().toPath().resolve("bundle-with-catalog").resolve("plugin-catalog.yaml");
        String content;
        try (InputStream in = FileUtils.openInputStream(pcFile1.toFile())) {
            content = IOUtils.toString(in, StandardCharsets.UTF_8);
        }
        try (OutputStream out = FileUtils.openOutputStream(pcFile1.toFile(), false)) {
            IOUtils.write(content.replaceAll("%%URL%%", wiremock.baseUrl()), out, StandardCharsets.UTF_8);
        }

    }

    @Test
    @Issue({"BEE-34438"})
    @WithEnvelope(TwoPluginsV2dot289.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/plugin/casc/initial-bundle")
    public void testBundleVersionUpdatesDetection()
            throws Exception {
        // This is a dirty hack to be able to manipulate the plugin catalog as in the BeforeClass method:
        // - WithConfigBundle needs a static resource, so we cannot use the "hacked" bundle that is in the TemporaryFolder rule
        // - So we initialize the CJPRule using a dummy bundle
        // - First thing we do is to apply the real bundle we need for this test (We need plugin catalog to force the bundle as no hot-reloadable)
        //      - Change the System property to point to the real bundle
        //      - Force the check of the new version
        //      - Force the reload of the bundle
        //      - As the reload is done in a background thread, we wait until it's finished
        System.setProperty("core.casc.config.bundle", Paths.get(bundlesSrc.getRoot().getAbsolutePath() + "/bundle-with-catalog").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();
        ExtensionList.lookupSingleton(HotReloadAction.class).doReload();
        await("Version is completely reloaded").atMost(3, TimeUnit.MINUTES).until(() -> !ConfigurationStatusSingleton.INSTANCE.isCurrentlyReloading());

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
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatusSingleton.INSTANCE.isCurrentlyReloading());
    }

    private void newBundleAvailable(String number) throws Exception {
        String path = String.format(
                "src/test/resources/com/cloudbees/opscenter/client/casc/AbstractBundleVersionCheckerTest/version-%s.zip",
                number
        );
        System.setProperty("core.casc.config.bundle", Paths.get(path).toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();
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