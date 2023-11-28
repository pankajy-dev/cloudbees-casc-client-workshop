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
import com.github.tomakehurst.wiremock.common.ClasspathFileSource;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

public class PluginCatalogTest extends AbstractIMTest {

    @ClassRule
    public static WireMockClassRule wiremock = new WireMockClassRule(wireMockConfig().dynamicPort().fileSource(new ClasspathFileSource("src/test/resources/wiremock/")));
    @ClassRule
    public static TemporaryFolder bundlesSrc = new TemporaryFolder();

    @BeforeClass
    public static void processBundles() throws Exception {
        wiremock.stubFor(get(urlEqualTo("/beer-1.2.hpi")).willReturn(aResponse().withStatus(200).withBodyFile("beer-1.2.hpi")));
        wiremock.stubFor(get(urlEqualTo("/manage-permission-1.0.1.hpi")).willReturn(aResponse().withStatus(200).withBodyFile("manage-permission-1.0.1.hpi")));

        FileUtils.copyDirectory(Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/PluginCatalogTest").toFile(), bundlesSrc.getRoot());
        // Sanitise plugin-catalog.yaml
        Path pcFile1 = bundlesSrc.getRoot().toPath().resolve("bundle").resolve("plugin-catalog.yaml");
        String content;
        try (InputStream in = FileUtils.openInputStream(pcFile1.toFile())) {
            content = IOUtils.toString(in, StandardCharsets.UTF_8);
        }
        try (OutputStream out = FileUtils.openOutputStream(pcFile1.toFile(), false)) {
            IOUtils.write(content.replaceAll("%%URL%%", wiremock.baseUrl()), out, StandardCharsets.UTF_8);
        }

    }

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
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/casc/PluginCatalogTest/initial-bundle")
    public void emptyIncludePluginsTest() throws Exception {
        // This is a dirty hack to be able to manipulate the plugin catalog as in the BeforeClass method:
        // - WithConfigBundle needs a static resource, so we cannot use the "hacked" bundle that is in the TemporaryFolder rule
        // - So we initialize the CJPRule using a dummy bundle
        // - First thing we do is to apply the real bundle we need for this test (We need plugin catalog to force the bundle as no hot-reloadable)
        //      - Change the System property to point to the real bundle.
        //      - Force the check of the new version
        //      - Force the reload of the bundle
        //      - As the reload is done in a background thread, we wait until it's finished.
        System.setProperty("core.casc.config.bundle", Paths.get(bundlesSrc.getRoot().getAbsolutePath() + "/bundle").toFile().getAbsolutePath());
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
