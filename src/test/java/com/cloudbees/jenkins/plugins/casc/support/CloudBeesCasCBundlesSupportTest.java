package com.cloudbees.jenkins.plugins.casc.support;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.support.SupportPlugin;
import hudson.ExtensionList;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CloudBeesCasCBundlesSupportTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void testNotAvailable() throws Exception {
        getZipBundleAndAssertSummary("Configuration as Code bundle not found");
    }

    @Test
    @LocalData
    public void testNotADirectory() throws Exception {
        getZipBundleAndAssertSummary("Found a file, not a bundle directory");
    }

    @Test
    public void testEmpty() throws Exception {
        // empty directories are not copied by LocalData
        Files.createDirectory(new File(j.jenkins.getRootDir(), ConfigurationBundleManager.CASC_BUNDLE_DIR).toPath());

        getZipBundleAndAssertSummary("Configuration as Code bundle is empty");
    }

    @Test
    @LocalData
    public void testGeneratedBundle() throws Exception {
        ZipFile zip = getZipBundleAndAssertSummary("Files found in the bundle");

        // All files are listed
        ZipEntry summary = zip.getEntry(ConfigurationBundleManager.CASC_BUNDLE_DIR + "/summary.md");
        assertThat("Wrong expected content in summary", IOUtils.toString(zip.getInputStream(summary), StandardCharsets.UTF_8), containsString("bundle.yaml"));
        assertThat("Wrong expected content in summary", IOUtils.toString(zip.getInputStream(summary), StandardCharsets.UTF_8), containsString("items/items.yaml"));
        assertThat("Wrong expected content in summary", IOUtils.toString(zip.getInputStream(summary), StandardCharsets.UTF_8), containsString("jcasc/jenkins.yaml"));
        assertThat("Wrong expected content in summary", IOUtils.toString(zip.getInputStream(summary), StandardCharsets.UTF_8), containsString("catalog/plugin-catalog.yaml"));
        assertThat("Wrong expected content in summary", IOUtils.toString(zip.getInputStream(summary), StandardCharsets.UTF_8), containsString("plugins/plugins.yaml"));
        assertThat("Wrong expected content in summary", IOUtils.toString(zip.getInputStream(summary), StandardCharsets.UTF_8), containsString("rbac/rbac.yaml"));

        // Check files contents
        assertFileContent(zip, "bundle.yaml");
        assertFileContent(zip, "items/items.yaml");
        assertFileContent(zip, "jcasc/jenkins.yaml");
        assertFileContent(zip, "catalog/plugin-catalog.yaml");
        assertFileContent(zip, "plugins/plugins.yaml");
        assertFileContent(zip, "rbac/rbac.yaml");
    }

    private void assertFileContent(ZipFile zip, String file) throws Exception {
        String fromBundle = IOUtils.toString(zip.getInputStream(zip.getEntry(ConfigurationBundleManager.CASC_BUNDLE_DIR + "/" + file)), StandardCharsets.UTF_8).replaceAll("\r\n", "\n");
        String fromResources = IOUtils.toString(CloudBeesCasCBundlesSupportTest.class.getResourceAsStream(CloudBeesCasCBundlesSupportTest.class.getSimpleName() + "/testGeneratedBundle/core-casc-bundle/" + file), StandardCharsets.UTF_8).replaceAll("\r\n", "\n");
        assertThat("Wrong content in file " + file, fromBundle, is(fromResources));
    }

    private ZipFile getZipBundleAndAssertSummary(String content) throws Exception {
        File bundleFile = temp.newFile();

        try (OutputStream os = Files.newOutputStream(bundleFile.toPath())) {
            SupportPlugin.writeBundle(os, ExtensionList.lookup(CloudBeesCasCBundlesSupport.class));
        }

        ZipFile zip = new ZipFile(bundleFile);
        ZipEntry summary = zip.getEntry(ConfigurationBundleManager.CASC_BUNDLE_DIR + "/summary.md");
        assertNotNull("CasC bundle content should have been created", summary);
        assertThat("Wrong expected content in summary", IOUtils.toString(zip.getInputStream(summary), StandardCharsets.UTF_8), containsString(content));

        return zip;
    }
}