package com.cloudbees.jenkins.plugins.casc;

import org.junit.Rule;
import org.junit.Test;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.model.CNode;

import com.cloudbees.jenkins.cjp.installmanager.WithBundleUpdateTiming;
import com.cloudbees.jenkins.cjp.installmanager.casc.BundleUpdateTimingManager;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static io.jenkins.plugins.casc.misc.Util.getUnclassifiedRoot;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;

public class BundleUpdateTimingConfigurationCasCTest {

    @Rule
    public JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

    @Test
    @WithBundleUpdateTiming("true")
    @ConfiguredWithCode("com/cloudbees/jenkins/plugins/casc/config/BundleUpdateTimingConfigurationCasCTest/configuration-as-code.yaml")
    public void configure_when_enabled() {
        BundleUpdateTimingConfiguration conf = BundleUpdateTimingConfiguration.get();
        assertTrue("configuration-as-code.yaml is setting true", conf.isAutomaticRestart());
        assertTrue("configuration-as-code.yaml is setting true", conf.isAutomaticReload());
        assertTrue("configuration-as-code.yaml is setting true", conf.isRejectWarnings());
        assertTrue("configuration-as-code.yaml is setting true", conf.isSkipNewVersions());
        assertTrue("configuration-as-code.yaml is setting true", conf.isReloadAlwaysOnRestart());

        BundleUpdateTimingManager propertyFile = BundleUpdateTimingManager.get();
        assertTrue("configuration-as-code.yaml is setting true and it's persisted", propertyFile.isAutomaticRestart());
        assertTrue("configuration-as-code.yaml is setting true and it's persisted", propertyFile.isAutomaticReload());
        assertTrue("configuration-as-code.yaml is setting true and it's persisted", propertyFile.isRejectWarnings());
        assertTrue("configuration-as-code.yaml is setting true and it's persisted", propertyFile.isSkipNewVersions());
        assertTrue("configuration-as-code.yaml is setting true and it's persisted", propertyFile.isReloadAlwaysOnRestart());

    }

    @Test
    @WithBundleUpdateTiming("false")
    @ConfiguredWithCode("com/cloudbees/jenkins/plugins/casc/config/BundleUpdateTimingConfigurationCasCTest/configuration-as-code.yaml")
    public void configure_when_disabled() {
        BundleUpdateTimingConfiguration conf = BundleUpdateTimingConfiguration.get();
        assertFalse("Bundle Update Timing disabled, so ignoring that configuration-as-code.yaml is setting true", conf.isAutomaticRestart());
        assertFalse("Bundle Update Timing disabled, so ignoring that configuration-as-code.yaml is setting true", conf.isAutomaticReload());
        assertFalse("Bundle Update Timing disabled, so ignoring that configuration-as-code.yaml is setting true", conf.isRejectWarnings());
        assertFalse("Bundle Update Timing disabled, so ignoring that configuration-as-code.yaml is setting true", conf.isSkipNewVersions());
        assertFalse("Bundle Update Timing disabled, so ignoring that configuration-as-code.yaml is setting true", conf.isReloadAlwaysOnRestart());

        BundleUpdateTimingManager propertyFile = BundleUpdateTimingManager.get();
        assertFalse("Bundle Update Timing disabled, so ignoring that configuration-as-code.yaml is setting true and the value is not persisted", propertyFile.isAutomaticRestart());
        assertFalse("Bundle Update Timing disabled, so ignoring that configuration-as-code.yaml is setting true and the value is not persisted", propertyFile.isAutomaticReload());
        assertFalse("Bundle Update Timing disabled, so ignoring that configuration-as-code.yaml is setting true and the value is not persisted", propertyFile.isRejectWarnings());
        assertFalse("Bundle Update Timing disabled, so ignoring that configuration-as-code.yaml is setting true and the value is not persisted", propertyFile.isSkipNewVersions());
        assertFalse("Bundle Update Timing disabled, so ignoring that configuration-as-code.yaml is setting true and the value is not persisted", propertyFile.isReloadAlwaysOnRestart());
    }

    @Test
    @ConfiguredWithCode("com/cloudbees/jenkins/plugins/casc/config/BundleUpdateTimingConfigurationCasCTest/configuration-as-code.yaml")
    @WithBundleUpdateTiming("true")
    public void export_when_enabled() throws Exception {
        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        CNode yourAttribute = getUnclassifiedRoot(context).get("bundleUpdateTiming");

        String exported = toYamlString(yourAttribute);

        assertThat("configuration-as-code.yaml is setting true", exported, containsString("automaticReload: true"));
        assertThat("configuration-as-code.yaml is setting true", exported, containsString("automaticRestart: true"));
        assertThat("configuration-as-code.yaml is setting true", exported, containsString("rejectWarnings: true"));
        assertThat("configuration-as-code.yaml is setting true", exported, containsString("reloadAlwaysOnRestart: true"));
        assertThat("configuration-as-code.yaml is setting true", exported, containsString("skipNewVersions: true"));
    }

    @Test
    @ConfiguredWithCode("com/cloudbees/jenkins/plugins/casc/config/BundleUpdateTimingConfigurationCasCTest/configuration-as-code.yaml")
    @WithBundleUpdateTiming("false")
    public void export_when_disabled() throws Exception {
        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        CNode yourAttribute = getUnclassifiedRoot(context).get("bundleUpdateTiming");

        String exported = toYamlString(yourAttribute);


        assertThat("Bundle Update Timing disabled, so ignoring that configuration-as-code.yaml is setting true", exported, containsString("automaticReload: false"));
        assertThat("Bundle Update Timing disabled, so ignoring that configuration-as-code.yaml is setting true", exported, containsString("automaticRestart: false"));
        assertThat("Bundle Update Timing disabled, so ignoring that configuration-as-code.yaml is setting true", exported, containsString("rejectWarnings: false"));
        assertThat("Bundle Update Timing disabled, so ignoring that configuration-as-code.yaml is setting true", exported, containsString("reloadAlwaysOnRestart: false"));
        assertThat("Bundle Update Timing disabled, so ignoring that configuration-as-code.yaml is setting true", exported, containsString("skipNewVersions: false"));
    }
}