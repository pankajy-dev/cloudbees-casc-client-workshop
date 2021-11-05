package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.CJPRule;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes;
import hudson.ExtensionList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

public class BundleExportInOCTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();
    @Rule
    public CJPRule j = new CJPRule(tmp);

    @Before
    public void setUp() {
        ExtensionList.lookupSingleton(PluginCatalogExporter.class).setProductId("core-oc-traditional");
    }

    @Test
    @Issue({"BEE-7093", "BEE-6931"})
    @WithEnvelope(TestEnvelopes.OnePlugin.class)
    public void exportBundleWithoutProperty() {
        ExtensionList.lookupSingleton(PluginCatalogExporter.class).setIsPluginCatalogEnabledInOC(false);

        BundleExport bundleExport = ExtensionList.lookupSingleton(BundleExport.class);
        BundleResponse resp = bundleExport.getBundleResponse();
        assertNull(resp.getPluginCatalog());
        assertThat(resp.getDescriptor(), containsString("jenkins.yaml"));
        assertThat(resp.getDescriptor(), not(containsString("plugin-catalog.yaml")));
        assertThat(resp.getDescriptor(), containsString("rbac.yaml"));
        assertThat(resp.getDescriptor(), containsString("plugins.yaml"));
        assertThat(resp.getDescriptor(), containsString("items.yaml"));
    }


    @Test
    @Issue({"BEE-7093", "BEE-6931"})
    @WithEnvelope(TestEnvelopes.OnePlugin.class)
    public void exportBundleWithProperty() {
        ExtensionList.lookupSingleton(PluginCatalogExporter.class).setIsPluginCatalogEnabledInOC(true);

        BundleExport bundleExport = ExtensionList.lookupSingleton(BundleExport.class);
        BundleResponse resp = bundleExport.getBundleResponse();
        assertNotNull(resp.getPluginCatalog());
        assertThat(resp.getDescriptor(), containsString("jenkins.yaml"));
        assertThat(resp.getDescriptor(), containsString("plugin-catalog.yaml"));
        assertThat(resp.getDescriptor(), containsString("rbac.yaml"));
        assertThat(resp.getDescriptor(), containsString("plugins.yaml"));
        assertThat(resp.getDescriptor(), containsString("items.yaml"));
    }
}
