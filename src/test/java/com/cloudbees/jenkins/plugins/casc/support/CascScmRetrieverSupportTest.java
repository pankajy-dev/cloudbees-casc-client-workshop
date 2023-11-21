package com.cloudbees.jenkins.plugins.casc.support;

import com.cloudbees.jenkins.support.SupportPlugin;
import hudson.ExtensionList;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class CascScmRetrieverSupportTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    /**
     * Validate that if the logfile lcoation does not exist the support bundle is still created and
     * indicates that the retriever is not deployed
     * This use case is possible for CBCI in K8s with casc-retriever disabled
     *
     * @throws Exception
     */
    @Test
    public void testRetrieverNotDeployedCreateSupportBundle() throws Exception {
        System.setProperty(CascScmRetrieverSupport.CASC_RETRIEVER_LOG_DIR_SYSPROP_NAME, j.jenkins.getRootDir().getAbsolutePath() + "/casc-retriever");
        ZipFile zip = getZipBundleAndAssertManifest("Support Bundle Manifest");
        ZipEntry cascRetrieverLog = zip.getEntry("casc_retriever.log");
        assertNull("casc_retriever.log should not have been included", cascRetrieverLog);
        ZipEntry cascRetriever = zip.getEntry(CascScmRetrieverSupport.CASC_RETRIEVER_MD_FILE);
        assertNotNull("casc retriever markdown file should be present", cascRetriever);
        assertThat("content should indicate casc-retriever is not deployed", IOUtils.toString(zip.getInputStream(cascRetriever), StandardCharsets.UTF_8), containsString(CascScmRetrieverSupport.CASC_RETRIEVER_NOT_DEPLOYED));
    }

    /**
     * Validates that casc retriever logs can correctly be included in a support bundle
     *
     * @throws Exception
     */
    @Test
    @LocalData
    public void testCascRetrieverLogsIncludedInSupportBundle() throws Exception {
        System.setProperty(CascScmRetrieverSupport.CASC_RETRIEVER_LOG_DIR_SYSPROP_NAME, j.jenkins.getRootDir().getAbsolutePath() + "/casc-retriever");

        ZipFile zip = getZipBundleAndAssertManifest("Support Bundle Manifest");

        ZipEntry cascRetrieverLog = zip.getEntry("casc_retriever.log");
        assertNotNull("casc_retriever.log should exist in support bundle", cascRetrieverLog);
        assertThat("casc_retriever.log should not be empty", cascRetrieverLog.getSize() > 0);

        ZipEntry cascRetrieverLog2 = zip.getEntry("casc_retriever.log.1");
        assertNotNull("casc_retriever.log.1 should exist in support bundle", cascRetrieverLog2);
        assertThat("casc_retriever.log.1 should not be empty", cascRetrieverLog2.getSize() > 0);

        ZipEntry shouldBeNull = zip.getEntry("foo.log");
        assertNotNull("foo.log should  have been included", shouldBeNull);

    }

    @NotNull
    private ZipFile getZipBundleAndAssertManifest(String content) throws Exception {
        File bundleFile = temp.newFile();

        try (OutputStream os = Files.newOutputStream(bundleFile.toPath())) {
            SupportPlugin.writeBundle(os, ExtensionList.lookup(CascScmRetrieverSupport.class));
        }

        ZipFile zip = new ZipFile(bundleFile);
        ZipEntry manifest = zip.getEntry("manifest.md");
        assertNotNull("CasC bundle content should have been created", manifest);
        assertThat("Wrong expected content in manifest", IOUtils.toString(zip.getInputStream(manifest), StandardCharsets.UTF_8), containsString(content));

        return zip;
    }
}

