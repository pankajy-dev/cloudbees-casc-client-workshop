package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.AbstractCJPTest;
import com.cloudbees.jenkins.cjp.installmanager.WithConfigBundle;
import com.cloudbees.jenkins.cjp.installmanager.WithEnvelope;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.BundleUpdateLog;
import com.cloudbees.jenkins.cjp.installmanager.casc.validation.Validation;
import com.cloudbees.jenkins.cjp.installmanager.org.apache.commons.lang3.Functions;
import com.cloudbees.jenkins.plugins.casc.config.BundleUpdateTimingConfiguration;
import com.cloudbees.jenkins.plugins.updates.envelope.Envelope;
import com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopeProvider;
import org.htmlunit.TextPage;
import org.htmlunit.UnexpectedPage;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlPage;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes.beer12;
import static com.cloudbees.jenkins.plugins.updates.envelope.TestEnvelopes.e;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UpdateLogActionTest extends AbstractCJPTest {

    @Test
    @LocalData
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/casc/UpdateLogActionTest/bundles/instance_start_up")
    @WithEnvelope(TestEnvelope.class)
    public void instance_start_up() throws Exception {
        try (JenkinsRule.WebClient wc = rule.createWebClient()) {
            HtmlPage bundleUpdateTab = wc.goTo("casc-bundle-export-ui/bundleUpdate");
            String content = bundleUpdateTab.getWebResponse().getContentAsString();
            assertThat("Expected bundle version is 4", content, containsString("You are using bundle my-bundle:4"));
            assertThat("Current version with validations", content, containsString("Validation output for version my-bundle:4"));
            assertThat("Expected version 5 was rejected", content, containsString("A new version of the <b>Configuration Bundle (my-bundle:5)</b> is available, but it cannot be applied"));

            String newest = new BundleUpdateLog().getHistoricalRecords().get(0).toFile().getName(); // folder for newest depends on the test execution date, so reading the newest folder
            HtmlPage updateLogTab = wc.goTo("casc-bundle-export-ui/updateLog");
            HtmlAnchor link = (HtmlAnchor) updateLogTab.getElementById("view-" + newest);
            TextPage yamlPage = link.click();
            content = yamlPage.getWebResponse().getContentAsString();
            assertThat("Expected error in " + newest, content, containsString("message: '''apiVersion'' property in the bundle.yaml file must be an integer.'"));
            link = (HtmlAnchor) updateLogTab.getElementById("download-" + newest);
            UnexpectedPage zipContent = link.click();
            String validationContent = null;
            String descriptorContent = null;
            boolean isCandidate = false;
            try (ZipInputStream zipFile = new ZipInputStream(zipContent.getWebResponse().getContentAsStream())) {
                ZipEntry entry;
                while ((entry = zipFile.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    if ("validation.yaml".equals(entryName)) {
                        validationContent = IOUtils.toString(zipFile, StandardCharsets.UTF_8);
                    } else if (entryName.endsWith("bundle.yaml")) {
                        descriptorContent = IOUtils.toString(zipFile, StandardCharsets.UTF_8);
                    } else if (".candidate".equals(entryName)) {
                        isCandidate = true;
                    }
                }
            }
            assertThat("Expected error in " + newest, validationContent, containsString("message: '''apiVersion'' property in the bundle.yaml file must be an integer.'"));
            assertThat("Expected version 5 in " + newest, descriptorContent, containsString("version: \"5\""));
            assertTrue("Version 5 should be marked as candidate in " + newest, isCandidate);

            ConfigurationBundleManager cbm = ConfigurationBundleManager.get();
            BundleUpdateLog updateLog = cbm.getUpdateLog();
            assertThat("ConfigurationBundleManager has version 4 as current", cbm.getConfigurationBundle().getVersion(), is("4"));
            assertThat("ConfigurationBundleManager has version 4 as current validated", updateLog.getCurrentVersionValidations().getValidations(), not(empty()));
            assertThat("ConfigurationBundleManager has version 5 as rejected candidate", updateLog.getCandidateBundle().getVersion(), is("5"));
        }
    }

    @Test
    @WithEnvelope(TestEnvelope.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/casc/UpdateLogActionTest/bundles/update_bundles/version-1.zip")
    public void update_bundles() throws Exception {
        BundleUpdateTimingConfiguration configuration = BundleUpdateTimingConfiguration.get();
        configuration.setAutomaticReload(true);
        configuration.save();

        // Instance started up with version 1 - Valid
        ConfigurationBundleManager cbm = ConfigurationBundleManager.get();
        BundleUpdateLog updateLog = cbm.getUpdateLog();
        assertThat("ConfigurationBundleManager has version 1 as current", cbm.getConfigurationBundle().getVersion(), is("1"));
        assertThat("ConfigurationBundleManager has version 1 as current validated", updateLog.getCurrentVersionValidations().getValidations().stream().filter(val -> val.getLevel() != Validation.Level.INFO).collect(Collectors.toList()), empty());
        assertNull("ConfigurationBundleManager has no version marked as rejected candidate", updateLog.getCandidateBundle());

        // Updated to version 2 - Valid
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/UpdateLogActionTest/bundles/update_bundles/version-2.zip").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();
        cbm = ConfigurationBundleManager.get();
        updateLog = cbm.getUpdateLog();
        assertThat("ConfigurationBundleManager has version 2 as current", cbm.getConfigurationBundle().getVersion(), is("2"));
        assertThat("ConfigurationBundleManager has version 2 as current validated", updateLog.getCurrentVersionValidations().getValidations().stream().filter(val -> val.getLevel() != Validation.Level.INFO).collect(Collectors.toList()), empty());
        assertNull("ConfigurationBundleManager has no version marked as rejected candidate", updateLog.getCandidateBundle());

        // Updated to version 3 - Without version - Ignored
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/UpdateLogActionTest/bundles/update_bundles/version-3.zip").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();
        cbm = ConfigurationBundleManager.get();
        updateLog = cbm.getUpdateLog();
        assertThat("ConfigurationBundleManager has version 2 as current", cbm.getConfigurationBundle().getVersion(), is("2"));
        assertThat("ConfigurationBundleManager has version 2 as current validated", updateLog.getCurrentVersionValidations().getValidations().stream().filter(val -> val.getLevel() != Validation.Level.INFO).collect(Collectors.toList()), empty());
        assertNull("ConfigurationBundleManager has no version marked as rejected candidate", updateLog.getCandidateBundle());

        // Updated to version 4 - Invalid
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/UpdateLogActionTest/bundles/update_bundles/version-4.zip").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();
        cbm = ConfigurationBundleManager.get();
        updateLog = cbm.getUpdateLog();
        assertThat("ConfigurationBundleManager has version 2 as current", cbm.getConfigurationBundle().getVersion(), is("2"));
        assertThat("ConfigurationBundleManager has version 2 as current validated", updateLog.getCurrentVersionValidations().getValidations().stream().filter(val -> val.getLevel() != Validation.Level.INFO).collect(Collectors.toList()), empty());
        assertThat("ConfigurationBundleManager has version 4 as rejected candidate", updateLog.getCandidateBundle().getVersion(), is("4"));

        // Updated to version 5 - Valid but with warnings
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/UpdateLogActionTest/bundles/update_bundles/version-5.zip").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();
        cbm = ConfigurationBundleManager.get();
        updateLog = cbm.getUpdateLog();
        assertThat("ConfigurationBundleManager has version 5 as current", cbm.getConfigurationBundle().getVersion(), is("5"));
        assertThat("ConfigurationBundleManager has version 5 as current validated", updateLog.getCurrentVersionValidations().getValidations().stream().filter(val -> val.getLevel() != Validation.Level.INFO).collect(Collectors.toList()), not(empty()));
        assertNull("ConfigurationBundleManager has no version marked as rejected candidate", updateLog.getCandidateBundle());

        // Updated to version 6 - Invalid
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/UpdateLogActionTest/bundles/update_bundles/version-6.zip").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();
        cbm = ConfigurationBundleManager.get();
        updateLog = cbm.getUpdateLog();
        assertThat("ConfigurationBundleManager has version 5 as current", cbm.getConfigurationBundle().getVersion(), is("5"));
        assertThat("ConfigurationBundleManager has version 5 as current validated", updateLog.getCurrentVersionValidations().getValidations().stream().filter(val -> val.getLevel() != Validation.Level.INFO).collect(Collectors.toList()), not(empty()));
        assertThat("ConfigurationBundleManager has version 6 as rejected candidate", updateLog.getCandidateBundle().getVersion(), is("6"));

        // Updated to version 7 - Valid
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/UpdateLogActionTest/bundles/update_bundles/version-7.zip").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();
        cbm = ConfigurationBundleManager.get();
        updateLog = cbm.getUpdateLog();
        assertThat("ConfigurationBundleManager has version 7 as current", cbm.getConfigurationBundle().getVersion(), is("7"));
        assertThat("ConfigurationBundleManager has version 7 as current validated", updateLog.getCurrentVersionValidations().getValidations().stream().filter(val -> val.getLevel() != Validation.Level.INFO).collect(Collectors.toList()), empty());
        assertNull("ConfigurationBundleManager has no version marked as rejected candidate", updateLog.getCandidateBundle());

        // BEE-17161
        // Updated to version 8 - Valid structure / Invalid jenkins.yaml only warnings
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/UpdateLogActionTest/bundles/update_bundles/version-8.zip").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();
        cbm = ConfigurationBundleManager.get();
        updateLog = cbm.getUpdateLog();
        assertThat("ConfigurationBundleManager has version 8 as current", cbm.getConfigurationBundle().getVersion(), is("8"));
        assertThat("ConfigurationBundleManager has version 8 as current validated - only warnings", updateLog.getCurrentVersionValidations().getValidations().stream().filter(v -> v.getLevel() == Validation.Level.WARNING).collect(Collectors.toList()), not(empty()));
        assertThat("ConfigurationBundleManager has version 8 as current validated - only warnings", updateLog.getCurrentVersionValidations().getValidations().stream().filter(v -> v.getLevel() == Validation.Level.ERROR).collect(Collectors.toList()), empty());
        assertNull("ConfigurationBundleManager has no version marked as rejected candidate", updateLog.getCandidateBundle());

        // Updated to version 9 - Valid
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/UpdateLogActionTest/bundles/update_bundles/version-9.zip").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();
        cbm = ConfigurationBundleManager.get();
        updateLog = cbm.getUpdateLog();
        assertThat("ConfigurationBundleManager has version 9 as current", cbm.getConfigurationBundle().getVersion(), is("9"));
        assertThat("ConfigurationBundleManager has version 9 as current validated", updateLog.getCurrentVersionValidations().getValidations().stream().filter(val -> val.getLevel() != Validation.Level.INFO).collect(Collectors.toList()), empty());
        assertNull("ConfigurationBundleManager has no version marked as rejected candidate", updateLog.getCandidateBundle());

        // Updated to version 10 - Valid structure / Invalid jenkins.yaml
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/UpdateLogActionTest/bundles/update_bundles/version-10.zip").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();
        cbm = ConfigurationBundleManager.get();
        updateLog = cbm.getUpdateLog();
        assertThat("ConfigurationBundleManager has version 9 as current", cbm.getConfigurationBundle().getVersion(), is("9"));
        assertThat("ConfigurationBundleManager has version 9 as current validated", updateLog.getCurrentVersionValidations().getValidations().stream().filter(val -> val.getLevel() != Validation.Level.INFO).collect(Collectors.toList()), empty());
        assertThat("ConfigurationBundleManager has version 10 as rejected candidate", updateLog.getCandidateBundle().getVersion(), is("10"));
        assertThat("ConfigurationBundleManager has version 10 as rejected candidate", updateLog.getCandidateBundle().getValidations().getValidations().stream().filter(v -> v.getLevel() == Validation.Level.ERROR).collect(Collectors.toList()), not(empty()));
        assertThat("ConfigurationBundleManager has version 10 as rejected candidate", updateLog.getCandidateBundle().getValidations().getValidations().stream().filter(v -> v.getLevel() == Validation.Level.WARNING).collect(Collectors.toList()), empty());

        // Updated to version 11 - Valid
        System.setProperty("core.casc.config.bundle", Paths.get("src/test/resources/com/cloudbees/opscenter/client/casc/UpdateLogActionTest/bundles/update_bundles/version-11.zip").toFile().getAbsolutePath());
        ConfigurationUpdaterHelper.checkForUpdates();
        cbm = ConfigurationBundleManager.get();
        updateLog = cbm.getUpdateLog();
        assertThat("ConfigurationBundleManager has version 11 as current", cbm.getConfigurationBundle().getVersion(), is("11"));
        assertThat("ConfigurationBundleManager has version 11 as current validated", updateLog.getCurrentVersionValidations().getValidations().stream().filter(val -> val.getLevel() != Validation.Level.INFO).collect(Collectors.toList()), empty());
        assertNull("ConfigurationBundleManager has no version marked as rejected candidate", updateLog.getCandidateBundle());
        // End of BEE-17161
    }

    @Test
    @Issue("BEE-40710")
    @WithEnvelope(TestEnvelope.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/casc/UpdateLogActionTest/bundles/update_bundles/version-1.zip")
    public void validate_bundles() throws Exception {
        Path newest = ConfigurationBundleManager.get().getUpdateLog().getHistoricalRecords().get(0);
        String content = Files.readString(newest.resolve(BundleUpdateLog.VALIDATIONS_FILE));

        // Valid request
        ResponseData responseData = doTestValidation("/" + newest.getFileName().toString());
        // Note: HttpResponses.text(content) (which is the method used in doValidation) does not explicitly set the status to 200
        assertThat("Should give the validation file", responseData.content, is(content));

        responseData = doTestValidation("");
        assertThat("Should be an error if the path is invalid", responseData.statusCode, is(500));
        assertThat("Error message should explain that the 'Bundle version missing'", responseData.content, containsString("Bundle version missing"));

        responseData = doTestValidation("/./whatever");
        assertThat("Should be an error if the path is invalid", responseData.statusCode, is(404));

        responseData = doTestValidation("/../whatever");
        assertThat("Should be an error if the path is invalid", responseData.statusCode, is(403));

        // BEE-40710
        responseData = doTestValidation("/../core-casc-bundle-log-private");
        assertThat("Should be an error if the path is invalid", responseData.statusCode, is(403));
        // end of BEE-40710
    }

    @Test
    @Issue("BEE-40710")
    @WithEnvelope(TestEnvelope.class)
    @WithConfigBundle("src/test/resources/com/cloudbees/opscenter/client/casc/UpdateLogActionTest/bundles/update_bundles/version-1.zip")
    public void download_bundles() throws Exception {
        Path newest = ConfigurationBundleManager.get().getUpdateLog().getHistoricalRecords().get(0);

        // Valid request
        ResponseData responseData = doTestDownload("/" + newest.getFileName().toString());
        assertThat("Should return a zip", responseData.contentType, is("application/zip"));
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(responseData.stream.toByteArray()))) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                String name = zipEntry.getName();
                Path path = newest.resolve(name);
                if (!Files.isDirectory(path)) {
                    String zipContent = IOUtils.toString(zipInputStream, Charset.defaultCharset());
                    String fileContent = Files.readString(path);
                    assertThat("Check content of " + name, zipContent, is(fileContent));
                }
                zipInputStream.closeEntry();
            }
        }

        responseData = doTestDownload("");
        assertThat("Should be an error if the path is invalid", responseData.statusCode, is(500));
        assertThat("Error message should explain that the 'Bundle version missing'", responseData.content, containsString("Bundle version missing"));

        responseData = doTestDownload("/./whatever");
        assertThat("Should be an error if the path is invalid", responseData.statusCode, is(404));

        responseData = doTestDownload("/../whatever");
        assertThat("Should be an error if the path is invalid", responseData.statusCode, is(403));

        // BEE-40710
        responseData = doTestDownload("/../core-casc-bundle-log-private");
        assertThat("Should be an error if the path is invalid", responseData.statusCode, is(403));
        // end of BEE-40710
    }

    private static class ResponseData {
        public Integer statusCode;

        public String contentType;

        public String content;

        public ByteArrayOutputStream stream;
    }

    private static ResponseData doTestValidation(String restOfPath) throws IOException, ServletException {
        return doTestAction(restOfPath, UpdateLogAction::doValidation);
    }

    private static ResponseData doTestDownload(String restOfPath) throws IOException, ServletException {
        return doTestAction(restOfPath, UpdateLogAction::download);
    }

    @NotNull
    private static ResponseData doTestAction(String restOfPath, BiFunction<UpdateLogAction, StaplerRequest, HttpResponse> tested)
            throws IOException, ServletException {
        ResponseData responseData = new ResponseData();
        try (MockedStatic<ServiceLoader> loader = mockStatic(ServiceLoader.class)) {
            // Disable the ErrorCustomizer because it requires a Stapler instance to generate the response
            ServiceLoader<HttpResponses.ErrorCustomizer> serviceLoaderMock = mock(ServiceLoader.class);
            Iterator<HttpResponses.ErrorCustomizer> iteratorMock = mock(Iterator.class);
            when(serviceLoaderMock.iterator()).thenReturn(iteratorMock);
            when(iteratorMock.hasNext()).thenReturn(false);

            loader.when(() -> ServiceLoader.load(HttpResponses.ErrorCustomizer.class)).thenReturn(serviceLoaderMock);
            StaplerRequest request = mock(StaplerRequest.class);
            StaplerResponse response = mock(StaplerResponse.class);
            StringWriter body = new StringWriter();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();

            doReturn(new PrintWriter(body)).when(response).getWriter();
            doReturn(new ServletOutputStream() {
                @Override
                public void write(int b) {
                    stream.write(b);
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener writeListener) {
                }
            }).when(response).getOutputStream();
            doReturn(restOfPath).when(request).getRestOfPath();

            UpdateLogAction updateLogAction = new UpdateLogAction();
            HttpResponse httpResponse = tested.apply(updateLogAction, request);
            httpResponse.generateResponse(request, response, updateLogAction);

            ArgumentCaptor<Integer> status = ArgumentCaptor.forClass(Integer.class);
            verify(response, atLeast(0)).setStatus(status.capture());
            if (!status.getAllValues().isEmpty()) {
                responseData.statusCode = status.getValue();
            }

            ArgumentCaptor<String> contentType = ArgumentCaptor.forClass(String.class);
            verify(response, atLeast(0)).setContentType(contentType.capture());
            if (!contentType.getAllValues().isEmpty()) {
                responseData.contentType = contentType.getValue();
            }

            responseData.content = body.toString();
            responseData.stream = stream;
        }

        return responseData;
    }

    public static final class TestEnvelope implements TestEnvelopeProvider {
        @NonNull
        @Override
        public Envelope call() throws Exception {
            return e("2.332.2.6", 1, "2.332.2-cb-2", beer12());
        }
    }
}