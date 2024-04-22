package com.cloudbees.jenkins.plugins.casc.config;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import org.junit.Before;
import org.junit.Test;

import com.cloudbees.jenkins.cjp.installmanager.AbstractCJPTest;
import com.cloudbees.jenkins.cjp.installmanager.WithBundleUpdateTiming;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;
import com.cloudbees.opscenter.client.casc.ConfigurationStatus;
import com.cloudbees.opscenter.client.casc.ConfigurationUpdaterHelper;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Test cases (when instance is up and running. See RejectBundleWithWarningsTest in cloudbees-installation-manager for when the instance is starting):
 * I. With bundle update timing enabled:
 *  a. Reject Warnings enabled:
 *   i. New bundle version without errors or warnings, so it's accepted
 *   ii. New bundle version with warnings but without errors, so it's rejected
 *   iii. New bundle version with errors and warnings, so it's rejected
 *   iV. New bundle version with errors but without warnings, so it's rejected
 *  b. Reject Warnings disabled:
 *   i. New bundle version without errors or warnings, so it's accepted
 *   ii. New bundle version with warnings but without errors, so it's accepted
 *   iii. New bundle version with errors and warnings, so it's rejected
 *   iV. New bundle version with errors but without warnings, so it's rejected
 * II. With bundle update timing disabled:
 *  a. New bundle version without errors or warnings, so it's accepted
 *  b. New bundle version with warnings but without errors, so it's accepted
 *  c. New bundle version with errors and warnings, so it's rejected
 *  d. New bundle version with errors but without warnings, so it's rejected
 */
public class RejectBundleWithWarningsTest extends AbstractCJPTest {

    private static void verifyCurrentUpdateStatus(
            String message,
            BundleUpdateLog.BundleUpdateLogAction action,
            BundleUpdateLog.BundleUpdateLogActionSource source,
            String fromBundle,
            String toBundle
    ) {
        BundleUpdateLog.BundleUpdateStatus current = BundleUpdateLog.BundleUpdateStatus.getCurrent();
        assertThat("BundleUpdateStatus should exists", current, notNullValue());
        assertThat(message, current.getAction(), is(action));
        assertThat(message, current.getSource(), is(source));
        assertThat("From bundle " + fromBundle, current.getFromBundleVersion(), is(fromBundle));
        assertThat("To bundle " + toBundle, current.getToBundleVersion(), is(toBundle));
        assertTrue("Action should be a success", current.isSuccess());
        assertNull("Action should be a success", current.getError());
        assertFalse("Skipped should be false", current.isSkipped());
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

    // I.a.i
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/initial-bundle-version")
    public void rejectWarningsAndEnabled_valid() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setRejectWarnings(true);
        configuration.setAutomaticReload(true);
        configuration.save();

        assertTrue("Reject Bundles with warnings is configured", configuration.isRejectWarnings());
        assertTrue("Let's force the bundle to be reloaded if valid", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/new-valid").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        // Just in case the async reload hasn't finished
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());

        assertThat("New bundle is accepted", bundleManager.getConfigurationBundle().getVersion(), is("2"));

        verifyCurrentUpdateStatus(
                "Automatic reload",
                BundleUpdateLog.BundleUpdateLogAction.RELOAD,
                BundleUpdateLog.BundleUpdateLogActionSource.AUTOMATIC,
                "1",
                "2"
        );
    }

    // I.a.ii
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/initial-bundle-version")
    public void rejectWarningsAndEnabled_only_warnings() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setRejectWarnings(true);
        configuration.setAutomaticReload(true);
        configuration.save();

        assertTrue("Reject Bundles with warnings is configured", configuration.isRejectWarnings());
        assertTrue("Let's force the bundle to be reloaded if valid", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/new-only-warnings").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is rejected", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        verifyCurrentUpdateStatus("No automatic reload",
                                  BundleUpdateLog.BundleUpdateLogAction.CREATE,
                                  BundleUpdateLog.BundleUpdateLogActionSource.INIT,
                                  "1",
                                  "2"
        );
    }

    // I.a.iii
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/initial-bundle-version")
    public void rejectWarningsAndEnabled_warnings_and_errors() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setRejectWarnings(true);
        configuration.setAutomaticReload(true);
        configuration.save();

        assertTrue("Reject Bundles with warnings is configured", configuration.isRejectWarnings());
        assertTrue("Let's force the bundle to be reloaded if valid", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/new-warnings-errors").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is rejected", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        verifyCurrentUpdateStatus("No automatic reload",
                                  BundleUpdateLog.BundleUpdateLogAction.CREATE,
                                  BundleUpdateLog.BundleUpdateLogActionSource.INIT,
                                  "1",
                                  "2"
        );
    }

    // I.a.iv
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/initial-bundle-version")
    public void rejectWarningsAndEnabled_only_errors() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setRejectWarnings(true);
        configuration.setAutomaticReload(true);
        configuration.save();

        assertTrue("Reject Bundles with warnings is configured", configuration.isRejectWarnings());
        assertTrue("Let's force the bundle to be reloaded if valid", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/new-only-errors").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is rejected", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        verifyCurrentUpdateStatus("No automatic reload",
                                  BundleUpdateLog.BundleUpdateLogAction.CREATE,
                                  BundleUpdateLog.BundleUpdateLogActionSource.INIT,
                                  "1",
                                  "2"
        );
    }

    // I.b.i
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/initial-bundle-version")
    public void dontRejectWarningsAndEnabled_valid() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setRejectWarnings(false);
        configuration.setAutomaticReload(true);
        configuration.save();

        assertFalse("Reject Bundles with warnings is NOT configured", configuration.isRejectWarnings());
        assertTrue("Let's force the bundle to be reloaded if valid", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/new-valid").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is accepted", bundleManager.getConfigurationBundle().getVersion(), is("2"));

        // Just in case the async reload hasn't finished
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());
        verifyCurrentUpdateStatus("Automatic reload",
                                  BundleUpdateLog.BundleUpdateLogAction.RELOAD,
                                  BundleUpdateLog.BundleUpdateLogActionSource.AUTOMATIC,
                                  "1",
                                  "2"
        );
    }

    // I.b.ii
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/initial-bundle-version")
    public void dontRejectWarningsAndEnabled_only_warnings() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setRejectWarnings(false);
        configuration.setAutomaticReload(true);
        configuration.save();

        assertFalse("Reject Bundles with warnings is NOT configured", configuration.isRejectWarnings());
        assertTrue("Let's force the bundle to be reloaded if valid", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/new-only-warnings").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is accepted", bundleManager.getConfigurationBundle().getVersion(), is("2"));

        // Just in case the async reload hasn't finished
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());
        verifyCurrentUpdateStatus("Automatic reload",
                                  BundleUpdateLog.BundleUpdateLogAction.RELOAD,
                                  BundleUpdateLog.BundleUpdateLogActionSource.AUTOMATIC,
                                  "1",
                                  "2"
        );
    }

    // I.b.iii
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/initial-bundle-version")
    public void dontRejectWarningsAndEnabled_warnings_and_errors() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setRejectWarnings(false);
        configuration.setAutomaticReload(true);
        configuration.save();

        assertFalse("Reject Bundles with warnings is NOT configured", configuration.isRejectWarnings());
        assertTrue("Let's force the bundle to be reloaded if valid", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/new-warnings-errors").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is rejected", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        verifyCurrentUpdateStatus("No automatic reload",
                                  BundleUpdateLog.BundleUpdateLogAction.CREATE,
                                  BundleUpdateLog.BundleUpdateLogActionSource.INIT,
                                  "1",
                                  "2"
        );
    }

    // I.b.iv
    @Test
    @WithBundleUpdateTiming("true")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/initial-bundle-version")
    public void dontRejectWarningsAndEnabled_only_errors() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setRejectWarnings(false);
        configuration.setAutomaticReload(true);
        configuration.save();

        assertFalse("Reject Bundles with warnings is NOT configured", configuration.isRejectWarnings());
        assertTrue("Let's force the bundle to be reloaded if valid", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/new-only-errors").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is rejected", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        verifyCurrentUpdateStatus("No automatic reload",
                                  BundleUpdateLog.BundleUpdateLogAction.CREATE,
                                  BundleUpdateLog.BundleUpdateLogActionSource.INIT,
                                  "1",
                                  "2"
        );
    }

    // II.a
    @Test
    @WithBundleUpdateTiming("false")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/initial-bundle-version")
    public void updateTimingDisabled_valid() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertFalse("Bundle Update Timing is disabled", configuration.isEnabled());

        configuration.setRejectWarnings(false);
        configuration.setAutomaticReload(true);
        configuration.save();

        assertFalse("Reject Bundles with warnings won't be configured as Bundle Update Timing is disabled", configuration.isRejectWarnings());
        assertFalse("Automatic reload won't be configured as Bundle Update Timing is disabled", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/new-valid").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is accepted", bundleManager.getConfigurationBundle().getVersion(), is("2"));

        verifyCurrentUpdateStatus("Accepted but no automatic reload",
                                  BundleUpdateLog.BundleUpdateLogAction.RESTART,
                                  BundleUpdateLog.BundleUpdateLogActionSource.MANUAL,
                                  "1",
                                  "2"
        );
    }

    // II.b
    @Test
    @WithBundleUpdateTiming("false")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/initial-bundle-version")
    public void updateTimingDisabled_only_warnings() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertFalse("Bundle Update Timing is disabled", configuration.isEnabled());

        configuration.setRejectWarnings(false);
        configuration.setAutomaticReload(true);
        configuration.save();

        assertFalse("Reject Bundles with warnings won't be configured as Bundle Update Timing is disabled", configuration.isRejectWarnings());
        assertFalse("Automatic reload won't be configured as Bundle Update Timing is disabled", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/new-only-warnings").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is accepted", bundleManager.getConfigurationBundle().getVersion(), is("2"));

        verifyCurrentUpdateStatus("Accepted but no automatic reload",
                                  BundleUpdateLog.BundleUpdateLogAction.RESTART,
                                  BundleUpdateLog.BundleUpdateLogActionSource.MANUAL,
                                  "1",
                                  "2"
        );
    }

    // II.c
    @Test
    @WithBundleUpdateTiming("false")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/initial-bundle-version")
    public void updateTimingDisabled_warnings_and_errors() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertFalse("Bundle Update Timing is disabled", configuration.isEnabled());

        configuration.setRejectWarnings(false);
        configuration.setAutomaticReload(true);
        configuration.save();

        assertFalse("Reject Bundles with warnings won't be configured as Bundle Update Timing is disabled", configuration.isRejectWarnings());
        assertFalse("Automatic reload won't be configured as Bundle Update Timing is disabled", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/new-warnings-errors").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is rejected", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        verifyCurrentUpdateStatus("No automatic reload",
                                  BundleUpdateLog.BundleUpdateLogAction.CREATE,
                                  BundleUpdateLog.BundleUpdateLogActionSource.INIT,
                                  "1",
                                  "2"
        );
    }

    // II.d
    @Test
    @WithBundleUpdateTiming("false")
    @WithEnvelope(TestEnvelopes.CoreCMTraditionalJCasC.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/initial-bundle-version")
    public void updateTimingDisabled_only_errors() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertFalse("Bundle Update Timing is disabled", configuration.isEnabled());

        configuration.setRejectWarnings(false);
        configuration.setAutomaticReload(true);
        configuration.save();

        assertFalse("Reject Bundles with warnings won't be configured as Bundle Update Timing is disabled", configuration.isRejectWarnings());
        assertFalse("Automatic reload won't be configured as Bundle Update Timing is disabled", configuration.isAutomaticReload());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/new-only-errors").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is rejected", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        verifyCurrentUpdateStatus("No automatic reload",
                                  BundleUpdateLog.BundleUpdateLogAction.CREATE,
                                  BundleUpdateLog.BundleUpdateLogActionSource.INIT,
                                  "1",
                                  "2"
        );
    }

}
