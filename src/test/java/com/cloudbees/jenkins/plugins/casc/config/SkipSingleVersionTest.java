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
import com.cloudbees.opscenter.client.casc.ConfigurationUpdaterMonitor;
import com.cloudbees.opscenter.client.casc.UpdateType;
import com.cloudbees.opscenter.client.casc.cli.BundleVersionCheckerCommand;
import com.cloudbees.opscenter.client.casc.cli.BundleVersionSkipCommand;
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
 * Test cases (instance up and running):
 * I. With bundle update timing enabled:
 *  a. Automatic reload, Automatic restart and Skip All Versions not configured, so 3 buttons are available in tab
 *    i. Skip the version, so it is not promoted or installed
 *    ii. Reload, so it is promoted and installed and it cannot be skipped
 *  b. Automatic reload, Automatic restart and Skip All Versions not configured, so 3 buttons are available in administrative monitor
 *    i. Skip the version, so it is not promoted or installed
 *    ii. Reload, so it is promoted and installed and it cannot be skipped
 *  c. Automatic reload
 *    - no buttons
 *    - no administrative monitor
 *    - the new version is promoted and reloaded
 *  d. Skip All new version
 *    - no buttons
 *    - no administrative monitor
 *    - the new version is not promoted or reloaded
 * II. With bundle update timing disabled:
 *  a. Automatic reload, Automatic restart and Skip All Versions not configured, so 2 buttons are available in tab
 *  b. Automatic reload, Automatic restart and Skip All Versions not configured, so 2 buttons are available in administrative monitor
 * III. Using the endpoint and CLI:
 *  a. Automatic reload, Automatic restart and Skip All Versions not configured, so 3 buttons are available in tab. Skip the version, so it is not promoted or installed
 *  b. Automatic reload, Automatic restart or Skip All Versions configured so trying to skip rise an error
 *  c. Bundle update timing disabled
 */
public class SkipSingleVersionTest extends AbstractCJPTest {

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

    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/initial-bundle-version")
    // I.a.i
    public void skipFromUpdateTab_enabled() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        assertFalse("Skip All New Versions is not configured", configuration.isSkipNewVersions());
        assertFalse("There is no automatic reload/restart", configuration.isAutomaticReload());
        assertFalse("There is no automatic reload/restart", configuration.isAutomaticRestart());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From Version 1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/new-bundle-version").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("Current Version is still v1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Current Version is still v1", Jenkins.get().getSystemMessage(), is("From Version 1"));
        assertTrue("There is a candidate", ConfigurationStatus.INSTANCE.isUpdateAvailable());
        assertThat("There is a candidate, and it's v2", bundleManager.getCandidateAsConfigurationBundle().getVersion(), is("2"));

        BundleVisualizationLink link = BundleVisualizationLink.get();
        assertTrue("Bundle Update tab shows the button bar", link.isUpdateAvailable());
        assertThat("Bundle Update tab shows the new version", link.getUpdateVersion(), is("2"));
        assertTrue("Bundle Update tab shows the Reload button", link.isHotReloadable());
        assertTrue("Bundle Update tab shows the Skip button", link.canManualSkip());

        // Skip
        ConfigurationUpdaterHelper.skipCandidate();
        assertThat("Current Version is still v1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Current Version is still v1", Jenkins.get().getSystemMessage(), is("From Version 1"));
        assertFalse("There is still a candidate but the button bar is not displayed", ConfigurationStatus.INSTANCE.isUpdateAvailable());
        assertThat("There is still a candidate but the button bar is not displayed", bundleManager.getCandidateAsConfigurationBundle().getVersion(), is("2"));

        assertFalse("Bundle Update tab doesn't show the button bar", link.isUpdateAvailable());
        assertNull("Bundle Update tab doesn't the new version", link.getUpdateVersion());

        List<Path> updateLog = bundleManager.getUpdateLog().getHistoricalRecords();
        assertThat("Newest entry in the Update Log", updateLog, hasSize(2));
        Path newest = updateLog.get(0); // getHistoricalRecords return a SORTED list
        assertTrue("Marker file for skipped version is there", Files.exists(updateLog.get(0).resolve(BundleUpdateLog.SKIPPED_MARKER_FILE)));
        assertThat("Bundle version in the skipped bundle is the newest", FileUtils.readFileToString(newest.resolve("bundle").resolve("bundle.yaml").toFile(), StandardCharsets.UTF_8), containsString("version: \"2\""));
    }

    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/initial-bundle-version")
    // I.a.ii
    public void reloadFromUpdateTab_enabled() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        assertFalse("Skip All New Versions is not configured", configuration.isSkipNewVersions());
        assertFalse("There is no automatic reload/restart", configuration.isAutomaticReload());
        assertFalse("There is no automatic reload/restart", configuration.isAutomaticRestart());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From Version 1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/new-bundle-version").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("Current Version is still v1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Current Version is still v1", Jenkins.get().getSystemMessage(), is("From Version 1"));
        assertTrue("There is a candidate", ConfigurationStatus.INSTANCE.isUpdateAvailable());
        assertThat("There is a candidate, and it's v2", bundleManager.getCandidateAsConfigurationBundle().getVersion(), is("2"));

        BundleVisualizationLink link = BundleVisualizationLink.get();
        assertTrue("Bundle Update tab shows the button bar", link.isUpdateAvailable());
        assertThat("Bundle Update tab shows the new version", link.getUpdateVersion(), is("2"));
        assertTrue("Bundle Update tab shows the Reload button", link.isHotReloadable());
        assertTrue("Bundle Update tab shows the Skip button", link.canManualSkip());

        // Reload (as if the user had clicked on the button)
        assertTrue("Candidate is promoted", ConfigurationUpdaterHelper.promoteCandidate());
        ExtensionList.lookupSingleton(BundleReloadAction.class).executeReload(false);
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        assertThat("Current Version is now v2", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertThat("Current Version is now v2", Jenkins.get().getSystemMessage(), is("From Version 2"));
        assertFalse("There is no candidate anymore", ConfigurationStatus.INSTANCE.isUpdateAvailable());
        assertNull("There is no candidate anymore", bundleManager.getCandidateAsConfigurationBundle());
        assertNull("There is no candidate anymore", bundleManager.getUpdateLog().getCandidateBundle());

        assertFalse("Bundle Update tab doesn't show the button bar", link.isUpdateAvailable());
        assertNull("Bundle Update tab doesn't the new version", link.getUpdateVersion());

        List<Path> updateLog = bundleManager.getUpdateLog().getHistoricalRecords();
        assertThat("Newest entry in the Update Log", updateLog, hasSize(2));
        Path newest = updateLog.get(0); // getHistoricalRecords return a SORTED list
        assertFalse("Marker file for skipped version is NOT there", Files.exists(updateLog.get(0).resolve(BundleUpdateLog.SKIPPED_MARKER_FILE)));
        assertThat("Bundle version in the newest record", FileUtils.readFileToString(newest.resolve("bundle").resolve("bundle.yaml").toFile(), StandardCharsets.UTF_8), containsString("version: \"2\""));
    }

    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/initial-bundle-version")
    // I.b.i
    public void skipFromMonitor_enabled() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        assertFalse("Skip All New Versions is not configured", configuration.isSkipNewVersions());
        assertFalse("There is no automatic reload/restart", configuration.isAutomaticReload());
        assertFalse("There is no automatic reload/restart", configuration.isAutomaticRestart());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From Version 1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/new-bundle-version").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("Current Version is still v1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Current Version is still v1", Jenkins.get().getSystemMessage(), is("From Version 1"));
        assertTrue("There is a candidate", ConfigurationStatus.INSTANCE.isUpdateAvailable());
        assertThat("There is a candidate, and it's v2", bundleManager.getCandidateAsConfigurationBundle().getVersion(), is("2"));


        ConfigurationUpdaterMonitor monitor = ExtensionList.lookupSingleton(ConfigurationUpdaterMonitor.class);
        assertTrue("Monitor shows up", monitor.isActivated());
        assertTrue("Monitor shows the button bar", monitor.isUpdateAvailable());
        assertThat("Monitor shows the new version", monitor.getUpdateVersion(), is("2"));
        assertTrue("Monitor shows the Reload button", monitor.isHotReloadable());
        assertTrue("Monitor shows the Skip button", monitor.canManualSkip());

        // Skip
        ConfigurationUpdaterHelper.skipCandidate();
        assertThat("Current Version is still v1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Current Version is still v1", Jenkins.get().getSystemMessage(), is("From Version 1"));
        assertFalse("There is still a candidate but the button bar is not displayed", ConfigurationStatus.INSTANCE.isUpdateAvailable());
        assertThat("There is still a candidate but the button bar is not displayed", bundleManager.getCandidateAsConfigurationBundle().getVersion(), is("2"));

        assertFalse("Monitor does not show up", monitor.isActivated());

        List<Path> updateLog = bundleManager.getUpdateLog().getHistoricalRecords();
        assertThat("Newest entry in the Update Log", updateLog, hasSize(2));
        Path newest = updateLog.get(0); // getHistoricalRecords return a SORTED list
        assertTrue("Marker file for skipped version is there", Files.exists(updateLog.get(0).resolve(BundleUpdateLog.SKIPPED_MARKER_FILE)));
        assertThat("Bundle version in the skipped bundle is the newest", FileUtils.readFileToString(newest.resolve("bundle").resolve("bundle.yaml").toFile(), StandardCharsets.UTF_8), containsString("version: \"2\""));
    }

    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/initial-bundle-version")
    // I.b.ii
    public void reloadFromMonitor_enabled() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        assertFalse("Skip All New Versions is not configured", configuration.isSkipNewVersions());
        assertFalse("There is no automatic reload/restart", configuration.isAutomaticReload());
        assertFalse("There is no automatic reload/restart", configuration.isAutomaticRestart());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From Version 1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/new-bundle-version").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("Current Version is still v1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Current Version is still v1", Jenkins.get().getSystemMessage(), is("From Version 1"));
        assertTrue("There is a candidate", ConfigurationStatus.INSTANCE.isUpdateAvailable());
        assertThat("There is a candidate, and it's v2", bundleManager.getCandidateAsConfigurationBundle().getVersion(), is("2"));

        ConfigurationUpdaterMonitor monitor = ExtensionList.lookupSingleton(ConfigurationUpdaterMonitor.class);
        assertTrue("Monitor shows up", monitor.isActivated());
        assertTrue("Monitor shows the button bar", monitor.isUpdateAvailable());
        assertThat("Monitor shows the new version", monitor.getUpdateVersion(), is("2"));
        assertTrue("Monitor shows the Reload button", monitor.isHotReloadable());
        assertTrue("Monitor shows the Skip button", monitor.canManualSkip());

        // Reload (as if the user had clicked on the button)
        assertTrue("Candidate is promoted", ConfigurationUpdaterHelper.promoteCandidate());
        ExtensionList.lookupSingleton(BundleReloadAction.class).executeReload(false);
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        assertThat("Current Version is now v2", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertThat("Current Version is now v2", Jenkins.get().getSystemMessage(), is("From Version 2"));
        assertFalse("There is no candidate anymore", ConfigurationStatus.INSTANCE.isUpdateAvailable());
        assertNull("There is no candidate anymore", bundleManager.getCandidateAsConfigurationBundle());
        assertNull("There is no candidate anymore", bundleManager.getUpdateLog().getCandidateBundle());

        assertFalse("Monitor does not show up", monitor.isActivated());

        List<Path> updateLog = bundleManager.getUpdateLog().getHistoricalRecords();
        assertThat("Newest entry in the Update Log", updateLog, hasSize(2));
        Path newest = updateLog.get(0); // getHistoricalRecords return a SORTED list
        assertFalse("Marker file for skipped version is NOT there", Files.exists(updateLog.get(0).resolve(BundleUpdateLog.SKIPPED_MARKER_FILE)));
        assertThat("Bundle version in the newest record", FileUtils.readFileToString(newest.resolve("bundle").resolve("bundle.yaml").toFile(), StandardCharsets.UTF_8), containsString("version: \"2\""));
    }

    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/initial-bundle-version")
    // I.c
    public void automaticReload_enabled() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setAutomaticReload(true);
        configuration.save();

        assertFalse("Skip All New Versions is not configured", configuration.isSkipNewVersions());
        assertTrue("There is an automatic reload", configuration.isAutomaticReload());
        assertFalse("There is no automatic restart", configuration.isAutomaticRestart());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From Version 1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/new-bundle-version").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        assertThat("Current Version is now v2", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertThat("Current Version is now v2", Jenkins.get().getSystemMessage(), is("From Version 2"));
        assertFalse("There is no candidate", ConfigurationStatus.INSTANCE.isUpdateAvailable());
        assertNull("There is no candidate", bundleManager.getCandidateAsConfigurationBundle());
        assertNull("There is no candidate", bundleManager.getUpdateLog().getCandidateBundle());

        BundleVisualizationLink link = BundleVisualizationLink.get();
        assertFalse("Bundle Update tab doesn't show the button bar", link.isUpdateAvailable());
        assertNull("Bundle Update tab doesn't the new version", link.getUpdateVersion());
        ConfigurationUpdaterMonitor monitor = ExtensionList.lookupSingleton(ConfigurationUpdaterMonitor.class);
        assertFalse("Monitor does not show up", monitor.isActivated());

        List<Path> updateLog = bundleManager.getUpdateLog().getHistoricalRecords();
        assertThat("Newest entry in the Update Log", updateLog, hasSize(2));
        Path newest = updateLog.get(0); // getHistoricalRecords return a SORTED list
        assertFalse("Marker file for skipped version is NOT there", Files.exists(updateLog.get(0).resolve(BundleUpdateLog.SKIPPED_MARKER_FILE)));
        assertThat("Bundle version in the newest record", FileUtils.readFileToString(newest.resolve("bundle").resolve("bundle.yaml").toFile(), StandardCharsets.UTF_8), containsString("version: \"2\""));
    }

    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/initial-bundle-version")
    // I.d
    public void skipNewVersions_enabled() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setSkipNewVersions(true);
        configuration.save();

        assertTrue("Skip All New Versions is configured", configuration.isSkipNewVersions());
        assertFalse("There is no automatic reload/restart", configuration.isAutomaticReload());
        assertFalse("There is no automatic reload/restart", configuration.isAutomaticRestart());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From Version 1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/new-bundle-version").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("Current Version is still v1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Current Version is still v1", Jenkins.get().getSystemMessage(), is("From Version 1"));
        assertFalse("There is a candidate but the button bar is not displayed", ConfigurationStatus.INSTANCE.isUpdateAvailable());
        assertThat("There is a candidate but the button bar is not displayed", bundleManager.getCandidateAsConfigurationBundle().getVersion(), is("2"));

        BundleVisualizationLink link = BundleVisualizationLink.get();
        assertFalse("Bundle Update tab doesn't show the button bar", link.isUpdateAvailable());
        assertNull("Bundle Update tab doesn't the new version", link.getUpdateVersion());
        ConfigurationUpdaterMonitor monitor = ExtensionList.lookupSingleton(ConfigurationUpdaterMonitor.class);
        assertFalse("Monitor does not show up", monitor.isActivated());

        List<Path> updateLog = bundleManager.getUpdateLog().getHistoricalRecords();
        assertThat("Newest entry in the Update Log", updateLog, hasSize(2));
        Path newest = updateLog.get(0); // getHistoricalRecords return a SORTED list
        assertTrue("Marker file for skipped version is there", Files.exists(updateLog.get(0).resolve(BundleUpdateLog.SKIPPED_MARKER_FILE)));
        assertThat("Bundle version in the skipped bundle is the newest", FileUtils.readFileToString(newest.resolve("bundle").resolve("bundle.yaml").toFile(), StandardCharsets.UTF_8), containsString("version: \"2\""));
    }

    @Test
    @WithBundleUpdateTiming("false")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/initial-bundle-version")
    // II.a y II.b
    public void tabAndMonitor_disabled() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertFalse("Bundle Update Timing is disabled", configuration.isEnabled());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From Version 1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/new-bundle-version").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("Current Version is still v1, but the bundle is promoted as Bundle Update Timing is disabled", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertThat("Current Version is still v1, but the bundle is promoted as Bundle Update Timing is disabled", Jenkins.get().getSystemMessage(), is("From Version 1"));
        assertTrue("There is a candidate", ConfigurationStatus.INSTANCE.isUpdateAvailable());
        assertNull("There is a candidate, but it's already promoted", bundleManager.getUpdateLog().getCandidateBundle());
        assertNull("There is a candidate, but it's already promoted", bundleManager.getCandidateAsConfigurationBundle());

        BundleVisualizationLink link = BundleVisualizationLink.get();
        assertTrue("Bundle Update tab shows the button bar", link.isUpdateAvailable());
        assertThat("Bundle Update tab shows the new version", link.getUpdateVersion(), is("2"));
        assertTrue("Bundle Update tab shows the Reload button", link.isHotReloadable());
        assertFalse("Bundle Update tab does not show the Skip button", link.canManualSkip());

        ConfigurationUpdaterMonitor monitor = ExtensionList.lookupSingleton(ConfigurationUpdaterMonitor.class);
        assertTrue("Monitor shows up", monitor.isActivated());
        assertTrue("Monitor shows the button bar", monitor.isUpdateAvailable());
        assertThat("Monitor shows the new version", monitor.getUpdateVersion(), is("2"));
        assertTrue("Monitor shows the Reload button", monitor.isHotReloadable());
        assertFalse("Monitor does not show the Skip button", monitor.canManualSkip());
    }

    // III.a
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/initial-bundle-version")
    public void skipFromEndpoint_enabled() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        assertFalse("Skip All New Versions is not configured", configuration.isSkipNewVersions());
        assertFalse("There is no automatic reload/restart", configuration.isAutomaticReload());
        assertFalse("There is no automatic reload/restart", configuration.isAutomaticRestart());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From Version 1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/new-bundle-version").toFile().getAbsolutePath());
        CJPRule.WebClient wc = rule.createWebClient();
        WebResponse resp = requestWithToken(HttpMethod.GET, "casc-bundle-mgnt/check-bundle-update", wc);
        JSONObject response = JSONObject.fromObject(resp.getContentAsString());
        assertNotNull("The bundle can be skipped", response.get("update-type"));
        assertThat("The bundle can be skipped", response.get("update-type"), is(UpdateType.RELOAD_OR_SKIP.label));

        assertThat("Current Version is still v1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Current Version is still v1", Jenkins.get().getSystemMessage(), is("From Version 1"));
        assertTrue("There is a candidate", ConfigurationStatus.INSTANCE.isUpdateAvailable());
        assertThat("There is a candidate, and it's v2", bundleManager.getCandidateAsConfigurationBundle().getVersion(), is("2"));

        BundleVisualizationLink link = BundleVisualizationLink.get();
        assertTrue("Bundle Update tab shows the button bar", link.isUpdateAvailable());
        assertThat("Bundle Update tab shows the new version", link.getUpdateVersion(), is("2"));
        assertTrue("Bundle Update tab shows the Reload button", link.isHotReloadable());
        assertTrue("Bundle Update tab shows the Skip button", link.canManualSkip());

        // Skip
        resp = requestWithToken(HttpMethod.POST, "casc-bundle-mgnt/casc-bundle-skip", wc);
        response = JSONObject.fromObject(resp.getContentAsString());
        assertNotNull("The bundle has been skipped", response.get("skipped"));
        assertTrue("The bundle has been skipped", response.getBoolean("skipped"));

        assertThat("Current Version is still v1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Current Version is still v1", Jenkins.get().getSystemMessage(), is("From Version 1"));
        assertFalse("There is still a candidate but the button bar is not displayed", ConfigurationStatus.INSTANCE.isUpdateAvailable());
        assertThat("There is still a candidate but the button bar is not displayed", bundleManager.getCandidateAsConfigurationBundle().getVersion(), is("2"));

        assertFalse("Bundle Update tab doesn't show the button bar", link.isUpdateAvailable());
        assertNull("Bundle Update tab doesn't the new version", link.getUpdateVersion());

        List<Path> updateLog = bundleManager.getUpdateLog().getHistoricalRecords();
        assertThat("Newest entry in the Update Log", updateLog, hasSize(2));
        Path newest = updateLog.get(0); // getHistoricalRecords return a SORTED list
        assertTrue("Marker file for skipped version is there", Files.exists(updateLog.get(0).resolve(BundleUpdateLog.SKIPPED_MARKER_FILE)));
        assertThat("Bundle version in the skipped bundle is the newest", FileUtils.readFileToString(newest.resolve("bundle").resolve("bundle.yaml").toFile(), StandardCharsets.UTF_8), containsString("version: \"2\""));

        // Call the skip endpoint without a candidate in the instance
        resp = requestWithToken(HttpMethod.POST, "casc-bundle-mgnt/casc-bundle-skip", wc);
        response = JSONObject.fromObject(resp.getContentAsString());
        assertThat("There is no bundle to skip", resp.getStatusCode(), is(404));
        assertNotNull("There is no bundle to skip", response.get("error"));
        assertThat("There is no bundle to skip", response.get("error"), is("Bundle version to skip not found."));
    }

    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/initial-bundle-version")
    public void skipFromCLI_enabled() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        assertFalse("Skip All New Versions is not configured", configuration.isSkipNewVersions());
        assertFalse("There is no automatic reload/restart", configuration.isAutomaticReload());
        assertFalse("There is no automatic reload/restart", configuration.isAutomaticRestart());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From Version 1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/new-bundle-version").toFile().getAbsolutePath());
        CLICommandInvoker.Result result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser("alice").invoke();
        JSONObject response = JSONObject.fromObject(result.stdout());
        assertNotNull("The bundle can be skipped", response.get("update-type"));
        assertThat("The bundle can be skipped", response.get("update-type"), is(UpdateType.RELOAD_OR_SKIP.label));

        assertThat("Current Version is still v1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Current Version is still v1", Jenkins.get().getSystemMessage(), is("From Version 1"));
        assertTrue("There is a candidate", ConfigurationStatus.INSTANCE.isUpdateAvailable());
        assertThat("There is a candidate, and it's v2", bundleManager.getCandidateAsConfigurationBundle().getVersion(), is("2"));

        BundleVisualizationLink link = BundleVisualizationLink.get();
        assertTrue("Bundle Update tab shows the button bar", link.isUpdateAvailable());
        assertThat("Bundle Update tab shows the new version", link.getUpdateVersion(), is("2"));
        assertTrue("Bundle Update tab shows the Reload button", link.isHotReloadable());
        assertTrue("Bundle Update tab shows the Skip button", link.canManualSkip());

        // Skip
        result = new CLICommandInvoker(rule, BundleVersionSkipCommand.COMMAND_NAME).asUser("alice").invoke();
        assertThat("The bundle has been skipped", result.stdout().trim(), is("Bundle version skipped."));

        assertThat("Current Version is still v1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Current Version is still v1", Jenkins.get().getSystemMessage(), is("From Version 1"));
        assertFalse("There is still a candidate but the button bar is not displayed", ConfigurationStatus.INSTANCE.isUpdateAvailable());
        assertThat("There is still a candidate but the button bar is not displayed", bundleManager.getCandidateAsConfigurationBundle().getVersion(), is("2"));

        assertFalse("Bundle Update tab doesn't show the button bar", link.isUpdateAvailable());
        assertNull("Bundle Update tab doesn't the new version", link.getUpdateVersion());

        List<Path> updateLog = bundleManager.getUpdateLog().getHistoricalRecords();
        assertThat("Newest entry in the Update Log", updateLog, hasSize(2));
        Path newest = updateLog.get(0); // getHistoricalRecords return a SORTED list
        assertTrue("Marker file for skipped version is there", Files.exists(updateLog.get(0).resolve(BundleUpdateLog.SKIPPED_MARKER_FILE)));
        assertThat("Bundle version in the skipped bundle is the newest", FileUtils.readFileToString(newest.resolve("bundle").resolve("bundle.yaml").toFile(), StandardCharsets.UTF_8), containsString("version: \"2\""));

        // Call the skip endpoint without a candidate in the instance
        result = new CLICommandInvoker(rule, BundleVersionSkipCommand.COMMAND_NAME).asUser("alice").invoke();
        assertThat("There is no bundle to skip", result.stderr().trim(), is("Bundle version to skip not found."));
    }

    // III.b
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/initial-bundle-version")
    public void automaticReload_enabled_endpoint() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setAutomaticReload(true);
        configuration.save();

        assertFalse("Skip All New Versions is not configured", configuration.isSkipNewVersions());
        assertTrue("There is an automatic reload", configuration.isAutomaticReload());
        assertFalse("There is no automatic restart", configuration.isAutomaticRestart());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From Version 1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/new-bundle-version").toFile().getAbsolutePath());
        CJPRule.WebClient wc = rule.createWebClient();
        WebResponse resp = requestWithToken(HttpMethod.GET, "casc-bundle-mgnt/check-bundle-update", wc);
        JSONObject response = JSONObject.fromObject(resp.getContentAsString());
        assertNotNull("The bundle cannot be skipped", response.get("update-type"));
        assertThat("The bundle cannot be skipped", response.get("update-type"), is(UpdateType.AUTOMATIC_RELOAD.label));
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        assertThat("Current Version is now v2", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertThat("Current Version is now v2", Jenkins.get().getSystemMessage(), is("From Version 2"));
        assertFalse("There is no candidate", ConfigurationStatus.INSTANCE.isUpdateAvailable());
        assertNull("There is no candidate", bundleManager.getCandidateAsConfigurationBundle());
        assertNull("There is no candidate", bundleManager.getUpdateLog().getCandidateBundle());

        BundleVisualizationLink link = BundleVisualizationLink.get();
        assertFalse("Bundle Update tab doesn't show the button bar", link.isUpdateAvailable());
        assertNull("Bundle Update tab doesn't the new version", link.getUpdateVersion());
        ConfigurationUpdaterMonitor monitor = ExtensionList.lookupSingleton(ConfigurationUpdaterMonitor.class);
        assertFalse("Monitor does not show up", monitor.isActivated());

        List<Path> updateLog = bundleManager.getUpdateLog().getHistoricalRecords();
        assertThat("Newest entry in the Update Log", updateLog, hasSize(2));
        Path newest = updateLog.get(0); // getHistoricalRecords return a SORTED list
        assertFalse("Marker file for skipped version is NOT there", Files.exists(updateLog.get(0).resolve(BundleUpdateLog.SKIPPED_MARKER_FILE)));
        assertThat("Bundle version in the newest record", FileUtils.readFileToString(newest.resolve("bundle").resolve("bundle.yaml").toFile(), StandardCharsets.UTF_8), containsString("version: \"2\""));

        // Call the skip endpoint without a candidate in the instance
        resp = requestWithToken(HttpMethod.POST, "casc-bundle-mgnt/casc-bundle-skip", wc);
        response = JSONObject.fromObject(resp.getContentAsString());
        assertThat("There is no bundle to skip", resp.getStatusCode(), is(404));
        assertNotNull("There is no bundle to skip, automatically reloaded", response.get("error"));
        assertThat("There is no bundle to skip, automatically reloaded", response.get("error"), is("Bundle version to skip not found."));
    }

    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/initial-bundle-version")
    public void skipNewVersions_enabled_endpoint() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setSkipNewVersions(true);
        configuration.save();

        assertTrue("Skip All New Versions is configured", configuration.isSkipNewVersions());
        assertFalse("There is no automatic reload/restart", configuration.isAutomaticReload());
        assertFalse("There is no automatic reload/restart", configuration.isAutomaticRestart());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From Version 1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/new-bundle-version").toFile().getAbsolutePath());
        CJPRule.WebClient wc = rule.createWebClient();
        WebResponse resp = requestWithToken(HttpMethod.GET, "casc-bundle-mgnt/check-bundle-update", wc);
        JSONObject response = JSONObject.fromObject(resp.getContentAsString());
        assertNotNull("The bundle cannot be skipped", response.get("update-type"));
        assertThat("The bundle cannot be skipped", response.get("update-type"), is(UpdateType.SKIPPED.label));
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        assertThat("Current Version is still v1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Current Version is still v1", Jenkins.get().getSystemMessage(), is("From Version 1"));
        assertFalse("There is a candidate but the button bar is not displayed", ConfigurationStatus.INSTANCE.isUpdateAvailable());
        assertThat("There is a candidate but the button bar is not displayed", bundleManager.getCandidateAsConfigurationBundle().getVersion(), is("2"));

        BundleVisualizationLink link = BundleVisualizationLink.get();
        assertFalse("Bundle Update tab doesn't show the button bar", link.isUpdateAvailable());
        assertNull("Bundle Update tab doesn't the new version", link.getUpdateVersion());
        ConfigurationUpdaterMonitor monitor = ExtensionList.lookupSingleton(ConfigurationUpdaterMonitor.class);
        assertFalse("Monitor does not show up", monitor.isActivated());

        List<Path> updateLog = bundleManager.getUpdateLog().getHistoricalRecords();
        assertThat("Newest entry in the Update Log", updateLog, hasSize(2));
        Path newest = updateLog.get(0); // getHistoricalRecords return a SORTED list
        assertTrue("Marker file for skipped version is there", Files.exists(updateLog.get(0).resolve(BundleUpdateLog.SKIPPED_MARKER_FILE)));
        assertThat("Bundle version in the skipped bundle is the newest", FileUtils.readFileToString(newest.resolve("bundle").resolve("bundle.yaml").toFile(), StandardCharsets.UTF_8), containsString("version: \"2\""));

        // Call the skip endpoint without a candidate in the instance
        resp = requestWithToken(HttpMethod.POST, "casc-bundle-mgnt/casc-bundle-skip", wc);
        response = JSONObject.fromObject(resp.getContentAsString());
        assertThat("There is no bundle to skip", resp.getStatusCode(), is(404));
        assertNotNull("There is no bundle to skip, automatically skipped", response.get("error"));
        assertThat("There is no bundle to skip, automatically skipped", response.get("error"), is("Bundle version to skip not found."));
    }

    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/initial-bundle-version")
    public void automaticReload_enabled_cli() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setAutomaticReload(true);
        configuration.save();

        assertFalse("Skip All New Versions is not configured", configuration.isSkipNewVersions());
        assertTrue("There is an automatic reload", configuration.isAutomaticReload());
        assertFalse("There is no automatic restart", configuration.isAutomaticRestart());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From Version 1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/new-bundle-version").toFile().getAbsolutePath());
        CLICommandInvoker.Result result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser("alice").invoke();
        JSONObject response = JSONObject.fromObject(result.stdout());
        assertNotNull("The bundle cannot be skipped", response.get("update-type"));
        assertThat("The bundle cannot be skipped", response.get("update-type"), is(UpdateType.AUTOMATIC_RELOAD.label));
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        assertThat("Current Version is now v2", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertThat("Current Version is now v2", Jenkins.get().getSystemMessage(), is("From Version 2"));
        assertFalse("There is no candidate", ConfigurationStatus.INSTANCE.isUpdateAvailable());
        assertNull("There is no candidate", bundleManager.getCandidateAsConfigurationBundle());
        assertNull("There is no candidate", bundleManager.getUpdateLog().getCandidateBundle());

        BundleVisualizationLink link = BundleVisualizationLink.get();
        assertFalse("Bundle Update tab doesn't show the button bar", link.isUpdateAvailable());
        assertNull("Bundle Update tab doesn't the new version", link.getUpdateVersion());
        ConfigurationUpdaterMonitor monitor = ExtensionList.lookupSingleton(ConfigurationUpdaterMonitor.class);
        assertFalse("Monitor does not show up", monitor.isActivated());

        List<Path> updateLog = bundleManager.getUpdateLog().getHistoricalRecords();
        assertThat("Newest entry in the Update Log", updateLog, hasSize(2));
        Path newest = updateLog.get(0); // getHistoricalRecords return a SORTED list
        assertFalse("Marker file for skipped version is NOT there", Files.exists(updateLog.get(0).resolve(BundleUpdateLog.SKIPPED_MARKER_FILE)));
        assertThat("Bundle version in the newest record", FileUtils.readFileToString(newest.resolve("bundle").resolve("bundle.yaml").toFile(), StandardCharsets.UTF_8), containsString("version: \"2\""));

        // Call the skip cli without a candidate in the instance
        result = new CLICommandInvoker(rule, BundleVersionSkipCommand.COMMAND_NAME).asUser("alice").invoke();
        assertThat("There is no bundle to skip, automatically reloaded", result.stderr().trim(), is("Bundle version to skip not found."));
    }

    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/initial-bundle-version")
    public void skipNewVersions_enabled_cli() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setSkipNewVersions(true);
        configuration.save();

        assertTrue("Skip All New Versions is configured", configuration.isSkipNewVersions());
        assertFalse("There is no automatic reload/restart", configuration.isAutomaticReload());
        assertFalse("There is no automatic reload/restart", configuration.isAutomaticRestart());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From Version 1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/new-bundle-version").toFile().getAbsolutePath());
        CLICommandInvoker.Result result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser("alice").invoke();
        JSONObject response = JSONObject.fromObject(result.stdout());
        assertNotNull("The bundle cannot be skipped", response.get("update-type"));
        assertThat("The bundle cannot be skipped", response.get("update-type"), is(UpdateType.SKIPPED.label));
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        assertThat("Current Version is still v1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Current Version is still v1", Jenkins.get().getSystemMessage(), is("From Version 1"));
        assertFalse("There is a candidate but the button bar is not displayed", ConfigurationStatus.INSTANCE.isUpdateAvailable());
        assertThat("There is a candidate but the button bar is not displayed", bundleManager.getCandidateAsConfigurationBundle().getVersion(), is("2"));

        BundleVisualizationLink link = BundleVisualizationLink.get();
        assertFalse("Bundle Update tab doesn't show the button bar", link.isUpdateAvailable());
        assertNull("Bundle Update tab doesn't the new version", link.getUpdateVersion());
        ConfigurationUpdaterMonitor monitor = ExtensionList.lookupSingleton(ConfigurationUpdaterMonitor.class);
        assertFalse("Monitor does not show up", monitor.isActivated());

        List<Path> updateLog = bundleManager.getUpdateLog().getHistoricalRecords();
        assertThat("Newest entry in the Update Log", updateLog, hasSize(2));
        Path newest = updateLog.get(0); // getHistoricalRecords return a SORTED list
        assertTrue("Marker file for skipped version is there", Files.exists(updateLog.get(0).resolve(BundleUpdateLog.SKIPPED_MARKER_FILE)));
        assertThat("Bundle version in the skipped bundle is the newest", FileUtils.readFileToString(newest.resolve("bundle").resolve("bundle.yaml").toFile(), StandardCharsets.UTF_8), containsString("version: \"2\""));

        // Call the skip cli without a candidate in the instance
        result = new CLICommandInvoker(rule, BundleVersionSkipCommand.COMMAND_NAME).asUser("alice").invoke();
        assertThat("There is no bundle to skip, automatically skipped", result.stderr().trim(), is("Bundle version to skip not found."));
    }

    // III.c
    @Test
    @WithBundleUpdateTiming("false")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/initial-bundle-version")
    public void endpoint_disabled() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertFalse("Bundle Update Timing is disabled", configuration.isEnabled());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From Version 1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/new-bundle-version").toFile().getAbsolutePath());
        CJPRule.WebClient wc = rule.createWebClient();
        WebResponse resp = requestWithToken(HttpMethod.GET, "casc-bundle-mgnt/check-bundle-update", wc);
        JSONObject response = JSONObject.fromObject(resp.getContentAsString());
        assertNotNull("The bundle cannot be skipped", response.get("update-type"));
        assertThat("The bundle cannot be skipped", response.get("update-type"), is(UpdateType.RELOAD.label));

        assertThat("Current Version is still v1, but the bundle is promoted as Bundle Update Timing is disabled", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertThat("Current Version is still v1, but the bundle is promoted as Bundle Update Timing is disabled", Jenkins.get().getSystemMessage(), is("From Version 1"));
        assertTrue("There is a candidate", ConfigurationStatus.INSTANCE.isUpdateAvailable());
        assertNull("There is a candidate, but it's already promoted", bundleManager.getUpdateLog().getCandidateBundle());
        assertNull("There is a candidate, but it's already promoted", bundleManager.getCandidateAsConfigurationBundle());

        BundleVisualizationLink link = BundleVisualizationLink.get();
        assertTrue("Bundle Update tab shows the button bar", link.isUpdateAvailable());
        assertThat("Bundle Update tab shows the new version", link.getUpdateVersion(), is("2"));
        assertTrue("Bundle Update tab shows the Reload button", link.isHotReloadable());
        assertFalse("Bundle Update tab does not show the Skip button", link.canManualSkip());

        ConfigurationUpdaterMonitor monitor = ExtensionList.lookupSingleton(ConfigurationUpdaterMonitor.class);
        assertTrue("Monitor shows up", monitor.isActivated());
        assertTrue("Monitor shows the button bar", monitor.isUpdateAvailable());
        assertThat("Monitor shows the new version", monitor.getUpdateVersion(), is("2"));
        assertTrue("Monitor shows the Reload button", monitor.isHotReloadable());
        assertFalse("Monitor does not show the Skip button", monitor.canManualSkip());

        // Call the skip endpoint
        resp = requestWithToken(HttpMethod.POST, "casc-bundle-mgnt/casc-bundle-skip", wc);
        response = JSONObject.fromObject(resp.getContentAsString());
        assertThat("Bundle update timing disabled", resp.getStatusCode(), is(500));
        assertNotNull("Bundle update timing disabled", response.get("error"));
        assertThat("Bundle update timing disabled", response.get("error"), is("This instance does not allow to skip bundles. Please, enable Bundle Update Timing by setting the System property -Dcore.casc.bundle.update.timing.enabled=true"));
    }

    @Test
    @WithBundleUpdateTiming("false")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/initial-bundle-version")
    public void cli_disabled() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertFalse("Bundle Update Timing is disabled", configuration.isEnabled());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));
        assertThat("Initial version is 1", Jenkins.get().getSystemMessage(), is("From Version 1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/SkipSingleVersionTest/new-bundle-version").toFile().getAbsolutePath());
        CLICommandInvoker.Result result = new CLICommandInvoker(rule, BundleVersionCheckerCommand.COMMAND_NAME).asUser("alice").invoke();
        JSONObject response = JSONObject.fromObject(result.stdout());
        assertNotNull("The bundle cannot be skipped", response.get("update-type"));
        assertThat("The bundle cannot be skipped", response.get("update-type"), is(UpdateType.RELOAD.label));

        assertThat("Current Version is still v1, but the bundle is promoted as Bundle Update Timing is disabled", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertThat("Current Version is still v1, but the bundle is promoted as Bundle Update Timing is disabled", Jenkins.get().getSystemMessage(), is("From Version 1"));
        assertTrue("There is a candidate", ConfigurationStatus.INSTANCE.isUpdateAvailable());
        assertNull("There is a candidate, but it's already promoted", bundleManager.getUpdateLog().getCandidateBundle());
        assertNull("There is a candidate, but it's already promoted", bundleManager.getCandidateAsConfigurationBundle());

        BundleVisualizationLink link = BundleVisualizationLink.get();
        assertTrue("Bundle Update tab shows the button bar", link.isUpdateAvailable());
        assertThat("Bundle Update tab shows the new version", link.getUpdateVersion(), is("2"));
        assertTrue("Bundle Update tab shows the Reload button", link.isHotReloadable());
        assertFalse("Bundle Update tab does not show the Skip button", link.canManualSkip());

        ConfigurationUpdaterMonitor monitor = ExtensionList.lookupSingleton(ConfigurationUpdaterMonitor.class);
        assertTrue("Monitor shows up", monitor.isActivated());
        assertTrue("Monitor shows the button bar", monitor.isUpdateAvailable());
        assertThat("Monitor shows the new version", monitor.getUpdateVersion(), is("2"));
        assertTrue("Monitor shows the Reload button", monitor.isHotReloadable());
        assertFalse("Monitor does not show the Skip button", monitor.canManualSkip());

        // Call the skip cli
        result = new CLICommandInvoker(rule, BundleVersionSkipCommand.COMMAND_NAME).asUser("alice").invoke();
        assertThat("There is no bundle to skip, automatically skipped", result.stderr().trim(), is("This instance does not allow to skip bundles. Please, enable Bundle Update Timing by setting the System property -Dcore.casc.bundle.update.timing.enabled=true"));
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
