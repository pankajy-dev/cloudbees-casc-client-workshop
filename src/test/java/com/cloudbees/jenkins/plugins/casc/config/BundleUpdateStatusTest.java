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
    @WithConfigBundle("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/BundleUpdateStatusTest/initial-bundle-version")
    public void skipMultipleVersions() throws Exception {
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
        System.setProperty("core.casc.config.bundle", Paths
                .get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/BundleUpdateStatusTest/second-bundle-version").toFile().getAbsolutePath());
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
                .get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/BundleUpdateStatusTest/third-bundle-version").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        verifyCurrentUpdateStatus(
                "Skipped from 1 to 3",
                BundleUpdateLog.BundleUpdateLogAction.SKIP,
                BundleUpdateLog.BundleUpdateLogActionSource.AUTOMATIC,
                "1",
                "3",
                true
        );
    }

}
