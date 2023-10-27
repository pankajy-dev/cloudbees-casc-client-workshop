package com.cloudbees.jenkins.plugins.casc.config;

import java.util.logging.Level;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;

import hudson.ExtensionList;
import hudson.XmlFile;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;

import com.cloudbees.jenkins.cjp.installmanager.WithBundleUpdateTiming;
import com.cloudbees.jenkins.cjp.installmanager.casc.BundleUpdateTimingManager;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
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
        assertThat("Listener was not triggered", ExtensionList.lookupSingleton(TestListener.class).getTriggeredAndReset(), is(false));
        configuration.save();

        assertThat("It's saved", logger, LoggerRule.recorded(Level.FINE, containsString("Bundle Update Timing enabled. Saving")));
        assertThat("Listener was triggered", ExtensionList.lookupSingleton(TestListener.class).getTriggeredAndReset(), is(true));
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
        assertThat("Listener was not triggered", ExtensionList.lookupSingleton(TestListener.class).getTriggeredAndReset(), is(false));
        configuration.save();

        assertThat("It's saved", logger, LoggerRule.recorded(Level.FINE, containsString("Bundle Update Timing enabled. Saving")));
        assertThat("Listener was triggered", ExtensionList.lookupSingleton(TestListener.class).getTriggeredAndReset(), is(true));
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
        assertThat("Listener was not triggered", ExtensionList.lookupSingleton(TestListener.class).getTriggeredAndReset(), is(false));
        configuration.save();

        assertThat("It isn't saved", logger, LoggerRule.recorded(Level.FINE, containsString("Saving when Bundle Update Timing is disabled. Ignoring request")));
        assertThat("Listener was not triggered", ExtensionList.lookupSingleton(TestListener.class).getTriggeredAndReset(), is(false));
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
    
    @TestExtension
    public static class TestListener extends SaveableListener {
        private boolean triggered;

        @Override
        public void onChange(Saveable o, XmlFile file) {
            if (o instanceof BundleUpdateTimingConfiguration) {
                triggered = true;
            }
        }

        public boolean getTriggeredAndReset() {
            boolean _ret = triggered;
            triggered = false;
            return _ret;
        }
    }
}