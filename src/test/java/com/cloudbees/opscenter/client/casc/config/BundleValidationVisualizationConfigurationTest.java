package com.cloudbees.opscenter.client.casc.config;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import edu.umd.cs.findbugs.annotations.NonNull;

import com.cloudbees.jenkins.cjp.installmanager.AbstractIMTest;
import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.plugins.updates.envelope.Envelope;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopeProvider;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;

public class BundleValidationVisualizationConfigurationTest extends AbstractIMTest {
    @Rule
    public final CJPRule j;

    public BundleValidationVisualizationConfigurationTest() {
        this.j = new CJPRule(this.tmp);
    }

    @Override
    protected CJPRule rule() {
        return this.j;
    }

    @WithEnvelope(V2dot289.class) //We need a fairly recent version
    @Test
    public void testDefaultQuietValue() {
        BundleValidationVisualizationConfiguration visualizationConfiguration;
        visualizationConfiguration = j.jenkins.getExtensionList(BundleValidationVisualizationConfiguration.class).get(0);
        ConfigurationBundleManager configurationBundleManager = ConfigurationBundleManager.get();
        Assert.assertFalse("Quiet mode should be disabled by default", configurationBundleManager.isQuiet());
        Assert.assertFalse("Quiet mode should be disabled by default", visualizationConfiguration.isQuiet());
    }

    @WithEnvelope(V2dot289.class) //We need a fairly recent version
    @Test
    public void testQuietValueSynchronization() {
        BundleValidationVisualizationConfiguration visualizationConfiguration;
        visualizationConfiguration = j.jenkins.getExtensionList(BundleValidationVisualizationConfiguration.class).get(0);
        ConfigurationBundleManager configurationBundleManager = ConfigurationBundleManager.get();

        visualizationConfiguration.setQuiet(false);
        Assert.assertEquals("ConfigurationBundleManager is out of sync",
                            visualizationConfiguration.isQuiet(),
                            configurationBundleManager.isQuiet());

        visualizationConfiguration.setQuiet(true);
        Assert.assertEquals("ConfigurationBundleManager is out of sync",
                            visualizationConfiguration.isQuiet(),
                            configurationBundleManager.isQuiet());

        visualizationConfiguration.setQuiet(false);
        Assert.assertEquals("ConfigurationBundleManager is out of sync",
                            visualizationConfiguration.isQuiet(),
                            configurationBundleManager.isQuiet());
    }

    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/casc/config/BundleValidationVisualizationConfigurationTest/quiet-bundle")
    @WithEnvelope(V2dot289.class) //We need a fairly recent version
    @Test
    public void testGetQuietValueFromBundle() {
        BundleValidationVisualizationConfiguration visualizationConfiguration;
        visualizationConfiguration = j.jenkins.getExtensionList(BundleValidationVisualizationConfiguration.class).get(0);
        ConfigurationBundleManager configurationBundleManager = ConfigurationBundleManager.get();
        Assert.assertTrue("Quiet mode should be enabled by the bundle", configurationBundleManager.isQuiet());
        Assert.assertTrue("Quiet mode should be enabled by the bundle", visualizationConfiguration.isQuiet());
    }

    public static final class V2dot289 implements TestEnvelopeProvider {
        @NonNull
        public Envelope call() {
            return TestEnvelopes.e("2.289.1", 1, "");
        }
    }
}