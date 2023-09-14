package com.cloudbees.jenkins.plugins.casc.config;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import com.github.tomakehurst.wiremock.common.ClasspathFileSource;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import net.sf.json.JSONObject;

import hudson.ExtensionList;
import hudson.cli.CLICommandInvoker;
import hudson.security.HudsonPrivateSecurityRealm;
import jenkins.model.Jenkins;

import com.cloudbees.jenkins.cjp.installmanager.AbstractCJPTest;
import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.cjp.installmanager.WithBundleUpdateTiming;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;
import com.cloudbees.opscenter.client.casc.BundleReloadAction;
import com.cloudbees.opscenter.client.casc.ConfigurationStatus;
import com.cloudbees.opscenter.client.casc.ConfigurationUpdaterHelper;
import com.cloudbees.opscenter.client.casc.UpdateType;
import com.cloudbees.opscenter.client.casc.cli.BundleReloadCommand;
import com.cloudbees.opscenter.client.casc.cli.BundleVersionCheckerCommand;
import com.cloudbees.opscenter.client.casc.visualization.BundleVisualizationLink;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Test cases (instance up and running, automatic reload only makes sense then):
 * I. With bundle update timing enabled:
 *  a. Automatic reload enabled:
 *   i. New bundle version is hot reloadable, so it forces the reload
 *   ii. New bundle version is not hot reloadable, so it doesn't force the reload
 *  b. Automatic reload disabled:
 *   i. New bundle version is hot reloadable, so it doesn't force the reload
 *   ii. New bundle version is hot reloadable, so it doesn't force the reload
 * II. With bundle update timing disabled:
 *   i. New bundle version is hot reloadable, so it doesn't force the reload
 *   ii. New bundle version is hot reloadable, so it doesn't force the reload
 * III. Using the endpoint and CLI:
 *  a. Automatic reload enabled:
 *   i. New bundle version is hot reloadable, so it forces the reload
 *   ii. New bundle version is not hot reloadable, so it doesn't force the reload
 *  b. Automatic reload disabled:
 *   i. New bundle version is hot reloadable, so it doesn't force the reload
 *   ii. New bundle version is hot reloadable, so it doesn't force the reload
 */
public class AutomaticReloadTest extends AbstractCJPTest {

    @ClassRule
    public static WireMockClassRule wiremock = new WireMockClassRule(wireMockConfig().dynamicPort().fileSource(new ClasspathFileSource("src/test/resources/wiremock/")));
    @ClassRule
    public static TemporaryFolder bundlesSrc = new TemporaryFolder();

    /**
     * Wiremock stubs for plugin catalog
     */
    @BeforeClass
    public static void processBundles() throws Exception {
        wiremock.stubFor(get(urlEqualTo("/beer-1.2.hpi")).willReturn(aResponse().withStatus(200).withBodyFile("beer-1.2.hpi")));
        wiremock.stubFor(get(urlEqualTo("/beer-1.3.hpi")).willReturn(aResponse().withStatus(200).withBodyFile("beer-1.3.hpi")));

        FileUtils.copyDirectory(Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest").toFile(), bundlesSrc.getRoot());
        // Sanitise plugin-catalog.yaml
        Path pcFile1 = bundlesSrc.getRoot().toPath().resolve("new-bundle-version-no-hot").resolve("plugin-catalog.yaml");
        String content;
        try (InputStream in = FileUtils.openInputStream(pcFile1.toFile())) {
            content = IOUtils.toString(in, StandardCharsets.UTF_8);
        }
        try (OutputStream out = FileUtils.openOutputStream(pcFile1.toFile(), false)) {
            IOUtils.write(content.replaceAll("%%URL%%", wiremock.baseUrl()), out, StandardCharsets.UTF_8);
        }
        Path pcFile2 = bundlesSrc.getRoot().toPath().resolve("final-bundle-version-no-hot").resolve("plugin-catalog.yaml");
        try (InputStream in = FileUtils.openInputStream(pcFile2.toFile())) {
            content = IOUtils.toString(in, StandardCharsets.UTF_8);
        }
        try (OutputStream out = FileUtils.openOutputStream(pcFile2.toFile(), false)) {
            IOUtils.write(content.replaceAll("%%URL%%", wiremock.baseUrl()), out, StandardCharsets.UTF_8);
        }
    }

    @Before
    public void setUp() throws Exception {
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false, false, null);
        realm.createAccount("alice", "alice");
        rule.jenkins.setSecurityRealm(realm);
        rule.getInstance().setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().toEveryone());

        // This is a dirty hack because the instance (it's an ENUM) is not reset between tests
        ConfigurationStatus.INSTANCE.setUpdateAvailable(false);
        ConfigurationStatus.INSTANCE.setOutdatedVersion(null);
        ConfigurationStatus.INSTANCE.setOutdatedBundleInformation(null);
        ConfigurationBundleManager.reset();
    }

    // I.a.i
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/initial-bundle-version")
    public void automaticReloadAndHotReload_enabled() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setAutomaticReload(true);
        configuration.save();

        assertTrue("There is the automatic reload", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From version 1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/new-bundle-version").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        // Just in case the async reload hasn't finished
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        BundleVisualizationLink bundleUpdateTab = BundleVisualizationLink.get();
        assertThat("New bundle is loaded", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertNull("New bundle is loaded", bundleUpdateTab.getUpdateVersion());
        assertThat("New bundle is loaded", bundleUpdateTab.getBundleVersion(), is("2"));
        assertThat("New bundle is loaded", Jenkins.get().getSystemMessage(), is("From version 2"));
    }

    // I.a.ii
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/initial-bundle-version")
    public void automaticReloadAndNotHotReload_enabled() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setAutomaticReload(true);
        configuration.save();

        assertTrue("There is the automatic reload", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From version 1"));

        // Update version to 2 - Hot Reloaded
        System.setProperty("core.casc.config.bundle", Paths.get(bundlesSrc.getRoot().getAbsolutePath() + "/new-bundle-version-no-hot").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        // Just in case the async reload hasn't finished
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        BundleVisualizationLink bundleUpdateTab = BundleVisualizationLink.get();
        assertThat("New bundle is loaded", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertNull("New bundle is loaded", bundleUpdateTab.getUpdateVersion());
        assertThat("New bundle is loaded", bundleUpdateTab.getBundleVersion(), is("2"));
        assertThat("New bundle is loaded", Jenkins.get().getSystemMessage(), is("From version 2"));

        // Update version to 3 - NO Hot Reloaded
        System.setProperty("core.casc.config.bundle", Paths.get(bundlesSrc.getRoot().getAbsolutePath() + "/final-bundle-version-no-hot").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        // Just in case the async reload hasn't finished
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        assertThat("New bundle cannot be hot loaded, so not promoted", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertThat("New bundle cannot be hot loaded, so not promoted", bundleUpdateTab.getUpdateVersion(), is("3"));
        assertThat("New bundle cannot be hot loaded, so not promoted", bundleUpdateTab.getBundleVersion(), is("2"));
        assertFalse("New bundle cannot be hot loaded, so not promoted", bundleUpdateTab.isHotReloadable());
        assertThat("New bundle cannot be hot loaded, so not promoted", Jenkins.get().getSystemMessage(), is("From version 2"));
    }

    // I.b.i
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/initial-bundle-version")
    public void noAutomaticReloadAndHotReload_enabled() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setAutomaticReload(false);
        configuration.save();

        assertFalse("There is no automatic reload", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From version 1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/new-bundle-version").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        // Just in case the async reload hasn't finished
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        BundleVisualizationLink bundleUpdateTab = BundleVisualizationLink.get();
        assertThat("New bundle isn't promoted", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("New bundle isn't promoted", bundleManager.getCandidateAsConfigurationBundle().getVersion(), is("2"));
        assertThat("New bundle isn't promoted", bundleUpdateTab.getUpdateVersion(), is("2"));
        assertThat("New bundle isn't promoted", bundleUpdateTab.getBundleVersion(), is("1"));
        assertThat("New bundle isn't promoted", Jenkins.get().getSystemMessage(), is("From version 1"));
    }

    // I.b.ii
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/initial-bundle-version")
    public void noAutomaticReloadAndNotHotReload_enabled() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setAutomaticReload(false);
        configuration.save();

        assertFalse("There is no automatic reload", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From version 1"));

        // Update version to 2 - Hot Reloaded
        System.setProperty("core.casc.config.bundle", Paths.get(bundlesSrc.getRoot().getAbsolutePath() + "/new-bundle-version-no-hot").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        BundleVisualizationLink bundleUpdateTab = BundleVisualizationLink.get();
        assertThat("No automatic reload, so new bundle no promoted", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("No automatic reload, so new bundle no promoted", bundleUpdateTab.getUpdateVersion(), is("2"));
        assertThat("No automatic reload, so new bundle no promoted", bundleUpdateTab.getBundleVersion(), is("1"));
        assertThat("No automatic reload, so new bundle no promoted", Jenkins.get().getSystemMessage(), is("From version 1"));

        // Let's reload as if the user had clicked the button
        assertTrue("This version 2 is Hot Reloadable", bundleManager.getCandidateAsConfigurationBundle().isHotReloadable());
        ConfigurationUpdaterHelper.promoteCandidate();
        ExtensionList.lookupSingleton(BundleReloadAction.class).executeReload(false);
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        assertThat("New bundle is now loaded", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertNull("New bundle is now loaded", bundleUpdateTab.getUpdateVersion());
        assertThat("New bundle is now loaded", bundleUpdateTab.getBundleVersion(), is("2"));
        assertThat("New bundle is now loaded", Jenkins.get().getSystemMessage(), is("From version 2"));

        // Update version to 3 - NO Hot Reloaded
        System.setProperty("core.casc.config.bundle", Paths.get(bundlesSrc.getRoot().getAbsolutePath() + "/final-bundle-version-no-hot").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();
        assertFalse("This version 3 is Hot Reloadable", bundleManager.getCandidateAsConfigurationBundle().isHotReloadable());
        assertThat("No automatic reload, so new bundle no promoted", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertThat("No automatic reload, so new bundle no promoted", bundleUpdateTab.getUpdateVersion(), is("3"));
        assertThat("No automatic reload, so new bundle no promoted", bundleUpdateTab.getBundleVersion(), is("2"));
        assertThat("No automatic reload, so new bundle no promoted", Jenkins.get().getSystemMessage(), is("From version 2"));
    }

    // II.i
    @Test
    @WithBundleUpdateTiming("false")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/initial-bundle-version")
    public void automaticReloadAndHotReload_disabled() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertFalse("Bundle Update Timing is disabled", configuration.isEnabled());

        configuration.setAutomaticReload(true);
        configuration.save();

        assertFalse("Automatic reload won't be configured as Bundle Update Timing is disabled", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From version 1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/new-bundle-version").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        // Just in case the async reload hasn't finished
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        BundleVisualizationLink bundleUpdateTab = BundleVisualizationLink.get();
        assertThat("New bundle isn't hot loaded", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertThat("New bundle isn't hot loaded", bundleUpdateTab.getUpdateVersion(), is("2"));
        assertThat("New bundle isn't hot loaded", bundleUpdateTab.getBundleVersion(), is("1"));
        assertThat("New bundle isn't hot loaded", Jenkins.get().getSystemMessage(), is("From version 1"));
    }

    // II.ii
    @Test
    @WithBundleUpdateTiming("false")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/initial-bundle-version")
    public void automaticReloadAndNotHotReload_disabled() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertFalse("Bundle Update Timing is disabled", configuration.isEnabled());

        configuration.setAutomaticReload(true);
        configuration.save();

        assertFalse("Automatic reload won't be configured as Bundle Update Timing is disabled", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From version 1"));

        // Update version to 2 - Hot Reloaded
        System.setProperty("core.casc.config.bundle", Paths.get(bundlesSrc.getRoot().getAbsolutePath() + "/new-bundle-version-no-hot").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        BundleVisualizationLink bundleUpdateTab = BundleVisualizationLink.get();
        assertThat("New bundle isn't hot loaded", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertThat("New bundle isn't hot loaded", bundleUpdateTab.getUpdateVersion(), is("2"));
        assertThat("New bundle isn't hot loaded", bundleUpdateTab.getBundleVersion(), is("1"));
        assertThat("New bundle isn't hot loaded", Jenkins.get().getSystemMessage(), is("From version 1"));

        // Let's reload as if the user had clicked the button
        assertTrue("This version 2 is Hot Reloadable", bundleManager.getConfigurationBundle().isHotReloadable());
        ExtensionList.lookupSingleton(BundleReloadAction.class).executeReload(false);
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        assertThat("New bundle is now loaded", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertNull("New bundle is now loaded", bundleUpdateTab.getUpdateVersion());
        assertThat("New bundle is now loaded", bundleUpdateTab.getBundleVersion(), is("2"));
        assertThat("New bundle is now loaded", Jenkins.get().getSystemMessage(), is("From version 2"));

        // Update version to 3 - NO Hot Reloaded
        System.setProperty("core.casc.config.bundle", Paths.get(bundlesSrc.getRoot().getAbsolutePath() + "/final-bundle-version-no-hot").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();
        assertFalse("This version 2 is Hot Reloadable", bundleManager.getConfigurationBundle().isHotReloadable());
        assertThat("New bundle cannot be hot loaded", bundleManager.getConfigurationBundle().getVersion(), is("3"));
        assertThat("New bundle cannot be hot loaded", bundleUpdateTab.getUpdateVersion(), is("3"));
        assertThat("New bundle cannot be hot loaded", bundleUpdateTab.getBundleVersion(), is("2"));
        assertThat("New bundle cannot be hot loaded", Jenkins.get().getSystemMessage(), is("From version 2"));
    }

    // III.a.i
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/initial-bundle-version")
    public void automaticReloadAndHotReload_enabled_endpoint() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setAutomaticReload(true);
        configuration.save();

        assertTrue("There is the automatic reload", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From version 1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/new-bundle-version").toFile().getAbsolutePath());
        CJPRule.WebClient wc = rule.createWebClient();
        WebResponse resp = requestWithToken(HttpMethod.GET, "casc-bundle-mgnt/check-bundle-update", wc);
        JSONObject response = JSONObject.fromObject(resp.getContentAsString());
        assertNotNull("There should be an automatic reload", response.get("update-type"));
        assertThat("There should be an automatic reload", response.get("update-type"), is(UpdateType.AUTOMATIC_RELOAD.label));

        // Just in case the async reload hasn't finished
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        BundleVisualizationLink bundleUpdateTab = BundleVisualizationLink.get();
        assertThat("New bundle is loaded", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertNull("New bundle is loaded", bundleUpdateTab.getUpdateVersion());
        assertThat("New bundle is loaded", bundleUpdateTab.getBundleVersion(), is("2"));
        assertThat("New bundle is loaded", Jenkins.get().getSystemMessage(), is("From version 2"));
    }

    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/initial-bundle-version")
    public void automaticReloadAndHotReload_enabled_cli() {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setAutomaticReload(true);
        configuration.save();

        assertTrue("There is the automatic reload", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From version 1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/new-bundle-version").toFile().getAbsolutePath());
        CLICommandInvoker.Result result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser("alice").invoke();
        JSONObject response = JSONObject.fromObject(result.stdout());
        assertNotNull("There should be an automatic reload", response.get("update-type"));
        assertThat("There should be an automatic reload", response.get("update-type"), is(UpdateType.AUTOMATIC_RELOAD.label));

        // Just in case the async reload hasn't finished
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        BundleVisualizationLink bundleUpdateTab = BundleVisualizationLink.get();
        assertThat("New bundle is loaded", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertNull("New bundle is loaded", bundleUpdateTab.getUpdateVersion());
        assertThat("New bundle is loaded", bundleUpdateTab.getBundleVersion(), is("2"));
        assertThat("New bundle is loaded", Jenkins.get().getSystemMessage(), is("From version 2"));
    }

    // III.a.ii
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/initial-bundle-version")
    public void automaticReloadAndNotHotReload_enabled_endpoint() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setAutomaticReload(true);
        configuration.save();

        assertTrue("There is the automatic reload", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From version 1"));

        // Update version to 2 - Hot Reloaded
        System.setProperty("core.casc.config.bundle", Paths.get(bundlesSrc.getRoot().getAbsolutePath() + "/new-bundle-version-no-hot").toFile().getAbsolutePath());
        CJPRule.WebClient wc = rule.createWebClient();
        WebResponse resp = requestWithToken(HttpMethod.GET, "casc-bundle-mgnt/check-bundle-update", wc);
        JSONObject response = JSONObject.fromObject(resp.getContentAsString());
        assertNotNull("There should be an automatic reload", response.get("update-type"));
        assertThat("There should be an automatic reload", response.get("update-type"), is(UpdateType.AUTOMATIC_RELOAD.label));

        // Just in case the async reload hasn't finished
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        BundleVisualizationLink bundleUpdateTab = BundleVisualizationLink.get();
        assertThat("New bundle is loaded", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertNull("New bundle is loaded", bundleUpdateTab.getUpdateVersion());
        assertThat("New bundle is loaded", bundleUpdateTab.getBundleVersion(), is("2"));
        assertThat("New bundle is loaded", Jenkins.get().getSystemMessage(), is("From version 2"));

        // Update version to 3 - NO Hot Reloaded
        System.setProperty("core.casc.config.bundle", Paths.get(bundlesSrc.getRoot().getAbsolutePath() + "/final-bundle-version-no-hot").toFile().getAbsolutePath());
        resp = requestWithToken(HttpMethod.GET, "casc-bundle-mgnt/check-bundle-update", wc);
        response = JSONObject.fromObject(resp.getContentAsString());
        assertNotNull("There should not be an automatic reload, so the bundle must be restarted or skipped", response.get("update-type"));
        assertThat("There should be not an automatic reload, so the bundle must be restarted or skipped", response.get("update-type"), is(UpdateType.RESTART_OR_SKIP.label));

        // Just in case the async reload hasn't finished
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        assertThat("New bundle cannot be hot loaded, so not promoted", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertThat("New bundle cannot be hot loaded, so not promoted", bundleUpdateTab.getUpdateVersion(), is("3"));
        assertThat("New bundle cannot be hot loaded, so not promoted", bundleUpdateTab.getBundleVersion(), is("2"));
        assertFalse("New bundle cannot be hot loaded, so not promoted", bundleUpdateTab.isHotReloadable());
        assertThat("New bundle cannot be hot loaded, so not promoted", Jenkins.get().getSystemMessage(), is("From version 2"));
    }

    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/initial-bundle-version")
    public void automaticReloadAndNotHotReload_enabled_cli() {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setAutomaticReload(true);
        configuration.save();

        assertTrue("There is the automatic reload", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From version 1"));

        // Update version to 2 - Hot Reloaded
        System.setProperty("core.casc.config.bundle", Paths.get(bundlesSrc.getRoot().getAbsolutePath() + "/new-bundle-version-no-hot").toFile().getAbsolutePath());
        CLICommandInvoker.Result result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser("alice").invoke();
        JSONObject response = JSONObject.fromObject(result.stdout());
        assertNotNull("There should be an automatic reload", response.get("update-type"));
        assertThat("There should be an automatic reload", response.get("update-type"), is(UpdateType.AUTOMATIC_RELOAD.label));

        // Just in case the async reload hasn't finished
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        BundleVisualizationLink bundleUpdateTab = BundleVisualizationLink.get();
        assertThat("New bundle is loaded", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertNull("New bundle is loaded", bundleUpdateTab.getUpdateVersion());
        assertThat("New bundle is loaded", bundleUpdateTab.getBundleVersion(), is("2"));
        assertThat("New bundle is loaded", Jenkins.get().getSystemMessage(), is("From version 2"));

        // Update version to 3 - NO Hot Reloaded
        System.setProperty("core.casc.config.bundle", Paths.get(bundlesSrc.getRoot().getAbsolutePath() + "/final-bundle-version-no-hot").toFile().getAbsolutePath());
        result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser("alice").invoke();
        response = JSONObject.fromObject(result.stdout());
        assertNotNull("There should not be an automatic reload, so the bundle must be restarted or skipped", response.get("update-type"));
        assertThat("There should be not an automatic reload, so the bundle must be restarted or skipped", response.get("update-type"), is(UpdateType.RESTART_OR_SKIP.label));

        // Just in case the async reload hasn't finished
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        assertThat("New bundle cannot be hot loaded, so not promoted", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertThat("New bundle cannot be hot loaded, so not promoted", bundleUpdateTab.getUpdateVersion(), is("3"));
        assertThat("New bundle cannot be hot loaded, so not promoted", bundleUpdateTab.getBundleVersion(), is("2"));
        assertFalse("New bundle cannot be hot loaded, so not promoted", bundleUpdateTab.isHotReloadable());
        assertThat("New bundle cannot be hot loaded, so not promoted", Jenkins.get().getSystemMessage(), is("From version 2"));
    }

    // III.b.i
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/initial-bundle-version")
    public void noAutomaticReloadAndHotReload_enabled_endpoint() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setAutomaticReload(false);
        configuration.save();

        assertFalse("There is no automatic reload", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From version 1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/new-bundle-version").toFile().getAbsolutePath());
        CJPRule.WebClient wc = rule.createWebClient();
        WebResponse resp = requestWithToken(HttpMethod.GET, "casc-bundle-mgnt/check-bundle-update", wc);
        JSONObject response = JSONObject.fromObject(resp.getContentAsString());
        assertNotNull("There should not be an automatic reload", response.get("update-type"));
        assertThat("There should not be an automatic reload", response.get("update-type"), is(UpdateType.RELOAD_OR_SKIP.label));

        // Just in case the async reload hasn't finished
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        BundleVisualizationLink bundleUpdateTab = BundleVisualizationLink.get();
        assertThat("New bundle isn't promoted", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("New bundle isn't promoted", bundleManager.getCandidateAsConfigurationBundle().getVersion(), is("2"));
        assertThat("New bundle isn't promoted", bundleUpdateTab.getUpdateVersion(), is("2"));
        assertThat("New bundle isn't promoted", bundleUpdateTab.getBundleVersion(), is("1"));
        assertThat("New bundle isn't promoted", Jenkins.get().getSystemMessage(), is("From version 1"));
    }

    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/initial-bundle-version")
    public void noAutomaticReloadAndHotReload_enabled_cli() {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setAutomaticReload(false);
        configuration.save();

        assertFalse("There is no automatic reload", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From version 1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/new-bundle-version").toFile().getAbsolutePath());
        CLICommandInvoker.Result result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser("alice").invoke();
        JSONObject response = JSONObject.fromObject(result.stdout());
        assertNotNull("There should not be an automatic reload", response.get("update-type"));
        assertThat("There should not be an automatic reload", response.get("update-type"), is(UpdateType.RELOAD_OR_SKIP.label));

        // Just in case the async reload hasn't finished
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        BundleVisualizationLink bundleUpdateTab = BundleVisualizationLink.get();
        assertThat("New bundle isn't promoted", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("New bundle isn't promoted", bundleManager.getCandidateAsConfigurationBundle().getVersion(), is("2"));
        assertThat("New bundle isn't promoted", bundleUpdateTab.getUpdateVersion(), is("2"));
        assertThat("New bundle isn't promoted", bundleUpdateTab.getBundleVersion(), is("1"));
        assertThat("New bundle isn't promoted", Jenkins.get().getSystemMessage(), is("From version 1"));
    }

    // III.b.ii
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/initial-bundle-version")
    public void noAutomaticReloadAndNotHotReload_enabled_endpoint() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setAutomaticReload(false);
        configuration.save();

        assertFalse("There is no automatic reload", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From version 1"));

        // Update version to 2 - Hot Reloaded
        System.setProperty("core.casc.config.bundle", Paths.get(bundlesSrc.getRoot().getAbsolutePath() + "/new-bundle-version-no-hot").toFile().getAbsolutePath());
        CJPRule.WebClient wc = rule.createWebClient();
        WebResponse resp = requestWithToken(HttpMethod.GET, "casc-bundle-mgnt/check-bundle-update", wc);
        JSONObject response = JSONObject.fromObject(resp.getContentAsString());
        assertNotNull("There should not be an automatic reload", response.get("update-type"));
        assertThat("There should not be an automatic reload", response.get("update-type"), is(UpdateType.RELOAD_OR_SKIP.label));

        BundleVisualizationLink bundleUpdateTab = BundleVisualizationLink.get();
        assertThat("No automatic reload, so new bundle no promoted", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("No automatic reload, so new bundle no promoted", bundleUpdateTab.getUpdateVersion(), is("2"));
        assertThat("No automatic reload, so new bundle no promoted", bundleUpdateTab.getBundleVersion(), is("1"));
        assertThat("No automatic reload, so new bundle no promoted", Jenkins.get().getSystemMessage(), is("From version 1"));

        // Let's reload as if the user had clicked the button
        assertTrue("This version 2 is Hot Reloadable", bundleManager.getCandidateAsConfigurationBundle().isHotReloadable());
        resp = requestWithToken(HttpMethod.POST, "casc-bundle-mgnt/reload-bundle", wc);
        response = JSONObject.fromObject(resp.getContentAsString());
        assertNotNull("Bundle reloaded", response.get("reloaded"));
        assertTrue("Bundle reloaded", response.getBoolean("reloaded"));

        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        assertThat("New bundle is now loaded", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertNull("New bundle is now loaded", bundleUpdateTab.getUpdateVersion());
        assertThat("New bundle is now loaded", bundleUpdateTab.getBundleVersion(), is("2"));
        assertThat("New bundle is now loaded", Jenkins.get().getSystemMessage(), is("From version 2"));

        // Update version to 3 - NO Hot Reloaded
        System.setProperty("core.casc.config.bundle", Paths.get(bundlesSrc.getRoot().getAbsolutePath() + "/final-bundle-version-no-hot").toFile().getAbsolutePath());
        resp = requestWithToken(HttpMethod.GET, "casc-bundle-mgnt/check-bundle-update", wc);
        response = JSONObject.fromObject(resp.getContentAsString());
        assertNotNull("There should not be an automatic reload", response.get("update-type"));
        assertThat("There should not be an automatic reload", response.get("update-type"), is(UpdateType.RESTART_OR_SKIP.label));

        assertFalse("This version 3 is Hot Reloadable", bundleManager.getCandidateAsConfigurationBundle().isHotReloadable());
        assertThat("No automatic reload, so new bundle no promoted", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertThat("No automatic reload, so new bundle no promoted", bundleUpdateTab.getUpdateVersion(), is("3"));
        assertThat("No automatic reload, so new bundle no promoted", bundleUpdateTab.getBundleVersion(), is("2"));
        assertThat("No automatic reload, so new bundle no promoted", Jenkins.get().getSystemMessage(), is("From version 2"));
    }

    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/initial-bundle-version")
    public void noAutomaticReloadAndNotHotReload_enabled_cli() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setAutomaticReload(false);
        configuration.save();

        assertFalse("There is no automatic reload", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From version 1"));

        // Update version to 2 - Hot Reloaded
        System.setProperty("core.casc.config.bundle", Paths.get(bundlesSrc.getRoot().getAbsolutePath() + "/new-bundle-version-no-hot").toFile().getAbsolutePath());
        CLICommandInvoker.Result result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser("alice").invoke();
        JSONObject response = JSONObject.fromObject(result.stdout());
        assertNotNull("There should not be an automatic reload", response.get("update-type"));
        assertThat("There should not be an automatic reload", response.get("update-type"), is(UpdateType.RELOAD_OR_SKIP.label));

        BundleVisualizationLink bundleUpdateTab = BundleVisualizationLink.get();
        assertThat("No automatic reload, so new bundle no promoted", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("No automatic reload, so new bundle no promoted", bundleUpdateTab.getUpdateVersion(), is("2"));
        assertThat("No automatic reload, so new bundle no promoted", bundleUpdateTab.getBundleVersion(), is("1"));
        assertThat("No automatic reload, so new bundle no promoted", Jenkins.get().getSystemMessage(), is("From version 1"));

        // Let's reload as if the user had clicked the button
        assertTrue("This version 2 is Hot Reloadable", bundleManager.getCandidateAsConfigurationBundle().isHotReloadable());
        result = new CLICommandInvoker(rule, BundleReloadCommand.COMMAND_NAME).asUser("alice").invoke();
        assertThat("Bundle reloaded", result.stdout().trim(), is("Bundle successfully reloaded."));
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        assertThat("New bundle is now loaded", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertNull("New bundle is now loaded", bundleUpdateTab.getUpdateVersion());
        assertThat("New bundle is now loaded", bundleUpdateTab.getBundleVersion(), is("2"));
        assertThat("New bundle is now loaded", Jenkins.get().getSystemMessage(), is("From version 2"));

        // Update version to 3 - NO Hot Reloaded
        System.setProperty("core.casc.config.bundle", Paths.get(bundlesSrc.getRoot().getAbsolutePath() + "/final-bundle-version-no-hot").toFile().getAbsolutePath());
        result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser("alice").invoke();
        response = JSONObject.fromObject(result.stdout());
        assertNotNull("There should not be an automatic reload", response.get("update-type"));
        assertThat("There should not be an automatic reload", response.get("update-type"), is(UpdateType.RESTART_OR_SKIP.label));

        assertFalse("This version 3 is Hot Reloadable", bundleManager.getCandidateAsConfigurationBundle().isHotReloadable());
        assertThat("No automatic reload, so new bundle no promoted", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertThat("No automatic reload, so new bundle no promoted", bundleUpdateTab.getUpdateVersion(), is("3"));
        assertThat("No automatic reload, so new bundle no promoted", bundleUpdateTab.getBundleVersion(), is("2"));
        assertThat("No automatic reload, so new bundle no promoted", Jenkins.get().getSystemMessage(), is("From version 2"));
    }

    // Bug fixes
    @Issue("BEE-40025")
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/initial-bundle-version")
    public void automaticReloadAndRestartWithInvalidBundle() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setAutomaticReload(true);
        configuration.setAutomaticRestart(true);
        configuration.save();

        assertTrue("There is the automatic reload", configuration.isAutomaticReload());
        assertTrue("There is the automatic restart", configuration.isAutomaticRestart());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From version 1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/AutomaticReloadTest/new-invalid-bundle-version").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        // Just in case the async reload hasn't finished
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        BundleVisualizationLink bundleUpdateTab = BundleVisualizationLink.get();
        assertThat("New bundle is rejected", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertNull("New bundle is rejected", bundleUpdateTab.getUpdateVersion());
        assertThat("New bundle is rejected", bundleUpdateTab.getBundleVersion(), is("1"));
        assertThat("New bundle is rejected", Jenkins.get().getSystemMessage(), is("From version 1"));

        Path bundleFolder = ConfigurationBundleManager.getBundleFolder();
        Path bundleDescriptor = bundleFolder.resolve("bundle.yaml");
        String descriptor = FileUtils.readFileToString(bundleDescriptor.toFile(), StandardCharsets.UTF_8);
        assertThat("New bundle isn't automatically promoted", descriptor, containsString("version: \"1\""));
    }

    private WebResponse requestWithToken(HttpMethod method, String endpoint, CJPRule.WebClient wc) throws Exception {
        try {
            WebRequest getRequest = new WebRequest(new URL(rule.getURL(), endpoint), method);
            return wc.withBasicApiToken("alice").getPage(getRequest).getWebResponse();
        } catch (FailingHttpStatusCodeException exception) {
            return exception.getResponse();
        }
    }
}
