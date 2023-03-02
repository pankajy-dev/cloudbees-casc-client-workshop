package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.AbstractIMTest;
import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.plugins.casc.YamlClientUtils;
import com.cloudbees.jenkins.plugins.assurance.remote.EnvelopeExtension;
import com.cloudbees.jenkins.plugins.casc.CasCException;
import com.cloudbees.jenkins.plugins.updates.envelope.Envelope;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopeProvider;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import net.sf.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public class PluginCatalogTest extends AbstractIMTest {

    @Rule
    public final CJPRule rule;

    public PluginCatalogTest() {
        this.rule = new CJPRule(this.tmp);
    }

    @Override
    protected CJPRule rule() {
        return this.rule;
    }

    @AfterClass
    public static void after() {
        System.clearProperty("casc.jenkins.config");
    }

    @Test(expected = CasCException.class)
    @Issue("BEE-2942")
    @WithEnvelope(TwoPluginsV2dot289.class) //We need a fairly recent version
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/casc/PluginCatalogTest/bundle")
    public void emptyIncludePluginsTest() throws IOException, CasCException {
        Assert.assertTrue(ConfigurationBundleManager.isSet());

        ConfigurationBundle bundle = loadBundleWithCatalog("src/test/resources/com/cloudbees/opscenter/client/casc/PluginCatalogTest/bundle-wrong/plugin-catalog.yaml");

        ConfigurationBundleService service = ExtensionList.lookupSingleton(ConfigurationBundleService.class);
        Assert.assertTrue(service.isHotReloadable(bundle));
        service.reloadIfIsHotReloadable(bundle);
    }

    private ConfigurationBundle loadBundleWithCatalog(String path) throws IOException {
        Path file = new File(path).toPath();

        try (InputStream inputStream = Files.newInputStream(file)) {
            Yaml yaml = YamlClientUtils.createDefault();
            Map<String, Object> extensionYaml = (Map<String, Object>) yaml.load(inputStream);
            String name = (String) extensionYaml.get("name");
            String displayName = (String) extensionYaml.get("displayName");
            JSONObject json = JSONObject.fromObject(extensionYaml);

            return ConfigurationBundle.builder()
                    .setVersion("new")
                    .setCatalog(new EnvelopeExtension(name, displayName, json.toString()))
                    .setPlugins(Collections.EMPTY_SET)
                    .build();
        }

    }

    public static final class TwoPluginsV2dot289 implements TestEnvelopeProvider {
        public TwoPluginsV2dot289() {
        }

        @NonNull
        public Envelope call() {
            return TestEnvelopes.e("2.289.1",1,"",TestEnvelopes.beer12(), TestEnvelopes.p("manage-permission", "1.0.1"));
        }
    }
}
