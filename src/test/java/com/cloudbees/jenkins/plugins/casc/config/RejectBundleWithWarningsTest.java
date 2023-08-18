package com.cloudbees.jenkins.plugins.casc.config;

import java.nio.file.Paths;

import org.junit.Test;

import com.cloudbees.jenkins.cjp.installmanager.AbstractCJPTest;
import com.cloudbees.jenkins.cjp.installmanager.WithBundleUpdateTiming;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.cjp.installmanager.casc.BundleUpdateTimingManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;
import com.cloudbees.opscenter.client.casc.ConfigurationUpdaterHelper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
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
        configuration.save();

        assertTrue("Reject Bundles with warnings is configured", configuration.isRejectWarnings());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/new-valid").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is accepted", bundleManager.getConfigurationBundle().getVersion(), is("2"));
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
        configuration.save();

        assertTrue("Reject Bundles with warnings is configured", configuration.isRejectWarnings());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/new-only-warnings").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is rejected", bundleManager.getConfigurationBundle().getVersion(), is("1"));
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
        configuration.save();

        assertTrue("Reject Bundles with warnings is configured", configuration.isRejectWarnings());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/new-warnings-errors").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is rejected", bundleManager.getConfigurationBundle().getVersion(), is("1"));
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
        configuration.save();

        assertTrue("Reject Bundles with warnings is configured", configuration.isRejectWarnings());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/new-only-errors").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is rejected", bundleManager.getConfigurationBundle().getVersion(), is("1"));
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
        configuration.save();

        assertFalse("Reject Bundles with warnings is NOT configured", configuration.isRejectWarnings());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/new-valid").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is accepted", bundleManager.getConfigurationBundle().getVersion(), is("2"));
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
        configuration.save();

        assertFalse("Reject Bundles with warnings is NOT configured", configuration.isRejectWarnings());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/new-only-warnings").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is accepted", bundleManager.getConfigurationBundle().getVersion(), is("2"));
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
        configuration.save();

        assertFalse("Reject Bundles with warnings is NOT configured", configuration.isRejectWarnings());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/new-warnings-errors").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is rejected", bundleManager.getConfigurationBundle().getVersion(), is("1"));
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
        configuration.save();

        assertFalse("Reject Bundles with warnings is NOT configured", configuration.isRejectWarnings());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/new-only-errors").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is rejected", bundleManager.getConfigurationBundle().getVersion(), is("1"));
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
        configuration.save();

        assertFalse("Reject Bundles with warnings won't be configured as Bundle Update Timing is disabled", configuration.isRejectWarnings());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/new-valid").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is accepted", bundleManager.getConfigurationBundle().getVersion(), is("2"));
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
        configuration.save();

        assertFalse("Reject Bundles with warnings won't be configured as Bundle Update Timing is disabled", configuration.isRejectWarnings());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/new-only-warnings").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is accepted", bundleManager.getConfigurationBundle().getVersion(), is("2"));
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
        configuration.save();

        assertFalse("Reject Bundles with warnings won't be configured as Bundle Update Timing is disabled", configuration.isRejectWarnings());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/new-warnings-errors").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is rejected", bundleManager.getConfigurationBundle().getVersion(), is("1"));
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
        configuration.save();

        assertFalse("Reject Bundles with warnings won't be configured as Bundle Update Timing is disabled", configuration.isRejectWarnings());

        ConfigurationBundleManager bundleManager = ConfigurationBundleManager.get();
        assertThat("Initial version is 1", bundleManager.getConfigurationBundle().getVersion(), is("1"));

        // Update version to 2
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/jenkins/plugins/casc/config/RejectBundleWithWarningsTest/new-only-errors").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();

        assertThat("New bundle is rejected", bundleManager.getConfigurationBundle().getVersion(), is("1"));
    }

}
