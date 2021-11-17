package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;
import hudson.ExtensionList;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class BundleExportInControllerTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();
    @Rule
    public CJPRule j = new CJPRule(tmp);

    @Before
    public void setUp() {
        ExtensionList.lookupSingleton(PluginCatalogExporter.class).setProductId("core-cm");
    }

    @Test
    @WithEnvelope(TestEnvelopes.OnePlugin.class)
    @Issue({"BEE-7093", "BEE-6931"})
    public void exportBundle() {
        BundleExport bundleExport = ExtensionList.lookupSingleton(BundleExport.class);
        BundleResponse resp = bundleExport.getBundleResponse();
        Assert.assertTrue("icon-shim is in the plugins list and not in the extension",
                resp.getPlugins().contains("icon-shim") && !resp.getPluginCatalog().contains("icon-shim"));
        assertNotNull(resp.getPluginCatalog());
        assertThat(resp.getDescriptor(), containsString("jenkins.yaml"));
        assertThat(resp.getDescriptor(), containsString("plugin-catalog.yaml"));
        assertThat(resp.getDescriptor(), containsString("rbac.yaml"));
        assertThat(resp.getDescriptor(), containsString("plugins.yaml"));
        assertThat(resp.getDescriptor(), containsString("items.yaml"));
    }

    @Test
    @WithEnvelope(TestEnvelopes.OnePlugin.class)
    @Issue({"BEE-6171"})
    public void sortedPluginsExport() {
        BundleExport bundleExport = ExtensionList.lookupSingleton(BundleExport.class);
        BundleResponse resp = bundleExport.getBundleResponse();

        Map<String, Object> catalog = toYaml(resp.getPluginCatalog());
        Map<String, Object> plugins = toYaml(resp.getPlugins());

        List<String> catalogPlugins = ((Map<String,Object>)((List<Map<String, Object>>)catalog.get("configurations")).get(0).get("includePlugins")).keySet().stream().collect(Collectors.toList());
        List<String> pluginsPlugins = ((List<Map<String,String>>)plugins.get("plugins")).stream().map(p -> p.values().stream().findFirst().get()).collect(Collectors.toList());

        List<String> sortedCatalog = new ArrayList<>(catalogPlugins);
        sortedCatalog.sort(String::compareTo);

        MatcherAssert.assertThat(catalogPlugins, contains(sortedCatalog.toArray()));

        List<String> sortedPlugins = new ArrayList<>(pluginsPlugins);
        sortedPlugins.sort(String::compareTo);

        MatcherAssert.assertThat(pluginsPlugins, contains(sortedPlugins.toArray()));
    }

    @Test
    @Issue({"BEE-9578"})
    @WithEnvelope(TestEnvelopes.OnePlugin.class)
    public void exportPlugins() {
        BundleExporter.PluginsExporter pluginsExporter = ExtensionList.lookupSingleton(BundleExporter.PluginsExporter.class);
        String yaml = pluginsExporter.getExport();

        MatcherAssert.assertThat(yaml, notNullValue());
        MatcherAssert.assertThat(yaml, not(containsString("{")));
        MatcherAssert.assertThat(yaml, not(containsString("}")));
        Map<String, Object> obj = toYaml(yaml);

        MatcherAssert.assertThat(yaml, notNullValue());
    }

    @Test
    @Issue({"BEE-9580"})
    @WithEnvelope(TestEnvelopes.OnePlugin.class)
    public void exportPluginCatalog() {
        PluginCatalogExporter pluginCatalogExporter = ExtensionList.lookupSingleton(PluginCatalogExporter.class);
        String yaml = pluginCatalogExporter.getExport();

        MatcherAssert.assertThat(yaml, notNullValue());
        MatcherAssert.assertThat(yaml, not(containsString("{")));
        MatcherAssert.assertThat(yaml, not(containsString("}")));
        Map<String, Object> obj = toYaml(yaml);

        MatcherAssert.assertThat(obj, notNullValue());
    }

    private Map<String, Object> toYaml(String str) {
        Map<String, Object> yaml = new Yaml(new SafeConstructor()).load(str);
        return yaml;
    }
}
