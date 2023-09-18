package com.cloudbees.jenkins.plugins.casc.config;

import com.cloudbees.jenkins.cjp.installmanager.AbstractCJPTest;
import com.cloudbees.jenkins.cjp.installmanager.WithBundleUpdateTiming;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;
import com.cloudbees.opscenter.client.casc.ConfigurationStatus;
import com.cloudbees.opscenter.client.casc.ConfigurationUpdaterHelper;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BundleUpdateStatusTest extends AbstractCJPTest {

    private static void verifyCurrentUpdateStatus(
            String message,
            BundleUpdateLog.BundleUpdateLogAction action,
            BundleUpdateLog.BundleUpdateLogActionSource source,
            String fromBundleVersion,
            String toBundleVersion,
            boolean skipped
    ) {
        BundleUpdateLog.BundleUpdateStatus current = BundleUpdateLog.BundleUpdateStatus.getCurrent();
        assertThat("BundleUpdateStatus should exists", current, notNullValue());
        assertThat(message, current.getAction(), is(action));
        assertThat(message, current.getSource(), is(source));
        assertThat("From bundle " + fromBundleVersion, current.getFromBundleVersion(), is(fromBundleVersion));
        assertThat("To bundle " + toBundleVersion, current.getToBundleVersion(), is(toBundleVersion));
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
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/BundleUpdateStatusTest/bundle-version-1")
    public void skipMultipleVersions() throws Exception {
        // Check initial configuration
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        configuration.setSkipNewVersions(true);
        configuration.setAutomaticReload(false);
        configuration.setAutomaticRestart(false);
        configuration.save();

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths
                .get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/BundleUpdateStatusTest/bundle-version-2").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        verifyCurrentUpdateStatus(
                "Skipped from 1 to 2",
                BundleUpdateLog.BundleUpdateLogAction.SKIP,
                BundleUpdateLog.BundleUpdateLogActionSource.AUTOMATIC,
                "1",
                "2",
                true
        );

        // Update version to 3
        System.setProperty("core.casc.config.bundle", Paths
                .get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/BundleUpdateStatusTest/bundle-version-3").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        verifyCurrentUpdateStatus(
                "Skipped from 1 to 3",
                BundleUpdateLog.BundleUpdateLogAction.SKIP,
                BundleUpdateLog.BundleUpdateLogActionSource.AUTOMATIC,
                "1",
                "3",
                true
        );

        configuration.setSkipNewVersions(false);
        configuration.setAutomaticReload(false);
        configuration.setAutomaticRestart(false);
        configuration.save();

        // Update version to 4
        System.setProperty("core.casc.config.bundle", Paths
                .get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/BundleUpdateStatusTest/bundle-version-4").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        verifyCurrentUpdateStatus(
                "Skipped from 1 to 4",
                BundleUpdateLog.BundleUpdateLogAction.CREATE,
                BundleUpdateLog.BundleUpdateLogActionSource.INIT,
                "1",
                "4",
                false
        );

        configuration.setSkipNewVersions(false);
        configuration.setAutomaticReload(true);
        configuration.setAutomaticRestart(false);
        configuration.save();

        // Update version to 5
        System.setProperty("core.casc.config.bundle", Paths
                .get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/BundleUpdateStatusTest/bundle-version-5").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        // Just in case the async reload hasn't finished
        await().atMost(30, TimeUnit.SECONDS).until(() -> !ConfigurationStatus.INSTANCE.isCurrentlyReloading());
        verifyCurrentUpdateStatus(
                "Auto reload from 1 to 5",
                BundleUpdateLog.BundleUpdateLogAction.RELOAD,
                BundleUpdateLog.BundleUpdateLogActionSource.AUTOMATIC,
                "1",
                "5",
                false
        );
    }

}
