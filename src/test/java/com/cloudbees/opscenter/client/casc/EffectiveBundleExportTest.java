package com.cloudbees.opscenter.client.casc;

import hudson.FilePath;
import org.apache.commons.io.FileUtils;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.jvnet.hudson.test.LoggerRule;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.zip.ZipOutputStream;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public class EffectiveBundleExportTest {

    @Rule
    public TestName testName = new TestName();
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();
    @Rule
    public LoggerRule loggerRule = new LoggerRule();

    private Path cascFolder;
    private EffectiveBundleExport export;
    private StaplerRequest request;
    private StaplerResponse response;

    @Before
    public void setUp() throws Exception {
        boolean isTestWithBundle = !testName.getMethodName().contains("withoutBundle");
        cascFolder = tmp.newFolder("core-casc-bundle").toPath();
        if (isTestWithBundle) {
            final URL url = EffectiveBundleExportTest.class.getClassLoader().getResource(EffectiveBundleExportTest.class.getName().replace(".", "/") + "/core-casc-bundle");
            FileUtils.copyDirectory(new File(url.toURI()), cascFolder.toFile());
        }

        export = spy(new EffectiveBundleExport());
        doReturn(isTestWithBundle).when(export).isCascConfigured();
        doNothing().when(export).checkPermissions();
        doReturn(cascFolder).when(export).getBundleFolder();

        request = mock(StaplerRequest.class);
        response = mock(StaplerResponse.class);
        doReturn(mock(ServletOutputStream.class)).when(response).getOutputStream();
        doReturn(mock(PrintWriter.class)).when(response).getWriter();
    }

    @Test
    public void withoutBundle() {
        loggerRule.record(EffectiveBundleExport.class, Level.WARNING).capture(10);

        doReturn("/any/file/path").when(request).getRestOfPath();
        export.doDownloadFile(request);
        assertThat(loggerRule, LoggerRule.recorded(Level.WARNING, Matchers.containsString("Attempted to download a bundle (any/file/path) when the controller is not using a CasC Bundle yet.")));

        export.doIndex();
        assertThat(loggerRule, LoggerRule.recorded(Level.WARNING, Matchers.containsString("Attempt to download the bundle when the controller is not using CasC Bundle yet")));
    }

    @Test
    public void downloadFile() throws Exception {
        loggerRule.record(EffectiveBundleExport.class, Level.ALL).capture(1);

        doReturn("").when(request).getRestOfPath();
        export.doDownloadFile(request);
        assertThat(loggerRule, LoggerRule.recorded(Level.WARNING, Matchers.containsString("'file' parameter in request is null.")));

        doReturn(null).when(request).getRestOfPath();
        export.doDownloadFile(request);
        assertThat(loggerRule, LoggerRule.recorded(Level.WARNING, Matchers.containsString("'file' parameter in request is null.")));

        doReturn("/./whatever").when(request).getRestOfPath();
        export.doDownloadFile(request);
        assertThat(loggerRule, LoggerRule.recorded(Level.WARNING, Matchers.containsString("Attempted to download a non-existent file.")));

        doReturn("/../whatever").when(request).getRestOfPath();
        export.doDownloadFile(request);
        assertThat(loggerRule, LoggerRule.recorded(Level.WARNING, Matchers.containsString("Attempted to access files outside the bundle directory.")));

        doReturn("/fake.yml").when(request).getRestOfPath();
        export.doDownloadFile(request);
        assertThat(loggerRule, LoggerRule.recorded(Level.WARNING, Matchers.containsString("Attempted to download a non-existent file.")));

        doReturn("/bundle.yaml").when(request).getRestOfPath();
        export.doDownloadFile(request).generateResponse(request, response, null);
        assertThat(loggerRule, LoggerRule.recorded(Level.FINE, Matchers.containsString("Downloading bundle.yaml")));
        String content = export.readContentFile(cascFolder.resolve("bundle.yaml"));
        assertThat("Invalid bundle.yaml content", content, containsString("id: \"my-master\""));
        assertThat("Invalid bundle.yaml content", content, containsString("description: \"Minimun version\""));

        doReturn("/jcasc/jenkins.yaml").when(request).getRestOfPath();
        export.doDownloadFile(request).generateResponse(request, response, null);
        assertThat(loggerRule, LoggerRule.recorded(Level.FINE, Matchers.containsString("Downloading jcasc/jenkins.yaml")));
        content = export.readContentFile(cascFolder.resolve("jcasc/jenkins.yaml"));
        assertThat("Invalid jenkins.yaml content", content, containsString("systemMessage: \"Hey! I've been configured as Code\""));
    }

    @Test
    public void downloadZipExport() throws Exception {
        loggerRule.record(EffectiveBundleExport.class, Level.FINE).capture(10);

        export.doIndex().generateResponse(request, response, null);
        assertThat(loggerRule, LoggerRule.recorded(Level.FINE, Matchers.containsString("Downloading installed bundle in zip format")));
    }

    @Test
    public void testZip() throws Exception {
        EffectiveBundleExport.ZipBundleResponse zbr = new EffectiveBundleExport.ZipBundleResponse(cascFolder.toFile());

        // Generate zip
        File zip = tmp.newFile("test-zip.zip");
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip, false));
        zbr.addZipEntries(zos);
        zos.close();

        // Check file
        File unzipped = tmp.newFolder("unzipped-bundle");
        FilePath dst = new FilePath(unzipped);
        (new FilePath(zip)).unzip(dst);
        assertTrue("Descriptor does not exist", new File(unzipped, "bundle.yaml").exists());
        assertTrue("Descriptor does not exist", new File(unzipped, "jcasc/jenkins.yaml").exists());
    }
}