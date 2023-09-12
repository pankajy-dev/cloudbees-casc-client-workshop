package com.cloudbees.jenkins.plugins.casc.config;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.junit.Before;
import org.junit.Test;
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
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;
import com.cloudbees.opscenter.client.casc.BundleReloadAction;
import com.cloudbees.opscenter.client.casc.ConfigurationStatus;
import com.cloudbees.opscenter.client.casc.ConfigurationUpdaterHelper;
import com.cloudbees.opscenter.client.casc.UpdateType;
import com.cloudbees.opscenter.client.casc.cli.BundleReloadCommand;
import com.cloudbees.opscenter.client.casc.cli.BundleVersionCheckerCommand;
import com.cloudbees.opscenter.client.casc.visualization.BundleVisualizationLink;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Test cases (instance up and running. See SkipNewVersionsTest in installation-manager for when the instance is starting):
 * I. With bundle update timing enabled:
 *  a. Skip All Versions is true and the new version is skipped
 *  b. Skip All Versions is true, but automatic reload and restart is true, so the new version is not skipped
 *  c. Skip All Versions is false, and the new version is installed
 * II. With bundle update timing disabled:
 *  a. Skip All Versions is true, but the new version is installed
 *  b. Skip All Versions is true, but automatic reload and restart is true, and the new version is installed
 *  c. Skip All Versions is false, and the new version is installed
 * III. Using the endpoint and CLI:
 *  a. Skip All Versions is true and the new version is skipped
 *  b. Skip All Versions is true, but automatic reload and restart is true, so the new version is not skipped
 *  c. Skip All Versions is false, and the new version is installed
 */
public class SkipNewVersionsTest extends AbstractCJPTest {

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


    // I.a
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipNewVersionsTest/initial-bundle-version")
    public void skipAllVersionsTrueAndEnabled() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setSkipNewVersions(true);
        configuration.setAutomaticReload(false);
        configuration.setAutomaticRestart(false);
        configuration.save();

        assertTrue("Skip All New Versions is configured", configuration.isSkipNewVersions());
        assertFalse("There is no automatic reload/restart", configuration.isAutomaticReload());
        assertFalse("There is no automatic reload/restart", configuration.isAutomaticRestart());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipNewVersionsTest/new-bundle-version").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is skipped", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        List<Path> updateLog = bundleManager.getUpdateLog().getHistoricalRecords();
        assertThat("New entry in the Update Log", updateLog, hasSize(2));
        Path newest = updateLog.get(0); // getHistoricalRecords return a SORTED list
        assertTrue("Marker file for skipped version is there", Files.exists(updateLog.get(0).resolve(BundleUpdateLog.SKIPPED_MARKER_FILE)));
        assertThat("Bundle version in the skipped bundle is the newest", FileUtils.readFileToString(newest.resolve("bundle").resolve("bundle.yaml").toFile(), StandardCharsets.UTF_8), containsString("version: \"2\""));
    }

    // II.a
    @Test
    @WithBundleUpdateTiming("false")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipNewVersionsTest/initial-bundle-version")
    public void skipAllVersionsTrueAndDisabled() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertFalse("Bundle Update Timing is disabled", configuration.isEnabled());

        configuration.setSkipNewVersions(true);
        configuration.setAutomaticReload(false);
        configuration.setAutomaticRestart(false);
        configuration.save();

        assertFalse("Skip All New Versions won't be configured as Bundle Update Timing is disabled", configuration.isSkipNewVersions());
        assertFalse("Automatic reload won't be configured as Bundle Update Timing is disabled", configuration.isAutomaticReload());
        assertFalse("Automatic restart won't be configured as Bundle Update Timing is disabled", configuration.isAutomaticRestart());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipNewVersionsTest/new-bundle-version").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is NOT skipped (Bundle Update Timing disabled)", bundleManager.getConfigurationBundle().getVersion(), is("2"));

        List<Path> updateLog = bundleManager.getUpdateLog().getHistoricalRecords();
        assertThat("New entry in the Update Log", updateLog, hasSize(2));
        Path newest = updateLog.get(0); // getHistoricalRecords return a SORTED list
        assertFalse("Marker file for skipped version is NOT there", Files.exists(updateLog.get(0).resolve(BundleUpdateLog.SKIPPED_MARKER_FILE)));
        assertThat("Bundle version in the skipped bundle is the newest", FileUtils.readFileToString(newest.resolve("bundle").resolve("bundle.yaml").toFile(), StandardCharsets.UTF_8), containsString("version: \"2\""));
    }

    // I.b
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipNewVersionsTest/initial-bundle-version")
    public void skipAllVersionsTrueButAutomaticReloadAndEnabled() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setSkipNewVersions(true);
        configuration.setAutomaticReload(true);
        configuration.setAutomaticRestart(true);
        configuration.save();

        assertTrue("Skip All New Versions is configured", configuration.isSkipNewVersions());
        assertTrue("There is an automatic reload/restart", configuration.isAutomaticReload());
        assertTrue("There is an automatic reload/restart", configuration.isAutomaticRestart());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipNewVersionsTest/new-bundle-version").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is NOT skipped as there's an automatic reload/restart", bundleManager.getConfigurationBundle().getVersion(), is("2"));

        List<Path> updateLog = bundleManager.getUpdateLog().getHistoricalRecords();
        assertThat("New entry in the Update Log", updateLog, hasSize(2));
        Path newest = updateLog.get(0); // getHistoricalRecords return a SORTED list
        assertFalse("Marker file for skipped version is removed as the bundle is promoted", Files.exists(updateLog.get(0).resolve(BundleUpdateLog.SKIPPED_MARKER_FILE)));
        assertThat("Bundle version in the skipped bundle is the newest", FileUtils.readFileToString(newest.resolve("bundle").resolve("bundle.yaml").toFile(), StandardCharsets.UTF_8), containsString("version: \"2\""));
    }

    // II.b
    @Test
    @WithBundleUpdateTiming("false")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipNewVersionsTest/initial-bundle-version")
    public void skipAllVersionsTrueButAutomaticReloadAndDisabled() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertFalse("Bundle Update Timing is disabled", configuration.isEnabled());

        configuration.setSkipNewVersions(true);
        configuration.setAutomaticReload(true);
        configuration.setAutomaticRestart(true);
        configuration.save();

        assertFalse("Skip All New Versions won't be configured as Bundle Update Timing is disabled", configuration.isSkipNewVersions());
        assertFalse("Automatic reload won't be configured as Bundle Update Timing is disabled", configuration.isAutomaticReload());
        assertFalse("Automatic restart won't be configured as Bundle Update Timing is disabled", configuration.isAutomaticRestart());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipNewVersionsTest/new-bundle-version").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is NOT skipped (Bundle Update Timing disabled)", bundleManager.getConfigurationBundle().getVersion(), is("2"));

        List<Path> updateLog = bundleManager.getUpdateLog().getHistoricalRecords();
        assertThat("New entry in the Update Log", updateLog, hasSize(2));
        Path newest = updateLog.get(0); // getHistoricalRecords return a SORTED list
        assertFalse("Marker file for skipped version is NOT there", Files.exists(updateLog.get(0).resolve(BundleUpdateLog.SKIPPED_MARKER_FILE)));
        assertThat("Bundle version in the skipped bundle is the newest", FileUtils.readFileToString(newest.resolve("bundle").resolve("bundle.yaml").toFile(), StandardCharsets.UTF_8), containsString("version: \"2\""));
    }

    // I.c
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipNewVersionsTest/initial-bundle-version")
    public void skipAllVersionsFalseAndEnabled() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setSkipNewVersions(false);
        configuration.save();

        assertFalse("Skip All New Versions is NOT configured", configuration.isSkipNewVersions());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipNewVersionsTest/new-bundle-version").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        BundleVisualizationLink bundleUpdateTab = BundleVisualizationLink.get();
        assertThat("New bundle is NOT skipped, but not promoted automatically", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("New bundle is NOT skipped, but not promoted automatically", bundleManager.getCandidateAsConfigurationBundle().getVersion(), is("2"));
        assertThat("New bundle is NOT skipped, but not promoted automatically", bundleUpdateTab.getUpdateVersion(), is("2"));
        assertThat("New bundle is NOT skipped, but not promoted automatically", bundleUpdateTab.getBundleVersion(), is("1"));

        List<Path> updateLog = bundleManager.getUpdateLog().getHistoricalRecords();
        assertThat("New entry in the Update Log", updateLog, hasSize(2));
        Path newest = updateLog.get(0); // getHistoricalRecords return a SORTED list
        assertFalse("Marker file for skipped version is NOT there", Files.exists(updateLog.get(0).resolve(BundleUpdateLog.SKIPPED_MARKER_FILE)));
        assertThat("Bundle version in the skipped bundle is the newest", FileUtils.readFileToString(newest.resolve("bundle").resolve("bundle.yaml").toFile(), StandardCharsets.UTF_8), containsString("version: \"2\""));

        // Let's reload as if the user had clicked the button, as it's not skipped
        assertTrue("This version 2 is Hot Reloadable", bundleManager.getCandidateAsConfigurationBundle().isHotReloadable());
        ConfigurationUpdaterHelper.promoteCandidate();
        ExtensionList.lookupSingleton(BundleReloadAction.class).executeReload(false);
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        assertThat("New bundle is now loaded", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertNull("New bundle is now loaded", bundleUpdateTab.getUpdateVersion());
        assertThat("New bundle is now loaded", bundleUpdateTab.getBundleVersion(), is("2"));
    }

    // II.c
    @Test
    @WithBundleUpdateTiming("false")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipNewVersionsTest/initial-bundle-version")
    public void skipAllVersionsFalseAndDisabled() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertFalse("Bundle Update Timing is disabled", configuration.isEnabled());

        configuration.setSkipNewVersions(false);
        configuration.save();

        assertFalse("Skip All New Versions won't be configured as Bundle Update Timing is disabled", configuration.isSkipNewVersions());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipNewVersionsTest/new-bundle-version").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is NOT skipped (Bundle Update Timing disabled)", bundleManager.getConfigurationBundle().getVersion(), is("2"));

        List<Path> updateLog = bundleManager.getUpdateLog().getHistoricalRecords();
        assertThat("New entry in the Update Log", updateLog, hasSize(2));
        Path newest = updateLog.get(0); // getHistoricalRecords return a SORTED list
        assertFalse("Marker file for skipped version is NOT there", Files.exists(updateLog.get(0).resolve(BundleUpdateLog.SKIPPED_MARKER_FILE)));
        assertThat("Bundle version in the skipped bundle is the newest", FileUtils.readFileToString(newest.resolve("bundle").resolve("bundle.yaml").toFile(), StandardCharsets.UTF_8), containsString("version: \"2\""));
    }

    // III.a
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipNewVersionsTest/initial-bundle-version")
    public void skipAllVersionsTrueAndEnabled_endpoint() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setSkipNewVersions(true);
        configuration.setAutomaticReload(false);
        configuration.setAutomaticRestart(false);
        configuration.save();

        assertTrue("Skip All New Versions is configured", configuration.isSkipNewVersions());
        assertFalse("There is no automatic reload/restart", configuration.isAutomaticReload());
        assertFalse("There is no automatic reload/restart", configuration.isAutomaticRestart());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipNewVersionsTest/new-bundle-version").toFile().getAbsolutePath());
        CJPRule.WebClient wc = rule.createWebClient();
        WebResponse resp = requestWithToken(HttpMethod.GET, "casc-bundle-mgnt/check-bundle-update", wc);
        JSONObject response = JSONObject.fromObject(resp.getContentAsString());
        assertNotNull("There should be an automatic reload", response.get("update-type"));
        assertThat("There should be an automatic reload", response.get("update-type"), is(UpdateType.SKIPPED.label));

        assertThat("New bundle is skipped", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        List<Path> updateLog = bundleManager.getUpdateLog().getHistoricalRecords();
        assertThat("New entry in the Update Log", updateLog, hasSize(2));
        Path newest = updateLog.get(0); // getHistoricalRecords return a SORTED list
        assertTrue("Marker file for skipped version is there", Files.exists(updateLog.get(0).resolve(BundleUpdateLog.SKIPPED_MARKER_FILE)));
        assertThat("Bundle version in the skipped bundle is the newest", FileUtils.readFileToString(newest.resolve("bundle").resolve("bundle.yaml").toFile(), StandardCharsets.UTF_8), containsString("version: \"2\""));
    }

    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipNewVersionsTest/initial-bundle-version")
    public void skipAllVersionsTrueAndEnabled_cli() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setSkipNewVersions(true);
        configuration.setAutomaticReload(false);
        configuration.setAutomaticRestart(false);
        configuration.save();

        assertTrue("Skip All New Versions is configured", configuration.isSkipNewVersions());
        assertFalse("There is no automatic reload/restart", configuration.isAutomaticReload());
        assertFalse("There is no automatic reload/restart", configuration.isAutomaticRestart());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipNewVersionsTest/new-bundle-version").toFile().getAbsolutePath());
        CLICommandInvoker.Result result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser("alice").invoke();
        JSONObject response = JSONObject.fromObject(result.stdout());
        assertNotNull("There should be an automatic reload", response.get("update-type"));
        assertThat("There should be an automatic reload", response.get("update-type"), is(UpdateType.SKIPPED.label));

        assertThat("New bundle is skipped", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        List<Path> updateLog = bundleManager.getUpdateLog().getHistoricalRecords();
        assertThat("New entry in the Update Log", updateLog, hasSize(2));
        Path newest = updateLog.get(0); // getHistoricalRecords return a SORTED list
        assertTrue("Marker file for skipped version is there", Files.exists(updateLog.get(0).resolve(BundleUpdateLog.SKIPPED_MARKER_FILE)));
        assertThat("Bundle version in the skipped bundle is the newest", FileUtils.readFileToString(newest.resolve("bundle").resolve("bundle.yaml").toFile(), StandardCharsets.UTF_8), containsString("version: \"2\""));
    }

    // III.b
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipNewVersionsTest/initial-bundle-version")
    public void skipAllVersionsTrueButAutomaticReloadAndEnabled_endpoint() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setSkipNewVersions(true);
        configuration.setAutomaticReload(true);
        configuration.setAutomaticRestart(true);
        configuration.save();

        assertTrue("Skip All New Versions is configured", configuration.isSkipNewVersions());
        assertTrue("There is an automatic reload/restart", configuration.isAutomaticReload());
        assertTrue("There is an automatic reload/restart", configuration.isAutomaticRestart());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipNewVersionsTest/new-bundle-version").toFile().getAbsolutePath());
        CJPRule.WebClient wc = rule.createWebClient();
        WebResponse resp = requestWithToken(HttpMethod.GET, "casc-bundle-mgnt/check-bundle-update", wc);
        JSONObject response = JSONObject.fromObject(resp.getContentAsString());
        assertNotNull("There should be an automatic reload", response.get("update-type"));
        assertThat("There should be an automatic reload", response.get("update-type"), is(UpdateType.AUTOMATIC_RELOAD.label));

        assertThat("New bundle is NOT skipped as there's an automatic reload/restart", bundleManager.getConfigurationBundle().getVersion(), is("2"));

        List<Path> updateLog = bundleManager.getUpdateLog().getHistoricalRecords();
        assertThat("New entry in the Update Log", updateLog, hasSize(2));
        Path newest = updateLog.get(0); // getHistoricalRecords return a SORTED list
        assertFalse("Marker file for skipped version is removed as the bundle is promoted", Files.exists(updateLog.get(0).resolve(BundleUpdateLog.SKIPPED_MARKER_FILE)));
        assertThat("Bundle version in the skipped bundle is the newest", FileUtils.readFileToString(newest.resolve("bundle").resolve("bundle.yaml").toFile(), StandardCharsets.UTF_8), containsString("version: \"2\""));
    }

    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipNewVersionsTest/initial-bundle-version")
    public void skipAllVersionsTrueButAutomaticReloadAndEnabled_cli() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setSkipNewVersions(true);
        configuration.setAutomaticReload(true);
        configuration.setAutomaticRestart(true);
        configuration.save();

        assertTrue("Skip All New Versions is configured", configuration.isSkipNewVersions());
        assertTrue("There is an automatic reload/restart", configuration.isAutomaticReload());
        assertTrue("There is an automatic reload/restart", configuration.isAutomaticRestart());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipNewVersionsTest/new-bundle-version").toFile().getAbsolutePath());
        CLICommandInvoker.Result result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser("alice").invoke();
        JSONObject response = JSONObject.fromObject(result.stdout());
        assertNotNull("There should be an automatic reload", response.get("update-type"));
        assertThat("There should be an automatic reload", response.get("update-type"), is(UpdateType.AUTOMATIC_RELOAD.label));

        assertThat("New bundle is NOT skipped as there's an automatic reload/restart", bundleManager.getConfigurationBundle().getVersion(), is("2"));

        List<Path> updateLog = bundleManager.getUpdateLog().getHistoricalRecords();
        assertThat("New entry in the Update Log", updateLog, hasSize(2));
        Path newest = updateLog.get(0); // getHistoricalRecords return a SORTED list
        assertFalse("Marker file for skipped version is removed as the bundle is promoted", Files.exists(updateLog.get(0).resolve(BundleUpdateLog.SKIPPED_MARKER_FILE)));
        assertThat("Bundle version in the skipped bundle is the newest", FileUtils.readFileToString(newest.resolve("bundle").resolve("bundle.yaml").toFile(), StandardCharsets.UTF_8), containsString("version: \"2\""));
    }

    // III.c
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipNewVersionsTest/initial-bundle-version")
    public void skipAllVersionsFalseAndEnabled_endpoint() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setSkipNewVersions(false);
        configuration.save();

        assertFalse("Skip All New Versions is NOT configured", configuration.isSkipNewVersions());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipNewVersionsTest/new-bundle-version").toFile().getAbsolutePath());
        CJPRule.WebClient wc = rule.createWebClient();
        WebResponse resp = requestWithToken(HttpMethod.GET, "casc-bundle-mgnt/check-bundle-update", wc);
        JSONObject response = JSONObject.fromObject(resp.getContentAsString());
        assertNotNull("There should be an automatic reload", response.get("update-type"));
        assertThat("There should be an automatic reload", response.get("update-type"), is(UpdateType.RELOAD_OR_SKIP.label));

        BundleVisualizationLink bundleUpdateTab = BundleVisualizationLink.get();
        assertThat("New bundle is NOT skipped, but not promoted automatically", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("New bundle is NOT skipped, but not promoted automatically", bundleManager.getCandidateAsConfigurationBundle().getVersion(), is("2"));
        assertThat("New bundle is NOT skipped, but not promoted automatically", bundleUpdateTab.getUpdateVersion(), is("2"));
        assertThat("New bundle is NOT skipped, but not promoted automatically", bundleUpdateTab.getBundleVersion(), is("1"));

        List<Path> updateLog = bundleManager.getUpdateLog().getHistoricalRecords();
        assertThat("New entry in the Update Log", updateLog, hasSize(2));
        Path newest = updateLog.get(0); // getHistoricalRecords return a SORTED list
        assertFalse("Marker file for skipped version is NOT there", Files.exists(updateLog.get(0).resolve(BundleUpdateLog.SKIPPED_MARKER_FILE)));
        assertThat("Bundle version in the skipped bundle is the newest", FileUtils.readFileToString(newest.resolve("bundle").resolve("bundle.yaml").toFile(), StandardCharsets.UTF_8), containsString("version: \"2\""));

        // Let's reload as if the user had clicked the button, as it's not skipped
        assertTrue("This version 2 is Hot Reloadable", bundleManager.getCandidateAsConfigurationBundle().isHotReloadable());
        resp = requestWithToken(HttpMethod.POST, "casc-bundle-mgnt/reload-bundle", wc);
        response = JSONObject.fromObject(resp.getContentAsString());
        assertNotNull("Bundle reloaded", response.get("reloaded"));
        assertTrue("Bundle reloaded", response.getBoolean("reloaded"));
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        assertThat("New bundle is now loaded", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertNull("New bundle is now loaded", bundleUpdateTab.getUpdateVersion());
        assertThat("New bundle is now loaded", bundleUpdateTab.getBundleVersion(), is("2"));
    }

    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipNewVersionsTest/initial-bundle-version")
    public void skipAllVersionsFalseAndEnabled_cli() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setSkipNewVersions(false);
        configuration.save();

        assertFalse("Skip All New Versions is NOT configured", configuration.isSkipNewVersions());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipNewVersionsTest/new-bundle-version").toFile().getAbsolutePath());
        CLICommandInvoker.Result result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser("alice").invoke();
        JSONObject response = JSONObject.fromObject(result.stdout());
        assertNotNull("There should be an automatic reload", response.get("update-type"));
        assertThat("There should be an automatic reload", response.get("update-type"), is(UpdateType.RELOAD_OR_SKIP.label));

        BundleVisualizationLink bundleUpdateTab = BundleVisualizationLink.get();
        assertThat("New bundle is NOT skipped, but not promoted automatically", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("New bundle is NOT skipped, but not promoted automatically", bundleManager.getCandidateAsConfigurationBundle().getVersion(), is("2"));
        assertThat("New bundle is NOT skipped, but not promoted automatically", bundleUpdateTab.getUpdateVersion(), is("2"));
        assertThat("New bundle is NOT skipped, but not promoted automatically", bundleUpdateTab.getBundleVersion(), is("1"));

        List<Path> updateLog = bundleManager.getUpdateLog().getHistoricalRecords();
        assertThat("New entry in the Update Log", updateLog, hasSize(2));
        Path newest = updateLog.get(0); // getHistoricalRecords return a SORTED list
        assertFalse("Marker file for skipped version is NOT there", Files.exists(updateLog.get(0).resolve(BundleUpdateLog.SKIPPED_MARKER_FILE)));
        assertThat("Bundle version in the skipped bundle is the newest", FileUtils.readFileToString(newest.resolve("bundle").resolve("bundle.yaml").toFile(), StandardCharsets.UTF_8), containsString("version: \"2\""));

        // Let's reload as if the user had clicked the button, as it's not skipped
        assertTrue("This version 2 is Hot Reloadable", bundleManager.getCandidateAsConfigurationBundle().isHotReloadable());
        result = new CLICommandInvoker(rule, BundleReloadCommand.COMMAND_NAME).asUser("alice").invoke();
        assertThat("Bundle reloaded", result.stdout().trim(), is("Bundle successfully reloaded."));
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        assertThat("New bundle is now loaded", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertNull("New bundle is now loaded", bundleUpdateTab.getUpdateVersion());
        assertThat("New bundle is now loaded", bundleUpdateTab.getBundleVersion(), is("2"));
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
