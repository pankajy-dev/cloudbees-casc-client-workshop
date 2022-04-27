package com.cloudbees.jenkins.plugins.casc.support;

import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.jenkins.support.SupportPlugin;
import hudson.ExtensionList;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mockStatic;

public class CloudBeesCasCUpdateLogBundlesSupportTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void testNotAvailable() throws Exception {
        try (MockedStatic<BundleUpdateLog> bundleUpdateLogMockedStatic = mockStatic(BundleUpdateLog.class)) {
            bundleUpdateLogMockedStatic.when(BundleUpdateLog::retentionPolicy).thenReturn(0L);
            bundleUpdateLogMockedStatic.when(BundleUpdateLog::getHistoricalRecordsFolder).thenCallRealMethod();
            getZipBundleAndAssertSummary("Configuration as Code bundle update log disabled");
        }
    }

    @Test
    @LocalData
    public void testUpdateLog() throws Exception {
        ZipFile zip = getZipBundleAndAssertSummary("Files found in the update log");

        // All files are listed
        ZipEntry summary = zip.getEntry(BundleUpdateLog.CASC_BUNDLE_HISTORICAL_RECORDS_DIR + "/summary.md");
        assertThat("Wrong expected content in summary", IOUtils.toString(zip.getInputStream(summary), StandardCharsets.UTF_8), containsString("2-332-2-cb-1_update-log.csv"));
        assertThat("Wrong expected content in summary", IOUtils.toString(zip.getInputStream(summary), StandardCharsets.UTF_8), containsString("2-332-2-cb-2_update-log.csv"));

        // Check files contents
        ZipEntry csv1 = zip.getEntry(BundleUpdateLog.CASC_BUNDLE_HISTORICAL_RECORDS_DIR + "/2-332-2-cb-1_update-log.csv");
        assertNotNull("Expected csv 2-332-2-cb-1_update-log.csv", csv1);
        ZipEntry csv2 = zip.getEntry(BundleUpdateLog.CASC_BUNDLE_HISTORICAL_RECORDS_DIR + "/2-332-2-cb-2_update-log.csv");
        assertNotNull("Expected csv 2-332-2-cb-2_update-log.csv", csv2);
    }

    private ZipFile getZipBundleAndAssertSummary(String content) throws Exception {
        File bundleFile = temp.newFile();

        try (OutputStream os = Files.newOutputStream(bundleFile.toPath())) {
            SupportPlugin.writeBundle(os, ExtensionList.lookup(CloudBeesCasCUpdateLogBundlesSupport.class));
        }

        ZipFile zip = new ZipFile(bundleFile);
        ZipEntry summary = zip.getEntry(BundleUpdateLog.CASC_BUNDLE_HISTORICAL_RECORDS_DIR + "/summary.md");
        assertNotNull("CasC bundle content should have been created", summary);
        assertThat("Wrong expected content in summary", IOUtils.toString(zip.getInputStream(summary), StandardCharsets.UTF_8), containsString(content));

        return zip;
    }
}