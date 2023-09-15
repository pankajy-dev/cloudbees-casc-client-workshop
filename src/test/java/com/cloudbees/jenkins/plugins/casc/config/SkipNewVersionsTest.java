package com.cloudbees.jenkins.plugins.casc.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import hudson.ExtensionList;

import com.cloudbees.jenkins.cjp.installmanager.AbstractCJPTest;
import com.cloudbees.jenkins.cjp.installmanager.WithBundleUpdateTiming;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;
import com.cloudbees.opscenter.client.casc.BundleReloadAction;
import com.cloudbees.opscenter.client.casc.ConfigurationStatus;
import com.cloudbees.opscenter.client.casc.ConfigurationUpdaterHelper;
import com.cloudbees.opscenter.client.casc.visualization.BundleVisualizationLink;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertFalse;
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
 */
public class SkipNewVersionsTest extends AbstractCJPTest {

    private static void verifyCurrentUpdateStatus(
            String message,
            BundleUpdateLog.BundleUpdateLogAction action,
            BundleUpdateLog.BundleUpdateLogActionSource source,
            boolean skipped
    ) {
        BundleUpdateLog.BundleUpdateStatus current = BundleUpdateLog.BundleUpdateStatus.getCurrent();
        assertThat("BundleUpdateStatus should exists", current, notNullValue());
        assertThat(message, current.getAction(), is(action));
        assertThat(message, current.getSource(), is(source));
        assertThat("From bundle 1", current.getFromBundleVersion(), is("1"));
        assertThat("To bundle 2", current.getToBundleVersion(), is("2"));
        assertTrue("Action should be a success", current.isSuccess());
        assertNull("Action should be a success", current.getError());
        assertThat("Skipped should be " + skipped, current.isSkipped(), is(skipped));
        assertFalse("Action is finished", current.isOngoingAction());
    }

    @Before
    public void setUp() {
        // This is a dirty hack because the instance (it's an ENUM) is not reset between tests
        ConfigurationStatus.INSTANCE.setUpdateAvailable(false);
        ConfigurationStatus.INSTANCE.setOutdatedVersion(null);
        ConfigurationStatus.INSTANCE.setOutdatedBundleInformation(null);
        ConfigurationBundleManager.reset();
    }


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

        verifyCurrentUpdateStatus("Skip new version",
                                  BundleUpdateLog.BundleUpdateLogAction.SKIP,
                                  BundleUpdateLog.BundleUpdateLogActionSource.AUTOMATIC,
                                  true
        );
    }

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

        verifyCurrentUpdateStatus("Bundle is promoted, bundle update timing is disabled, bundle will be applied with the next restart",
                                  BundleUpdateLog.BundleUpdateLogAction.RESTART,
                                  BundleUpdateLog.BundleUpdateLogActionSource.MANUAL,
                                  false
        );
    }

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

        // Just in case the async reload hasn't finished
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());
        verifyCurrentUpdateStatus("Not skipped, reloaded",
                                  BundleUpdateLog.BundleUpdateLogAction.RELOAD,
                                  BundleUpdateLog.BundleUpdateLogActionSource.AUTOMATIC,
                                  false
        );
    }

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

        verifyCurrentUpdateStatus("Bundle is promoted, bundle update timing is disabled, bundle will be applied with the next restart",
                                  BundleUpdateLog.BundleUpdateLogAction.RESTART,
                                  BundleUpdateLog.BundleUpdateLogActionSource.MANUAL,
                                  false
        );
    }

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

        verifyCurrentUpdateStatus("Not skipped, not promoted",
                                  BundleUpdateLog.BundleUpdateLogAction.CREATE,
                                  BundleUpdateLog.BundleUpdateLogActionSource.INIT,
                                  false
        );

        // Let's reload as if the user had clicked the button, as it's not skipped
        assertTrue("This version 2 is Hot Reloadable", bundleManager.getCandidateAsConfigurationBundle().isHotReloadable());
        ConfigurationUpdaterHelper.promoteCandidate();
        ExtensionList.lookupSingleton(BundleReloadAction.class).executeReload(false);
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        assertThat("New bundle is now loaded", bundleManager.getConfigurationBundle().getVersion(), is("2"));
        assertNull("New bundle is now loaded", bundleUpdateTab.getUpdateVersion());
        assertThat("New bundle is now loaded", bundleUpdateTab.getBundleVersion(), is("2"));

        // Unknown because the Java API was called directly
        verifyCurrentUpdateStatus("Reloaded",
                                  BundleUpdateLog.BundleUpdateLogAction.RELOAD,
                                  BundleUpdateLog.BundleUpdateLogActionSource.UNKNOWN,
                                  false
        );
    }

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

        verifyCurrentUpdateStatus("Bundle is promoted, bundle update timing is disabled, bundle will be applied with the next restart",
                                  BundleUpdateLog.BundleUpdateLogAction.RESTART,
                                  BundleUpdateLog.BundleUpdateLogActionSource.MANUAL,
                                  false
        );
    }

}
