package com.cloudbees.opscenter.client.casc.config;

import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.model.CNode;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;


import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static io.jenkins.plugins.casc.misc.Util.getUnclassifiedRoot;
import static io.jenkins.plugins.casc.misc.Util.toStringFromYamlFile;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;

public class BundleValidationVisualizationConfigurationCasCTest {
    @ClassRule
    @ConfiguredWithCode("configuration-as-code.yml")
    public static JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

    @Test
    public void should_support_configuration_as_code() {
        Assert.assertTrue(ConfigurationBundleManager.get().isQuiet());
    }

    @Test
    public void should_support_configuration_export() throws Exception {

        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        CNode yourAttribute = getUnclassifiedRoot(context).get("validationVisualization");

        String exported = toYamlString(yourAttribute);

        String expected = toStringFromYamlFile(this, "expected_output.yaml");

        assertThat(exported, is(expected));
    }

}