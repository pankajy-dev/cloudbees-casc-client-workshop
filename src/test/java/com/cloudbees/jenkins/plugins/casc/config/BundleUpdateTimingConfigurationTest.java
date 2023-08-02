package com.cloudbees.jenkins.plugins.casc.config;

import java.util.logging.Level;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import com.cloudbees.jenkins.cjp.installmanager.WithBundleUpdateTiming;
import com.cloudbees.jenkins.cjp.installmanager.casc.BundleUpdateTimingManager;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BundleUpdateTimingConfigurationTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();
    @Rule
    public LoggerRule logger = new LoggerRule().record(BundleUpdateTimingConfiguration.class, Level.FINE);

    @Test
    @WithBundleUpdateTiming("true")
    public void smokes_when_enabled() {
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();

        assertTrue("Bundle Update Timing is enabled", configuration.isEnabled());

        // Check default values
        BundleUpdateTimingManager propertiesFile = BundleUpdateTimingManager.get();
        assertFalse("Default value is false", propertiesFile.isAutomaticReload());
        assertFalse("Default value is false", propertiesFile.isAutomaticRestart());
        assertFalse("Default value is false", propertiesFile.isSkipNewVersions());
        assertFalse("Default value is false", propertiesFile.isRejectWarnings());
        assertFalse("Default value is false", propertiesFile.isReloadAlwaysOnRestart());

        // first update
        logger.capture(5);
        configuration.setAutomaticReload(true);
        configuration.setAutomaticRestart(true);
        configuration.setRejectWarnings(true);
        configuration.setSkipNewVersions(true);
        configuration.setReloadAlwaysOnRestart(true);
        configuration.save();

        assertThat("It's saved", logger, LoggerRule.recorded(Level.FINE, containsString("Bundle Update Timing enabled. Saving")));
        propertiesFile = BundleUpdateTimingManager.get();
        assertTrue("Value is persisted", propertiesFile.isAutomaticReload());
        assertTrue("Value is persisted", propertiesFile.isAutomaticRestart());
        assertTrue("Value is persisted", propertiesFile.isSkipNewVersions());
        assertTrue("Value is persisted", propertiesFile.isRejectWarnings());
        assertTrue("Value is persisted", propertiesFile.isReloadAlwaysOnRestart());

        // Partial update
        logger.capture(5);
        configuration.setRejectWarnings(false);
        configuration.setSkipNewVersions(false);
        configuration.save();

        assertThat("It's saved", logger, LoggerRule.recorded(Level.FINE, containsString("Bundle Update Timing enabled. Saving")));
        propertiesFile = BundleUpdateTimingManager.get();
        assertTrue("Value remains", propertiesFile.isAutomaticReload());
        assertTrue("Value remains", propertiesFile.isAutomaticRestart());
        assertFalse("Value is persisted", propertiesFile.isSkipNewVersions());
        assertFalse("Value is persisted", propertiesFile.isRejectWarnings());
        assertTrue("Value remains", propertiesFile.isReloadAlwaysOnRestart());
    }

    @Test
    @WithBundleUpdateTiming("false")
    public void smokes_when_disabled() {
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();

        assertFalse("Bundle Update Timing is disabled", configuration.isEnabled());

        // Check default values
        BundleUpdateTimingManager propertiesFile = BundleUpdateTimingManager.get();
        assertFalse("Default value is false", propertiesFile.isAutomaticReload());
        assertFalse("Default value is false", propertiesFile.isAutomaticRestart());
        assertFalse("Default value is false", propertiesFile.isSkipNewVersions());
        assertFalse("Default value is false", propertiesFile.isRejectWarnings());
        assertFalse("Default value is false", propertiesFile.isReloadAlwaysOnRestart());

        // update
        logger.capture(5);
        configuration.setAutomaticReload(true);
        configuration.setAutomaticRestart(true);
        configuration.setRejectWarnings(true);
        configuration.setSkipNewVersions(true);
        configuration.setReloadAlwaysOnRestart(true);
        configuration.save();

        assertThat("It isn't saved", logger, LoggerRule.recorded(Level.FINE, containsString("Saving when Bundle Update Timing is disabled. Ignoring request")));
        propertiesFile = BundleUpdateTimingManager.get();
        assertFalse("Value is not persisted", propertiesFile.isAutomaticReload());
        assertFalse("Value is not persisted", propertiesFile.isAutomaticRestart());
        assertFalse("Value is not persisted", propertiesFile.isSkipNewVersions());
        assertFalse("Value is not persisted", propertiesFile.isRejectWarnings());
        assertFalse("Value is not persisted", propertiesFile.isReloadAlwaysOnRestart());

        assertEquals("Configuration object is reconciled", propertiesFile.isAutomaticReload(), configuration.isAutomaticReload());
        assertEquals("Configuration object is reconciled", propertiesFile.isAutomaticRestart(), configuration.isAutomaticRestart());
        assertEquals("Configuration object is reconciled", propertiesFile.isSkipNewVersions(), configuration.isSkipNewVersions());
        assertEquals("Configuration object is reconciled", propertiesFile.isRejectWarnings(), configuration.isRejectWarnings());
        assertEquals("Configuration object is reconciled", propertiesFile.isReloadAlwaysOnRestart(), configuration.isReloadAlwaysOnRestart());
    }
}